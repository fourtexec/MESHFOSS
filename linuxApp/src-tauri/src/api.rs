use chrono::Days;
use mesh_core::{diary, grades, services, *};
use reqwest::{Client, Method, Response, StatusCode};
use serde_json::{json, Value};
use std::{collections::BTreeMap, time::Duration};
use url::Url;

pub const SCHOOL: &str = "https://school.mos.ru";
const FAMILY: &str = "/api/family/web/v1";
const RANK: &str = "/api/ej/rating/v1/rank";
const FOOD: &str = "/api/food/meals/v3";
const YEARS: &str = "/api/ej/core/family/v1/academic_years";
pub const REFRESH: &str = "/v2/token/refresh?roleId=1&subsystem=2";
pub type Result<T> = std::result::Result<T, String>;
pub fn http() -> Result<Client> {
    Client::builder()
        .user_agent(concat!("MESHFOSS-Desktop/", env!("CARGO_PKG_VERSION")))
        .timeout(Duration::from_secs(22))
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(|_| "Не удалось создать подключение".into())
}
pub fn network(e: reqwest::Error) -> String {
    if e.is_timeout() {
        "Сервер не успел ответить"
    } else {
        "Проверьте подключение к интернету"
    }
    .into()
}
pub fn status_error(status: StatusCode) -> String {
    match status.as_u16() {
        401 => "Требуется повторный вход".into(),
        403 => "Нет доступа к этому разделу".into(),
        404 => "Раздел недоступен".into(),
        _ => format!("HTTP {}", status.as_u16()),
    }
}
pub fn token_value(raw: &str) -> Option<String> {
    fn valid(s: &str) -> bool {
        (24..=16384).contains(&s.len())
            && s.bytes()
                .all(|c| c.is_ascii_alphanumeric() || b"._~+/=-".contains(&c))
    }
    let input = raw.trim();
    if valid(input) {
        return Some(input.into());
    }
    if let Ok(value) = serde_json::from_str::<Value>(input) {
        let candidate = value.as_str().map(str::to_owned).unwrap_or_else(|| {
            field(
                &value,
                &[
                    "token",
                    "access_token",
                    "auth_token",
                    "/data/token",
                    "/payload/token",
                ],
            )
        });
        if valid(&candidate) {
            return Some(candidate);
        }
    }
    None
}
pub fn token_info(token: &str) -> Vec<Field> {
    use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
    let mut fields = vec![];
    if token.len() >= 12 && token.is_ascii() {
        fields.push(Field::new(
            "Токен МЭШ",
            format!("{}…{}", &token[..4], &token[token.len() - 4..]),
        ));
    }
    let claims = token
        .split('.')
        .nth(1)
        .and_then(|p| URL_SAFE_NO_PAD.decode(p.trim_end_matches('=')).ok())
        .and_then(|b| serde_json::from_slice::<Value>(&b).ok());
    if let Some(exp) = claims
        .and_then(|v| v["exp"].as_i64())
        .and_then(|t| chrono::DateTime::from_timestamp(t, 0))
    {
        fields.push(Field::new(
            "Срок действия",
            exp.format("%d.%m.%Y %H:%M UTC"),
        ));
    }
    fields
}
pub fn segment(value: &str) -> Result<&str> {
    if !value.is_empty()
        && value != "."
        && value != ".."
        && value.len() < 256
        && value
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || b"-_:.".contains(&c))
    {
        Ok(value)
    } else {
        Err("Некорректный идентификатор".into())
    }
}

