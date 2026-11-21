use leptos::prelude::*;
use mesh_core::Action;
use mesh_core::*;
use serde::de::DeserializeOwned;
use serde_json::{json, Value};
use wasm_bindgen::prelude::*;
use wasm_bindgen_futures::spawn_local;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(catch,js_namespace=["window","__TAURI__","core"],js_name=invoke)]
    async fn invoke(command: &str, args: JsValue) -> Result<JsValue, JsValue>;
}
pub async fn call<T: DeserializeOwned>(request: Action) -> Result<T, String> {
    let args = serde::Serialize::serialize(
        &json!({"request":request}),
        &serde_wasm_bindgen::Serializer::json_compatible(),
    )
    .map_err(|_| "Некорректный запрос".to_string())?;
    let result = invoke("desk", args).await.map_err(|e| {
        e.as_string()
            .unwrap_or_else(|| "Не удалось выполнить действие".into())
    })?;
    serde_wasm_bindgen::from_value(result).map_err(|_| "Не удалось прочитать ответ МЭШ".into())
}
pub fn today() -> chrono::NaiveDate {
    let js = js_sys::Date::new_0();
    chrono::NaiveDate::from_ymd_opt(js.get_full_year() as i32, js.get_month() + 1, js.get_date())
        .unwrap_or_default()
}
#[derive(Clone, Copy)]
pub struct Ui {
    pub ready: RwSignal<bool>,
    pub account: RwSignal<Option<Account>>,
    pub prefs: RwSignal<Preferences>,
    pub q: RwSignal<Query>,
    pub board: RwSignal<Board>,
    pub selected: RwSignal<Option<Row>>,
    pub stack: RwSignal<Vec<Row>>,
    pub busy: RwSignal<bool>,
    pub working: RwSignal<bool>,
    pub error: RwSignal<String>,
    pub info: RwSignal<String>,
    pub ticket: RwSignal<u64>,
    pub portal: RwSignal<String>,
    pub remember: RwSignal<bool>,
    pub update: RwSignal<Option<(String, String)>>,
}
impl Ui {
    pub fn new() -> Self {
        Self {
            ready: RwSignal::new(false),
            account: RwSignal::new(None),
            prefs: RwSignal::new(Preferences::default()),
            q: RwSignal::new(Query::new(Section::Schedule, today())),
            board: RwSignal::new(Board::default()),
            selected: RwSignal::new(None),
            stack: RwSignal::new(vec![]),
            busy: RwSignal::new(false),
            working: RwSignal::new(false),
            error: RwSignal::new(String::new()),
            info: RwSignal::new(String::new()),
            ticket: RwSignal::new(0),
            portal: RwSignal::new(String::new()),
            remember: RwSignal::new(true),
            update: RwSignal::new(None),
        }
    }
    pub fn t(self, key: &str) -> String {
        crate::i18n::text(&self.prefs.get().language, key).into()
    }
    pub fn accept(self, s: Start) {
        self.prefs.set(s.prefs);
        self.account.set(s.account);
        self.info.set(s.message);
    }
    pub fn session(self, action: Action) {
        if self.working.get_untracked() {
            return;
        }
        self.working.set(true);
        self.error.set(String::new());
        spawn_local(async move {
            let result = call::<Start>(action).await;
            self.working.set(false);
            self.ready.set(true);
            match result {
                Ok(s) => {
                    self.portal.set(String::new());
                    self.accept(s);
                    self.board.set(Board::default());
                    self.selected.set(None);
                    self.ticket.update(|t| *t += 1);
                    if self.account.get_untracked().is_some() {
                        self.navigate(Section::Schedule);
                    }
                }
                Err(e) => self.error.set(e),
            }
        })
    }
    pub fn navigate(self, section: Section) {
        self.load(Query::new(section, today()), None, false)
    }
    pub fn reload(self) {
        self.load(self.q.get_untracked(), None, true)
    }
    pub fn load(self, q: Query, lesson: Option<Row>, keep: bool) {
        let current = self.selected.get_untracked().filter(|_| keep).map(|r| r.id);
        self.ticket.update(|t| *t += 1);
        let generation = self.ticket.get_untracked();
        self.q.set(q.clone());
        self.stack.set(Vec::new());
        self.error.set(String::new());
        if !keep {
            self.board.set(Board::default());
            self.selected.set(None);
        }
        if q.section == Section::Settings {
            self.busy.set(false);
            return;
        }
        self.busy.set(true);
        spawn_local(async move {
            let result = call::<Board>(Action::Page { query: q.clone() }).await;
            if self.ticket.get_untracked() != generation {
                return;
            }
            self.busy.set(false);
            match result {
                Ok(b) => {
                    let row = lesson
                        .as_ref()
                        .and_then(|l| diary::match_lesson(l, &b.rows))
                        .and_then(|i| b.rows.get(i))
                        .or_else(|| {
                            current
                                .as_ref()
                                .and_then(|id| b.rows.iter().find(|r| &r.id == id))
                        })
                        .cloned();
                    if lesson.is_some() && row.is_none() {
                        self.info.set("Выберите задание из списка".into())
                    }
                    self.selected.set(row);
                    self.board.set(b);
                }
                Err(e) => self.error.set(e),
            }
        });
    }
    pub fn select(self, row: Row, nested: bool) {
        if nested {
            if let Some(prev) = self.selected.get_untracked() {
                self.stack.update(|s| s.push(prev));
            }
        } else {
            self.stack.set(vec![]);
        }
        let mark_id = row.id.clone();
        let is_mark = row.kind == "mark" && !mark_id.is_empty();
        self.selected.set(Some(row));
        self.error.set(String::new());
        if is_mark {
            let ticket = self.ticket.get_untracked();
            spawn_local(async move {
                let result = call::<Row>(Action::Mark {
                    id: mark_id.clone(),
                })
                .await;
                if self.ticket.get_untracked() != ticket
                    || self.selected.get_untracked().as_ref().map(|r| &r.id) != Some(&mark_id)
                {
                    return;
                }
                match result {
                    Ok(detail) => self.selected.update(|r| {
                        if let Some(r) = r {
                            for f in detail.fields {
                                if !r.fields.iter().any(|old| old.label == f.label) {
                                    r.fields.push(f)
                                }
                            }
                            if !detail.description.is_empty() {
                                r.description = detail.description
                            }
                        }
                    }),
                    Err(e) => self.error.set(e),
                }
            })
        }
    }
    pub fn close_detail(self) {
        let mut stack = self.stack.get_untracked();
        let previous = stack.pop();
        self.stack.set(stack);
        if previous.is_none() {
            if let Some(r) = self.selected.get_untracked() {
                if let Some(el) = document().get_element_by_id(&format!("row-{}", r.id)) {
                    use wasm_bindgen::JsCast;
                    if let Some(el) = el.dyn_ref::<web_sys::HtmlElement>() {
                        let _ = el.focus();
                    }
                }
            }
        }
        self.selected.set(previous);
    }
    pub fn action<T: DeserializeOwned + 'static>(
        self,
        request: Action,
        success: impl FnOnce(T) + 'static,
    ) {
        if self.working.get_untracked() {
            return;
        }
        self.working.set(true);
        self.error.set(String::new());
        let ticket = self.ticket.get_untracked();
        spawn_local(async move {
            let result = call(request).await;
            self.working.set(false);
            if self.ticket.get_untracked() == ticket {
                match result {
                    Ok(v) => success(v),
                    Err(e) => self.error.set(e),
                }
            }
        });
    }
    pub fn save(self, prefs: Preferences) {
        self.action(Action::SavePrefs { prefs }, move |s: Start| {
            self.prefs.set(s.prefs)
        })
    }
    pub fn start_portal(self, kind: &str) {
        let kind = kind.to_string();
        self.action(Action::Portal { kind: kind.clone() }, move |_: Value| {
            self.portal.set(kind)
        })
    }
    pub fn portal_finish(self) {
        let kind = self.portal.get_untracked();
        let card = kind == "card";
        self.action(
            Action::FinishPortal {
                kind,
                remember: self.remember.get_untracked(),
            },
            move |s: Start| {
                self.portal.set(String::new());
                self.accept(s);
                if card {
                    self.reload()
                } else {
                    self.navigate(Section::Schedule)
                }
            },
        )
    }
    pub fn card_action(self, action: &str) {
        self.action(
            Action::Card {
                action: action.into(),
            },
            move |card: Card| self.board.update(|b| b.card = Some(card)),
        )
    }
    pub fn toggle(self, r: Row) {
        let Some(previous) = r.done else { return };
        if self.working.get_untracked() {
            return;
        }
        let set = move |ui: Ui, id: &str, value: bool| {
            ui.board.update(|b| {
                for row in &mut b.rows {
                    if row.id == id {
                        row.done = Some(value)
                    }
                }
            });
            ui.selected.update(|current| {
                if let Some(row) = current {
                    if row.id == id {
                        row.done = Some(value)
                    }
                }
            });
        };
        set(self, &r.id, !previous);
        self.working.set(true);
        self.error.set(String::new());
        let ticket = self.ticket.get_untracked();
        spawn_local(async move {
            let result = call::<Value>(Action::Done {
                id: r.id.clone(),
                kind: r.kind,
                done: !previous,
            })
            .await;
            self.working.set(false);
            if let Err(e) = result {
                if self.ticket.get_untracked() == ticket {
                    set(self, &r.id, previous);
                    self.error.set(e);
                }
            }
        })
    }
    pub fn updates(self, manual: bool) {
        let run = move |value: Value| {
            if let (Some(v), Some(url)) = (value["version"].as_str(), value["url"].as_str()) {
                self.update.set(Some((v.into(), url.into())));
            } else if manual {
                self.info.set("Установлена актуальная версия".into());
            }
        };
        if manual {
            self.action(Action::Updates { manual }, run)
        } else {
            spawn_local(async move {
                if let Ok(v) = call(Action::Updates { manual: false }).await {
                    run(v)
                }
            })
        }
    }
    pub fn open(self, url: String) {
        self.action(Action::Open { url }, |_: Value| {})
    }
}
