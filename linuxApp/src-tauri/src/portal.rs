use crate::api::{Result, SCHOOL};
use tauri::{AppHandle, Manager, WebviewUrl, WebviewWindowBuilder};
use url::Url;
const LABEL: &str = "mesh-authorization";
pub fn open(app: &AppHandle, kind: &str, device: &str) -> Result<()> {
    close(app)?;
    let url = match kind {
        "school" => Url::parse(SCHOOL).unwrap(),
        // The identity provider draws the QR and handles phone approval and MFA in its own window.
        "qr" => Url::parse_with_params(
            "https://login.mos.ru/sps/oauth/ae",
            &[
                ("client_id", "dnevnik.mos.ru"),
                ("response_type", "code"),
                ("scope", "openid profile offline_access"),
                ("access_type", "offline"),
                (
                    "redirect_uri",
                    "https://school.mos.ru/v3/auth/sudir/callback",
                ),
            ],
        )
        .unwrap(),
        "card" => Url::parse(&format!(
            "https://id.mos.ru/api/v3/edm/auth/login/{device}/android"
        ))
        .map_err(|_| "Некорректный адрес")?,
        _ => return Err("Некорректный способ входа".into()),
    };
    WebviewWindowBuilder::new(app, LABEL, WebviewUrl::External(url))
        .title("Авторизация · mos.ru")
        .inner_size(840., 760.)
        .incognito(true)
        .on_navigation(|url| {
            let host = url.host_str().unwrap_or("");
            url.scheme() == "https"
                && (host == "mos.ru" || host.ends_with(".mos.ru") || host == "esia.gosuslugi.ru")
        })
        .on_new_window(|_, _| tauri::webview::NewWindowResponse::Deny)
        .build()
        .map(|_| ())
        .map_err(|_| "Не удалось открыть окно входа".into())
}
// Only the specific service cookie in this app's isolated login window is used.
pub fn result(app: &AppHandle, kind: &str) -> Result<String> {
    let win = app.get_webview_window(LABEL).ok_or("Окно входа закрыто")?;
    let (origin, name) = if kind == "card" {
        ("https://id.mos.ru", "sessionid")
    } else {
        (SCHOOL, "aupd_token")
    };
    let current = win.url().map_err(|_| "Вход ещё не завершён")?;
    let url = Url::parse(origin).unwrap();
    if current.scheme() != "https" || current.host_str() != url.host_str() {
        return Err("Вход ещё не завершён".into());
    }
    if kind == "card" && !current.path().contains("/auth/login_callback/") {
        return Err("Вход ещё не завершён".into());
    }
    win.cookies_for_url(url)
        .map_err(|_| "Вход ещё не завершён")?
        .into_iter()
        .find(|c| c.name() == name && !c.value().is_empty())
        .map(|c| c.value().to_string())
        .ok_or_else(|| "Вход ещё не завершён".into())
}
pub fn close(app: &AppHandle) -> Result<()> {
    if let Some(w) = app.get_webview_window(LABEL) {
        w.destroy().map_err(|_| "Не удалось закрыть окно входа")?;
    }
    Ok(())
}
