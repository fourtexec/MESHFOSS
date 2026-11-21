use crate::{
    api::{network, segment, status_error, Result},
    storage::Credentials,
};
use base64::{engine::general_purpose::STANDARD, Engine};
use chrono::Utc;
use mesh_core::{field, list, Card, Field};
use qrcode::{render::svg, QrCode};
use reqwest::{Client, Method, StatusCode};
use serde_json::{json, Value};
use std::collections::VecDeque;

pub const ID: &str = "https://id.mos.ru";
pub fn qr(text: &str) -> Result<String> {
    let code = QrCode::new(text.as_bytes()).map_err(|_| "Не удалось отобразить QR-код")?;
    let svg = code.render::<svg::Color>().min_dimensions(240, 240).build();
    Ok(format!(
        "data:image/svg+xml;base64,{}",
        STANDARD.encode(svg)
    ))
}
pub struct Documents {
    pub log: VecDeque<String>,
}
impl Documents {
    pub fn new() -> Self {
        Self {
            log: VecDeque::new(),
        }
    }
    async fn request(
        &mut self,
        http: &Client,
        secrets: &Credentials,
        method: Method,
        path: &str,
        force: bool,
        body: Option<Value>,
    ) -> Result<(StatusCode, Value)> {
        let mut req = http
            .request(method.clone(), format!("{ID}/api/v3/edm/mp/{path}"))
            .header("x-sessionid", &secrets.card)
            .header("x-deviceid", &secrets.device)
            .header("Content-Type", "application/json");
        if force {
            req = req.header("x-force-refresh", "true")
        }
        if let Some(json) = body {
            req = req.json(&json)
        }
        let response = req.send().await.map_err(network)?;
        let status = response.status();
        if self.log.len() >= 120 {
            self.log.pop_front();
        }
        let action = if path.starts_with("documents/") {
            "document"
        } else {
            path
        };
        self.log.push_back(format!(
            "{}  {} {}  {}",
            Utc::now().format("%H:%M:%S UTC"),
            method,
            action,
            status.as_u16()
        ));
        if status == StatusCode::UNAUTHORIZED || status == StatusCode::FORBIDDEN {
            return Err("Требуется вход для карты".into());
        }
        if status.is_redirection() && method == Method::POST {
            return Ok((status, Value::Null));
        }
        if !status.is_success() {
            return Err(status_error(status));
        }
        let text = response.text().await.map_err(network)?;
        let data = if text.trim().is_empty() || status == StatusCode::ACCEPTED {
            Value::Null
        } else {
            serde_json::from_str(&text).map_err(|_| "Не удалось прочитать ответ МЭШ")?
        };
        Ok((status, data))
    }
    pub async fn load(
        &mut self,
        http: &Client,
        secrets: &Credentials,
        action: &str,
        student: &str,
    ) -> Result<Card> {
        let state = |s: &str| Card {
            state: s.into(),
            ..Default::default()
        };
        if secrets.card.is_empty() {
            return Ok(state("login"));
        }
        let (code, objects) = self
            .request(http, secrets, Method::GET, "objects", false, None)
            .await?;
        if code == StatusCode::ACCEPTED {
            return Ok(state("processing"));
        }
        let mut entries = Vec::new();
        for group in list(&objects, &["groups"]) {
            for item in list(group, &["items"]) {
                if item["type"] == "mesh" {
                    entries.push(item.clone())
                }
            }
        }
        if entries.is_empty() {
            if action == "enable" {
                self.request(
                    http,
                    secrets,
                    Method::POST,
                    "settings/docs",
                    false,
                    Some(json!({"profile":[22],"favorites":[22]})),
                )
                .await?;
                return Ok(state("processing"));
            }
            return Ok(state("disabled"));
        }
        // A document must come from this account's document list.
        let chosen = entries
            .iter()
            .find(|v| {
                field(v, &["person_id", "personId", "contingent_guid"]) == student
                    || field(v, &["id"]).contains(student)
            })
            .or_else(|| (entries.len() == 1).then(|| &entries[0]));
        let Some(item) = chosen else {
            return Ok(state("unavailable"));
        };
        let id = field(item, &["id"]);
        if id.is_empty() {
            return Ok(state("processing"));
        }
        let path = id
            .split('/')
            .map(segment)
            .collect::<Result<Vec<_>>>()?
            .join("/");
        let (code, doc) = self
            .request(
                http,
                secrets,
                Method::GET,
                &format!("documents/{path}?type=mesh"),
                action == "refresh",
                None,
            )
            .await?;
        let payload = field(&doc, &["barcode_payload"]);
        if code == StatusCode::ACCEPTED || payload.is_empty() {
            return Ok(state("processing"));
        }
        let ttl = doc["barcode_valid_time"]
            .as_i64()
            .or_else(|| doc["barcode_valid_time"].as_str()?.parse().ok())
            .unwrap_or(0);
        let fields = list(&doc, &["fields"])
            .iter()
            .filter(|f| f["type"] == "docname")
            .map(|f| Field::new(&field(f, &["label"]), field(f, &["value"])))
            .collect();
        Ok(Card {
            state: "ready".into(),
            image: qr(&payload)?,
            expires: if ttl > 0 {
                Utc::now().timestamp_millis().saturating_add(ttl)
            } else {
                0
            },
            fields,
        })
    }
}
