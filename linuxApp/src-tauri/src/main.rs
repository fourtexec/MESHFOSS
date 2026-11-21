#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]
mod api;
#[cfg(target_os = "linux")]
mod background;
mod card;
mod portal;
mod storage;
mod updates;

use api::{Mesh, Result};
use chrono::{NaiveDate, Utc};
use mesh_core::{demo, Account, Action, Board, Card, Section, Start};
use serde_json::{json, Value};
use std::{collections::BTreeMap, time::Duration};
use storage::{Credentials, Local, Storage};
use tauri::{AppHandle, Manager, WebviewWindow};
use tokio::sync::Mutex;

struct Desk {
    local: Local,
    storage: Storage,
    auth: Credentials,
    remember: bool,
    client: Option<Mesh>,
    http: reqwest::Client,
    documents: card::Documents,
    portal: String,
    // Only successful authenticated reads are cached, scoped to this student's session.
    cache: BTreeMap<String, (NaiveDate, Board)>,
}
impl Desk {
    fn account(&self) -> Option<Account> {
        if self.local.demo {
            return Some(demo::account());
        }
        self.client.as_ref().map(|c| {
            let mut a = c.account.clone();
            a.remembered = self.remember;
            a.token_info = api::token_info(&c.token);
            a
        })
    }
    fn snapshot(&self, message: String) -> Value {
        json!(Start {
            account: self.account(),
            prefs: self.local.prefs.clone(),
            message
        })
    }
    fn persist(&mut self) -> Result<()> {
        if let Some(client) = &self.client {
            self.auth.token = client.token.clone();
            self.auth.active = client.account.active.clone();
        }
        if self.remember {
            storage::write_secret(&self.auth)?
        }
        Ok(())
    }
    async fn sign_in(&mut self, token: String, active: &str, remember: bool) -> Result<Value> {
        let mut candidate = Mesh::new(
            self.http.clone(),
            api::token_value(&token).ok_or("Некорректный токен")?,
        );
        candidate.identify(active).await?;
        if !remember && self.local.saved_session != Some(false) {
            storage::clear_secret()?;
            self.local.saved_session = Some(false);
        }
        self.client = Some(candidate);
        self.local.demo = false;
        self.cache.clear();
        self.remember = remember;
        let message = if let Err(e) = self.persist() {
            self.remember = false;
            e
        } else {
            String::new()
        };
        self.local.saved_session = Some(self.remember);
        self.storage.save(&self.local)?;
        Ok(self.snapshot(message))
    }
    fn live(&mut self) -> Result<&mut Mesh> {
        self.client.as_mut().ok_or_else(|| "Требуется вход".into())
    }
    async fn card(&mut self, action: &str) -> Result<Card> {
        if self.local.demo {
            return Ok(Card {
                state: "demo".into(),
                image: card::qr("MESHFOSS DEMO - NOT AN ACCESS PASS")?,
                ..Default::default()
            });
        }
        let student = self
            .live()?
            .account
            .student()
            .ok_or("Требуется вход")?
            .person
            .clone();
        match self
            .documents
            .load(&self.http, &self.auth, action, &student)
            .await
        {
            Err(e) if e == "Требуется вход для карты" => {
                self.auth.card.clear();
                self.persist()?;
                Ok(Card {
                    state: "login".into(),
                    ..Default::default()
                })
            }
            r => r,
        }
    }
    async fn handle(&mut self, app: &AppHandle, action: Action) -> Result<Value> {
        match action {
            Action::Start => {
                if self.account().is_some() || self.local.saved_session == Some(false) {
                    return Ok(self.snapshot(String::new()));
                }
                match storage::read_secret() {
                    Ok(Some(s)) => {
                        self.auth = s;
                        match self
                            .sign_in(self.auth.token.clone(), &self.auth.active.clone(), true)
                            .await
                        {
                            Ok(v) => Ok(v),
                            Err(e) => Ok(self.snapshot(e)),
                        }
                    }
                    Ok(None) => {
                        self.local.saved_session = Some(false);
                        self.storage.save(&self.local)?;
                        Ok(self.snapshot(String::new()))
                    }
                    Err(e) => Ok(self.snapshot(e)),
                }
            }
            Action::Demo => {
                self.client = None;
                self.local.demo = true;
                self.cache.clear();
                self.storage.save(&self.local)?;
                Ok(self.snapshot(String::new()))
            }
            Action::Login { token, remember } => {
                self.auth = Credentials::default();
                self.sign_in(token, "", remember).await
            }
            Action::Portal { kind } => {
                if kind == "card" {
                    self.live()?;
                }
                if self.auth.device.is_empty() {
                    self.auth.device = uuid::Uuid::new_v4().to_string()
                }
                portal::open(app, &kind, &self.auth.device)?;
                self.portal = kind;
                Ok(Value::Null)
            }
            Action::CancelPortal => {
                self.portal.clear();
                portal::close(app)?;
                Ok(Value::Null)
            }
            Action::FinishPortal { kind, remember } => {
                if kind != self.portal || kind.is_empty() {
                    return Err("Окно входа закрыто".into());
                }
                let result = portal::result(app, &kind)?;
                let outcome = if kind == "card" {
                    self.auth.card = result;
                    self.persist()?;
                    self.snapshot(String::new())
                } else {
                    self.auth = Credentials::default();
                    self.sign_in(result, "", remember).await?
                };
                self.portal.clear();
                portal::close(app)?;
                Ok(outcome)
            }
            Action::Logout => {
                #[cfg(target_os = "linux")]
                if self.local.prefs.renew_when_closed {
                    background::configure(false)?;
                    self.local.prefs.renew_when_closed = false;
                }
                if self.remember && !self.local.demo {
                    storage::clear_secret()?;
                    self.local.saved_session = Some(false);
                }
                self.client = None;
                self.auth = Credentials::default();
                self.cache.clear();
                self.documents.log.clear();
                self.portal.clear();
                self.remember = false;
                self.local.demo = false;
                self.storage.save(&self.local)?;
                portal::close(app)?;
                Ok(self.snapshot(String::new()))
            }
            Action::Refresh => {
                if !self.local.demo {
                    self.live()?.refresh().await?;
                    self.persist()?;
                }
                Ok(self.snapshot(String::new()))
            }
            Action::SelectStudent { id } => {
                if !self.local.demo {
                    let client = self.live()?;
                    if !client.account.students.iter().any(|s| s.id == id) {
                        return Err("В профиле нет ученика".into());
                    }
                    client.account.active = id;
                    self.cache.clear();
                    self.persist()?;
                }
                Ok(self.snapshot(String::new()))
            }
            Action::Page { query } => {
                if query.section == Section::Card {
                    return Ok(json!(Board {
                        card: Some(self.card("load").await?),
                        ..Default::default()
                    }));
                }
                if self.local.demo {
                    return Ok(json!(demo::board(&query, &self.local.completed)));
                }
                let cacheable = matches!(
                    query.section,
                    Section::Grades | Section::Ranking | Section::Subjects
                );
                let key = serde_json::to_string(&query).map_err(|_| "Некорректный запрос")?;
                let today = Utc::now().date_naive();
                let result = self.live()?.page(&query).await;
                if self
                    .client
                    .as_ref()
                    .is_some_and(|c| c.token != self.auth.token)
                {
                    self.persist()?;
                }
                match result {
                    Ok(b) => {
                        if cacheable {
                            if self.cache.len() >= 64 {
                                self.cache.pop_first();
                            }
                            self.cache.insert(key, (today, b.clone()));
                        }
                        Ok(json!(b))
                    }
                    Err(e)
                        if cacheable
                            && (e == "Проверьте подключение к интернету"
                                || e == "Сервер не успел ответить") =>
                    {
                        if let Some((_, b)) = self.cache.get(&key) {
                            let mut b = b.clone();
                            b.notice = "Сохранённые данные".into();
                            Ok(json!(b))
                        } else {
                            Err(e)
                        }
                    }
                    Err(e) => Err(e),
                }
            }
            Action::Done { id, kind, done } => {
                if self.local.demo {
                    let previous = self.local.completed.clone();
                    if done {
                        self.local.completed.insert(id);
                    } else {
                        self.local.completed.remove(&id);
                    }
                    if let Err(e) = self.storage.save(&self.local) {
                        self.local.completed = previous;
                        return Err(e);
                    }
                } else {
                    self.live()?.done(&id, &kind, done).await?;
                }
                Ok(Value::Null)
            }
            Action::Mark { id } => Ok(json!(if self.local.demo {
                demo::mark_detail()
            } else {
                self.live()?.mark(&id).await?
            })),
            Action::Material { entry, resource } => {
                let url = if resource.url.is_empty() {
                    self.live()?.material(&entry, &resource.material).await?
                } else {
                    resource.url
                };
                open_link(&url)?;
                Ok(Value::Null)
            }
            Action::Card { action } => {
                if !matches!(action.as_str(), "enable" | "refresh" | "load") {
                    return Err("Некорректный запрос".into());
                }
                Ok(json!(self.card(&action).await?))
            }
            Action::SavePrefs { prefs } => {
                if !prefs.valid() {
                    return Err("Некорректные настройки".into());
                }
                #[cfg(target_os = "linux")]
                if prefs.renew_when_closed != self.local.prefs.renew_when_closed {
                    if prefs.renew_when_closed && (!self.remember || self.local.demo) {
                        return Err("Сначала сохраните сессию в связке ключей".into());
                    }
                    if let Err(e) = background::configure(prefs.renew_when_closed) {
                        let _ = background::configure(self.local.prefs.renew_when_closed);
                        return Err(e);
                    }
                }
                let old = std::mem::replace(&mut self.local.prefs, prefs);
                if let Err(e) = self.storage.save(&self.local) {
                    #[cfg(target_os = "linux")]
                    if self.local.prefs.renew_when_closed != old.renew_when_closed {
                        let _ = background::configure(old.renew_when_closed);
                    }
                    self.local.prefs = old;
                    return Err(e);
                }
                Ok(self.snapshot(String::new()))
            }
            Action::Updates { manual } => {
                updates::check(
                    &self.http,
                    &app.package_info().version,
                    self.local.prefs.channel == "preview",
                    manual,
                )
                .await
            }
            Action::Open { url } => {
                open_link(&url)?;
                Ok(Value::Null)
            }
            Action::Log { clear } => {
                if clear {
                    self.documents.log.clear()
                }
                Ok(json!(self
                    .documents
                    .log
                    .iter()
                    .cloned()
                    .collect::<Vec<_>>()
                    .join("\n")))
            }
        }
    }
}
fn open_link(raw: &str) -> Result<()> {
    let u = url::Url::parse(raw).map_err(|_| "Ссылка недоступна")?;
    if !matches!(u.scheme(), "https" | "http")
        || !u.has_host()
        || !u.username().is_empty()
        || u.password().is_some()
    {
        return Err("Ссылка недоступна".into());
    }
    open::that_detached(u.as_str()).map_err(|_| "Не удалось открыть ссылку".into())
}
#[tauri::command]
async fn desk(
    window: WebviewWindow,
    request: Action,
    state: tauri::State<'_, Mutex<Desk>>,
) -> Result<Value> {
    if window.label() != "main" {
        return Err("Нет доступа".into());
    }
    let mut desk = state.lock().await;
    let result = desk.handle(window.app_handle(), request).await;
    if desk
        .client
        .as_ref()
        .is_some_and(|c| c.token != desk.auth.token)
    {
        desk.persist()?;
    }
    result
}
fn main() {
    #[cfg(target_os = "linux")]
    if std::env::args().nth(1).as_deref() == Some("--refresh-session") {
        if let Err(e) = tauri::async_runtime::block_on(background::renew()) {
            eprintln!("{e}");
            std::process::exit(1)
        }
        return;
    }
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.set_focus();
            }
        }))
        .setup(|app| {
            let dir = app.path().app_config_dir()?;
            #[cfg(target_os = "linux")]
            {
                let guard = background::lock(&dir, true)
                    .map_err(std::io::Error::other)?
                    .ok_or_else(|| std::io::Error::other("MESHFOSS уже запущен"))?;
                app.manage(guard);
            }
            let storage = Storage::new(dir);
            let local = storage.load().map_err(std::io::Error::other)?;
            app.manage(Mutex::new(Desk {
                local,
                storage,
                auth: Credentials::default(),
                remember: false,
                client: None,
                http: api::http().map_err(std::io::Error::other)?,
                documents: card::Documents::new(),
                portal: String::new(),
                cache: BTreeMap::new(),
            }));
            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                loop {
                    tokio::time::sleep(Duration::from_secs(12 * 3600)).await;
                    let state = handle.state::<Mutex<Desk>>();
                    let mut desk = state.lock().await;
                    if let Some(client) = desk.client.as_mut() {
                        if client.refresh().await.is_ok() {
                            let _ = desk.persist();
                        }
                    }
                }
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![desk])
        .run(tauri::generate_context!())
        .expect("MESHFOSS desktop");
}
