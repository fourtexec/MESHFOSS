use super::*;
use std::io::{BufRead, BufReader, Write};
use std::net::TcpListener;
use std::thread;

const OLD: &str = "synthetic_school_session_00001";
const NEW: &str = "synthetic_school_session_00002";

fn fixture(responses: Vec<(&'static str, String)>) -> (Mesh, thread::JoinHandle<Vec<String>>) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let addr = listener.local_addr().unwrap();
    let server = thread::spawn(move || {
        let mut calls = vec![];
        for (status, body) in responses {
            let (mut stream, _) = listener.accept().unwrap();
            let mut buf = BufReader::new(stream.try_clone().unwrap());
            let mut request = String::new();
            loop {
                let mut line = String::new();
                buf.read_line(&mut line).unwrap();
                if line == "\r\n" || line.is_empty() {
                    break;
                }
                request.push_str(&line)
            }
            calls.push(request);
            write!(stream,"HTTP/1.1 {status}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",body.len()).unwrap();
        }
        calls
    });
    let mut m = Mesh::new(http().unwrap(), OLD.into());
    m.base = format!("http://{addr}");
    (m, server)
}
#[tokio::test]
async fn refreshes_once_and_persists_the_token_used_for_the_retry() {
    let profile = json!({"children":[{"id":1,"person_id":"p","name":"Demo"}]});
    let (mut api, server) = fixture(vec![
        ("401 Unauthorized", "{}".into()),
        ("200 OK", json!({"token":NEW}).to_string()),
        ("200 OK", profile.to_string()),
    ]);
    api.identify("").await.unwrap();
    assert_eq!(api.token, NEW);
    assert_eq!(api.account.active, "1");
    let calls = server.join().unwrap();
    assert!(calls[1].starts_with("GET /v2/token/refresh?roleId=1&subsystem=2 "));
    assert!(calls[2].contains(&format!("Bearer {NEW}")));
}
#[tokio::test]
async fn homework_toggle_waits_for_confirmation_and_passes_entry_type() {
    let (mut api, server) = fixture(vec![
        ("204 No Content", "".into()),
        ("403 Forbidden", "{}".into()),
    ]);
    api.done("321", "oo", true).await.unwrap();
    assert!(api.done("321", "oo", false).await.is_err());
    let calls = server.join().unwrap();
    assert!(calls[0].starts_with("POST /api/family/web/v1/homeworks/321/done?type=OO "));
    assert!(calls[1].starts_with("DELETE /api/family/web/v1/homeworks/321/done?type=OO "));
}
#[tokio::test]
async fn food_uses_the_family_profile_and_the_food_gateway_headers() {
    let (mut api, server) = fixture(vec![
        ("200 OK", "{}".into()),
        ("200 OK", "[]".into()),
        ("200 OK", "[]".into()),
    ]);
    api.account = mesh_core::demo::account();
    let q = Query::new(Section::Food, "2026-10-12".parse().unwrap());
    api.page(&q).await.unwrap();
    let calls = server.join().unwrap();
    for c in calls {
        let c = c.to_lowercase();
        assert!(c.contains("demo-person"));
        assert!(c.contains("profile-id: demo-student"));
        assert!(c.contains("cookie: aupd_token="));
        assert!(!c.contains("auth-token:"));
    }
}
#[test]
fn rejects_unsafe_path_segments_and_accepts_only_valid_token_values() {
    for bad in ["..", "a/b", "1?other=2", ""] {
        assert!(segment(bad).is_err());
    }
    assert_eq!(
        token_value(&json!({"access_token":OLD}).to_string()).as_deref(),
        Some(OLD)
    );
    assert_eq!(token_value("html error"), None);
}
