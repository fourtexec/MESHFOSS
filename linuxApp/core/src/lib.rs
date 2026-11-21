pub mod actions;
pub use actions::Action;
pub mod preferences;
pub use preferences::{Preferences, Start};
pub mod demo;
pub mod diary;
pub mod grades;
pub mod services;

use chrono::{Datelike, Days, NaiveDate, Weekday};
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Clone, Copy, Debug, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum Section {
    #[default]
    Schedule,
    Homework,
    Grades,
    Subjects,
    Ranking,
    Food,
    Card,
    Profile,
    Settings,
}

impl Section {
    pub const ALL: [Self; 9] = [
        Self::Schedule,
        Self::Homework,
        Self::Grades,
        Self::Subjects,
        Self::Ranking,
        Self::Food,
        Self::Card,
        Self::Profile,
        Self::Settings,
    ];
    pub fn label(self) -> &'static str {
        match self {
            Self::Schedule => "Расписание",
            Self::Homework => "Домашние задания",
            Self::Grades => "Оценки",
            Self::Subjects => "Предметы",
            Self::Ranking => "Рейтинг",
            Self::Food => "Питание",
            Self::Card => "Москвёнок",
            Self::Profile => "Профиль",
            Self::Settings => "Настройки",
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Query {
    pub section: Section,
    pub date: NaiveDate,
    pub mode: String,
    pub filter: String,
    #[serde(default)]
    pub days: u32,
}

impl Query {
    pub fn new(section: Section, today: NaiveDate) -> Self {
        let mut day = today;
        if section == Section::Homework {
            day = today
                + Days::new(match today.weekday() {
                    Weekday::Fri => 3,
                    Weekday::Sat => 2,
                    _ => 1,
                });
        }
        Self {
            section,
            date: day,
            mode: String::new(),
            filter: String::new(),
            days: 30,
        }
    }
}

#[derive(Default, Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Field {
    pub label: String,
    pub value: String,
}
impl Field {
    pub fn new(label: &str, value: impl ToString) -> Self {
        Self {
            label: label.into(),
            value: value.to_string(),
        }
    }
}

#[derive(Default, Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Choice {
    pub id: String,
    pub name: String,
}
#[derive(Default, Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Resource {
    pub title: String,
    pub url: String,
    pub material: String,
}
#[derive(Default, Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Row {
    pub id: String,
    pub title: String,
    pub subtitle: String,
    pub leading: String,
    pub badge: String,
    pub fields: Vec<Field>,
    pub description: String,
    pub links: Vec<Resource>,
    pub children: Vec<Row>,
    pub subject: String,
    pub entry: String,
    pub kind: String,
    pub done: Option<bool>,
    pub assigned: String,
    pub due: String,
    pub selected: bool,
}
impl Row {
    pub fn field(&mut self, key: &str, value: impl ToString) {
        let value = value.to_string();
        if !value.trim().is_empty() {
            self.fields.push(Field::new(key, value));
        }
    }
}
#[derive(Default, Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Board {
    pub rows: Vec<Row>,
    pub stats: Vec<Field>,
    pub choices: Vec<Choice>,
    pub selection: String,
    pub feeding_days: Vec<String>,
    pub chart: Vec<Field>,
    pub trend: Vec<Field>,
    pub notice: String,
    pub card: Option<Card>,
}
#[derive(Default, Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Card {
    pub state: String,
    pub image: String,
    pub expires: i64,
    pub fields: Vec<Field>,
}

