use crate::api::Result;
use mesh_core::Preferences;
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeSet,
    fs,
    io::Write,
    path::{Path, PathBuf},
};

#[derive(Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Local {
    pub prefs: Preferences,
    pub demo: bool,
    pub completed: BTreeSet<String>,
}
#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Credentials {
    pub token: String,
    pub active: String,
    pub device: String,
    pub card: String,
}
pub struct Storage {
    path: PathBuf,
}
impl Storage {
    pub fn new(dir: PathBuf) -> Self {
        Self {
            path: dir.join("desktop.json"),
        }
    }
    pub fn load(&self) -> Result<Local> {
        match fs::read(&self.path) {
            Ok(bytes) => {
                serde_json::from_slice(&bytes).map_err(|_| "Не удалось прочитать настройки".into())
            }
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Local::default()),
            Err(_) => Err("Не удалось прочитать настройки".into()),
        }
    }
    pub fn save(&self, data: &Local) -> Result<()> {
        let dir = self.path.parent().unwrap_or(Path::new("."));
        fs::create_dir_all(dir).map_err(|_| "Не удалось сохранить настройки")?;
        let temp = dir.join(format!(".settings-{}.tmp", uuid::Uuid::new_v4()));
        let mut options = fs::OpenOptions::new();
        options.create_new(true).write(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            options.mode(0o600);
        }
        let result = (|| -> std::io::Result<()> {
            let mut f = options.open(&temp)?;
            serde_json::to_writer(&mut f, data)?;
            f.flush()?;
            f.sync_all()?;
            fs::rename(&temp, &self.path)
        })();
        if result.is_err() {
            let _ = fs::remove_file(temp);
        }
        result.map_err(|_| "Не удалось сохранить настройки".into())
    }
}
const KEYRING: &str = "Связка ключей недоступна";
fn key() -> Result<keyring::Entry> {
    keyring::Entry::new("ru.meshfoss.desktop", "school-session").map_err(|_| KEYRING.into())
}
pub fn read_secret() -> Result<Option<Credentials>> {
    match key()?.get_password() {
        Ok(s) => serde_json::from_str(&s)
            .map(Some)
            .map_err(|_| "Не удалось прочитать сессию".into()),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(_) => Err(KEYRING.into()),
    }
}
pub fn write_secret(s: &Credentials) -> Result<()> {
    key()?
        .set_password(&serde_json::to_string(s).map_err(|_| KEYRING)?)
        .map_err(|_| KEYRING.into())
}
pub fn clear_secret() -> Result<()> {
    match key()?.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(_) => Err(KEYRING.into()),
    }
}