pub struct Mesh {
    pub http: Client,
    base: String,
    pub token: String,
    pub account: Account,
    names: BTreeMap<String, String>,
    years: Option<Value>,
}
impl Mesh {
    pub fn new(http: Client, token: String) -> Self {
        Self {
            http,
            base: SCHOOL.into(),
            token,
            account: Account::default(),
            names: BTreeMap::new(),
            years: None,
        }
    }
    fn builder(
        &self,
        method: Method,
        path: &str,
        params: &[(&str, String)],
    ) -> reqwest::RequestBuilder {
        let mut r = self
            .http
            .request(method, format!("{}{path}", self.base))
            .query(params)
            .bearer_auth(&self.token)
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json")
            .header("X-Mes-RoleId", "1")
            .header("X-Mes-Subsystem", "familyweb")
            .header("Profile-Type", "student");
        if path.starts_with(FOOD) {
            r = r.header("Cookie", format!("aupd_token={}", self.token));
        } else {
            r = r.header("Auth-Token", &self.token);
        }
        if !self.account.active.is_empty() {
            r = r.header("Profile-Id", &self.account.active);
        }
        r
    }
    async fn send(
        &self,
        method: Method,
        path: &str,
        params: &[(&str, String)],
    ) -> Result<Response> {
        self.builder(method, path, params)
            .send()
            .await
            .map_err(network)
    }
    pub async fn refresh(&mut self) -> Result<()> {
        let res = self.send(Method::GET, REFRESH, &[]).await?;
        if !res.status().is_success() {
            return Err(status_error(res.status()));
        }
        self.token =
            token_value(&res.text().await.map_err(network)?).ok_or("Требуется повторный вход")?;
        Ok(())
    }
    async fn request(
        &mut self,
        method: Method,
        path: &str,
        args: &[(&str, String)],
    ) -> Result<Response> {
        let mut r = self.send(method.clone(), path, args).await?;
        if r.status() == StatusCode::UNAUTHORIZED {
            self.refresh().await?;
            r = self.send(method, path, args).await?;
        }
        if r.status().is_client_error() || r.status().is_server_error() {
            return Err(status_error(r.status()));
        }
        Ok(r)
    }
    async fn get(&mut self, path: &str, args: &[(&str, String)]) -> Result<Value> {
        let r = self.request(Method::GET, path, args).await?;
        if !r.status().is_success() {
            return Err(status_error(r.status()));
        }
        r.json()
            .await
            .map_err(|_| "Не удалось прочитать ответ МЭШ".into())
    }
    pub async fn identify(&mut self, active: &str) -> Result<()> {
        let raw = self.get(&format!("{FAMILY}/profile"), &[]).await?;
        let mut account = profile(&raw)?;
        if account.students.iter().any(|s| s.id == active) {
            account.active = active.into();
        }
        self.account = account;
        Ok(())
    }
    pub async fn page(&mut self, q: &Query) -> Result<Board> {
        let s = self.account.student().ok_or("Требуется вход")?.clone();
        let date = q.date.to_string();
        match q.section {
            Section::Schedule => Ok(diary::schedule(
                &self
                    .get(
                        &format!("{FAMILY}/schedule"),
                        &[("student_id", s.id), ("date", date.clone())],
                    )
                    .await?,
                &date,
            )),
            Section::Homework => {
                let params = [
                    ("student_id", s.id),
                    ("from", (q.date - Days::new(45)).to_string()),
                    ("to", (q.date + Days::new(45)).to_string()),
                ];
                Ok(diary::homework(
                    &self.get(&format!("{FAMILY}/homeworks"), &params).await?,
                    q,
                ))
            }
            Section::Grades if q.mode == "final" => {
                let years = if let Some(cache) = &self.years {
                    cache.clone()
                } else {
                    let v = self.get(YEARS, &[]).await?;
                    self.years = Some(v.clone());
                    v
                };
                let (choices, current) = grades::academic_years(&years);
                let selected = choices
                    .iter()
                    .find(|y| y.id == q.filter)
                    .map(|c| c.id.clone())
                    .unwrap_or_else(|| current.clone());
                let mut board = Board::default();
                if !selected.is_empty() {
                    let mut params = vec![
                        ("student_id", s.id),
                        ("person_id", s.person),
                        ("academic_year_id", selected.clone()),
                    ];
                    if selected != current {
                        params.push(("is_year_marks_only", "true".into()))
                    }
                    board = grades::finals(
                        &self
                            .get(&format!("{FAMILY}/final_marks/v2"), &params)
                            .await?,
                    );
                }
                board.choices = choices;
                board.selection = selected;
                Ok(board)
            }
            Section::Subjects | Section::Grades
                if q.section == Section::Subjects || q.mode == "subject" =>
            {
                Ok(grades::subjects(
                    &self
                        .get(&format!("{FAMILY}/subject_marks"), &[("student_id", s.id)])
                        .await?,
                ))
            }
            Section::Grades | Section::Subjects => {
                let from = (q.date - Days::new(q.days.clamp(1, 366) as u64)).to_string();
                Ok(grades::marks(
                    &self
                        .get(
                            &format!("{FAMILY}/marks"),
                            &[("student_id", s.id), ("from", from), ("to", date)],
                        )
                        .await?,
                ))
            }
            Section::Ranking => {
                let mut params = vec![("personId", s.person.clone()), ("date", date)];
                let subjects = self.get(&format!("{RANK}/subjects"), &params).await?;
                if !q.filter.is_empty() {
                    params.push(("subjectId", q.filter.clone()));
                }
                let raw = self.get(&format!("{RANK}/class"), &params).await?;
                // Resolve only members returned by the authenticated class ranking.
                let mut pending = std::collections::BTreeSet::new();
                for member in items(&raw) {
                    let id = field(member, &["personId", "person_id"]);
                    if segment(&id).is_ok()
                        && !self.names.contains_key(&id)
                        && fullname(member).is_empty()
                    {
                        pending.insert(id);
                    }
                }
                let mut pending = pending.into_iter();
                let mut tasks = tokio::task::JoinSet::new();
                let deadline = tokio::time::Instant::now() + Duration::from_secs(6);
                loop {
                    while tasks.len() < 5 {
                        let Some(id) = pending.next() else { break };
                        let req = self.builder(
                            Method::GET,
                            "/api/gamification/v1/profiles",
                            &[("personId", id.clone())],
                        );
                        tasks.spawn(async move {
                            let v = req
                                .send()
                                .await
                                .ok()?
                                .error_for_status()
                                .ok()?
                                .json::<Value>()
                                .await
                                .ok()?;
                            let mut name = [
                                field(&v, &["firstName", "first_name"]),
                                field(&v, &["lastName", "last_name"]),
                            ]
                            .into_iter()
                            .filter(|p| !p.is_empty())
                            .collect::<Vec<_>>()
                            .join(" ");
                            if name.is_empty() {
                                name = field(&v, &["name", "fio"]);
                            }
                            Some((id, name))
                        });
                    }
                    match tokio::time::timeout_at(deadline, tasks.join_next()).await {
                        Ok(Some(Ok(Some((id, name))))) if !name.is_empty() => {
                            self.names.insert(id, name);
                        }
                        Ok(Some(_)) => continue,
                        _ => break,
                    }
                }
                Ok(grades::ranking(
                    &raw,
                    &subjects,
                    &s.person,
                    q.mode == "month",
                    &self.names,
                ))
            }
            Section::Food => {
                let client = json!({"personId":s.person});
                let params = [("clientId", client.to_string())];
                let state = self
                    .get(&format!("{FOOD}/clients/allowed-food-state"), &params)
                    .await?;
                let balance = self
                    .get(
                        &format!("{FOOD}/clients/balance"),
                        &[("clientIds", json!([client]).to_string())],
                    )
                    .await?;
                let mut period = vec![params[0].clone(), ("from", date.clone()), ("to", date)];
                let (menu, orders) = if q.mode == "orders" {
                    period.push(("limit", "50".into()));
                    (
                        Value::Null,
                        self.get(&format!("{FOOD}/orders"), &period).await?,
                    )
                } else {
                    period.push(("withPreorder", "in".into()));
                    (
                        self.get(&format!("{FOOD}/menu/complexes"), &period).await?,
                        Value::Null,
                    )
                };
                Ok(services::food(&state, &balance, &menu, &orders, q))
            }
            Section::Profile => Ok(services::profile_board(&self.account)),
            Section::Settings | Section::Card => Ok(Board::default()),
        }
    }
    pub async fn done(&mut self, id: &str, kind: &str, done: bool) -> Result<()> {
        let path = format!("{FAMILY}/homeworks/{}/done", segment(id)?);
        let method = if done { Method::POST } else { Method::DELETE };
        let r = self
            .request(
                method,
                &path,
                &[("type", segment(kind)?.to_ascii_uppercase())],
            )
            .await?;
        if !r.status().is_success() {
            return Err(status_error(r.status()));
        }
        Ok(())
    }
    pub async fn mark(&mut self, id: &str) -> Result<Row> {
        let person = self
            .account
            .student()
            .ok_or("Требуется вход")?
            .person
            .clone();
        Ok(grades::detail(
            &self
                .get(
                    &format!("{FAMILY}/marks/{}", segment(id)?),
                    &[("person_id", person), ("markId", id.into())],
                )
                .await?,
        ))
    }
    pub async fn material(&mut self, entry: &str, id: &str) -> Result<String> {
        let r = self
            .request(
                Method::GET,
                "/api/ej/partners/v1/homeworks/launch",
                &[
                    ("homework_entry_id", segment(entry)?.into()),
                    ("material_id", segment(id)?.into()),
                ],
            )
            .await?;
        let location = r
            .headers()
            .get(reqwest::header::LOCATION)
            .and_then(|v| v.to_str().ok())
            .map(str::to_owned);
        let raw = r.text().await.map_err(network)?;
        let body = serde_json::from_str::<Value>(&raw).unwrap_or(Value::String(raw));
        let link = location.unwrap_or_else(|| {
            body.as_str().map(str::to_owned).unwrap_or_else(|| {
                field(
                    &body,
                    &["url", "link", "location", "redirect", "activity_url"],
                )
            })
        });
        if !link.starts_with('/') && Url::parse(&link).is_err() {
            return Err("Ссылка недоступна".into());
        }
        web_url(&link).ok_or_else(|| "Ссылка недоступна".into())
    }
}

#[cfg(test)]
#[path = "api_tests.rs"]
mod tests;