#[derive(Default, Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Student {
    pub id: String,
    pub person: String,
    pub name: String,
    pub class_name: String,
    pub school: String,
    pub email: String,
    pub phone: String,
}
#[derive(Default, Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct Account {
    pub students: Vec<Student>,
    pub active: String,
    pub parent: String,
    pub email: String,
    pub phone: String,
    pub demo: bool,
    pub remembered: bool,
    pub token_info: Vec<Field>,
}
impl Account {
    pub fn student(&self) -> Option<&Student> {
        self.students.iter().find(|s| s.id == self.active)
    }
}
pub fn scalar(v: &Value) -> String {
    match v {
        Value::String(s) => s.trim().into(),
        Value::Number(n) => n.to_string(),
        _ => String::new(),
    }
}
// Keys may be nested JSON pointers or simple field names. APIs vary between school years.
pub fn val<'a>(v: &'a Value, key: &str) -> &'a Value {
    if key.starts_with('/') {
        v.pointer(key).unwrap_or(&Value::Null)
    } else {
        &v[key]
    }
}
pub fn field(v: &Value, keys: &[&str]) -> String {
    keys.iter()
        .map(|key| scalar(val(v, key)))
        .find(|s| !s.is_empty())
        .unwrap_or_default()
}
pub fn number(v: &Value, keys: &[&str]) -> Option<f64> {
    keys.iter()
        .find_map(|key| {
            let s = val(v, key);
            s.as_f64()
                .or_else(|| s.as_str()?.replace(',', ".").parse().ok())
        })
        .filter(|x| x.is_finite())
}
pub fn yes(v: &Value) -> bool {
    v.as_bool().unwrap_or(false)
        || v.as_i64() == Some(1)
        || matches!(v.as_str(), Some("true" | "1"))
}
pub fn list<'a>(v: &'a Value, keys: &[&str]) -> &'a [Value] {
    v.as_array()
        .or_else(|| keys.iter().find_map(|key| val(v, key).as_array()))
        .map(Vec::as_slice)
        .unwrap_or_default()
}
pub fn items(v: &Value) -> &[Value] {
    list(v, &["payload", "items", "data", "lessons", "activities"])
}
pub fn iso(s: &str) -> String {
    s.chars().take(10).collect()
}
pub fn display(n: Option<f64>) -> String {
    n.map(|n| {
        format!("{n:.2}")
            .trim_end_matches('0')
            .trim_end_matches('.')
            .replace('.', ",")
    })
    .unwrap_or_else(|| "—".into())
}
pub fn join(parts: &[String]) -> String {
    parts
        .iter()
        .filter(|s| !s.is_empty())
        .cloned()
        .collect::<Vec<_>>()
        .join(" · ")
}
pub fn fullname(v: &Value) -> String {
    if let Some(s) = v.as_str() {
        return s.to_string();
    }
    let parts = [
        field(v, &["last_name", "lastName"]),
        field(v, &["first_name", "firstName"]),
        field(v, &["middle_name", "middleName"]),
    ];
    let name = parts
        .iter()
        .filter(|s| !s.is_empty())
        .cloned()
        .collect::<Vec<_>>()
        .join(" ");
    if name.is_empty() {
        field(v, &["name", "full_name", "displayName", "fullName"])
    } else {
        name
    }
}
pub fn profile(raw: &Value) -> Result<Account, String> {
    let children = list(raw, &["children"]);
    let students: Vec<Student> = children
        .iter()
        .map(|v| Student {
            id: field(v, &["id", "student_id", "user_id"]),
            person: field(v, &["contingent_guid", "person_id", "personId"]),
            name: fullname(v),
            class_name: field(v, &["class_name", "className"]),
            school: field(
                v,
                &[
                    "school_name",
                    "schoolName",
                    "/school/short_name",
                    "/school/name",
                ],
            ),
            email: field(v, &["email"]),
            phone: field(v, &["phone", "mobile_phone"]),
        })
        .filter(|s| !s.id.is_empty() && !s.person.is_empty())
        .collect();
    let active = students.first().ok_or("В профиле нет ученика")?.id.clone();
    let rep = children
        .first()
        .and_then(|c| c.pointer("/representatives/0"))
        .unwrap_or(&raw["profile"]);
    Ok(Account {
        students,
        active,
        parent: fullname(rep),
        email: field(rep, &["email"]),
        phone: field(rep, &["phone", "mobile_phone"]),
        ..Default::default()
    })
}
pub fn web_url(s: &str) -> Option<String> {
    if s.is_empty() {
        return None;
    }
    let url = url::Url::parse("https://school.mos.ru/")
        .ok()?
        .join(s.trim())
        .ok()?;
    (matches!(url.scheme(), "http" | "https")
        && url.has_host()
        && url.username().is_empty()
        && url.password().is_none())
    .then(|| url.to_string())
}
