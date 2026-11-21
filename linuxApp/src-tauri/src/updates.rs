use crate::api::{network, status_error, Result};
use mesh_core::{field, list};
use reqwest::Client;
use semver::Version;
use serde_json::{json, Value};
use url::Url;

const REPOSITORY: &str = match option_env!("GITHUB_REPOSITORY") {
    Some(name) => name,
    None => "fourtexec/MESHFOSS",
};
pub fn pick(all: &Value, installed: &Version, preview: bool, manual: bool) -> Option<Value> {
    let mut best: Option<(Version, Value)> = None;
    for release in list(all, &[]) {
        if release["draft"] == true {
            continue;
        }
        let tag = field(release, &["tag_name"]);
        if (release["prerelease"] == true && !preview) || (tag == "nightly" && !manual) {
            continue;
        }
        let Some(asset) = list(release, &["assets"]).iter().find(|v| {
            let n = field(v, &["name"]).to_ascii_lowercase();
            n.ends_with(".appimage") && (n.contains("amd64") || n.contains("x86_64"))
        }) else {
            continue;
        };
        let version = tag.trim_start_matches("linux-v").trim_start_matches('v');
        let Ok(version) = Version::parse(version) else {
            continue;
        };
        if version.pre.as_str().starts_with("dev.") && !manual {
            continue;
        }
        if &version <= installed || best.as_ref().is_some_and(|(b, _)| b >= &version) {
            continue;
        }
        let url = field(asset, &["browser_download_url"]);
        if !Url::parse(&url).is_ok_and(|u| {
            u.scheme() == "https"
                && u.host_str() == Some("github.com")
                && u.path().to_ascii_lowercase().starts_with(&format!(
                    "/{}/releases/download/",
                    REPOSITORY.to_ascii_lowercase()
                ))
        }) {
            continue;
        }
        best = Some((
            version.clone(),
            json!({"version":version.to_string(),"url":url}),
        ));
    }
    best.map(|(_, v)| v)
}
pub async fn check(
    http: &Client,
    installed: &Version,
    preview: bool,
    manual: bool,
) -> Result<Value> {
    let r = http
        .get(format!(
            "https://api.github.com/repos/{REPOSITORY}/releases?per_page=50"
        ))
        .send()
        .await
        .map_err(network)?;
    if !r.status().is_success() {
        return Err(status_error(r.status()));
    }
    let releases = r
        .json()
        .await
        .map_err(|_| "Не удалось прочитать список обновлений")?;
    Ok(pick(&releases, installed, preview, manual).unwrap_or(Value::Null))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn update_follows_channel_version_and_linux_assets() {
        let release = |version: &str, preview: bool, filename: &str| {
            json!({
                "tag_name":format!("linux-v{version}"),"prerelease":preview,
                "assets":[{"name":filename,"browser_download_url":format!("https://github.com/{REPOSITORY}/releases/download/linux-v{version}/{filename}")}]
            })
        };
        let releases = json!([
            release("2.0.2", false, "android.apk"),
            release("2.0.1", false, "mesh_amd64.AppImage"),
            release("2.0.3-dev.7", true, "mesh_amd64.AppImage")
        ]);
        let installed = Version::parse("2.0.0").unwrap();
        assert_eq!(
            pick(&releases, &installed, false, true).unwrap()["version"],
            "2.0.1"
        );
        assert_eq!(
            pick(&releases, &installed, true, false).unwrap()["version"],
            "2.0.1"
        );
        assert_eq!(
            pick(&releases, &installed, true, true).unwrap()["version"],
            "2.0.3-dev.7"
        );
        assert!(pick(&releases, &Version::parse("2.1.0").unwrap(), true, true).is_none());
    }
}
