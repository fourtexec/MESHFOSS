use crate::{
    api::{self, Mesh, Result},
    storage,
};
use std::{
    fs::{self, File, OpenOptions},
    path::{Path, PathBuf},
    process::Command,
};
const UNIT: &str = "meshfoss-session";

fn config_dir() -> Result<PathBuf> {
    std::env::var_os("XDG_CONFIG_HOME")
        .map(PathBuf::from)
        .filter(|p| p.is_absolute())
        .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".config")))
        .ok_or_else(|| "Не удалось прочитать настройки".into())
}
// The foreground process owns this lock. A timer firing while it is open skips its run.
pub fn lock(dir: &Path, wait: bool) -> Result<Option<File>> {
    fs::create_dir_all(dir).map_err(|_| "Не удалось сохранить настройки")?;
    let f = OpenOptions::new()
        .create(true)
        .truncate(false)
        .write(true)
        .open(dir.join("session.lock"))
        .map_err(|_| "Не удалось сохранить настройки")?;
    if wait {
        f.lock().map_err(|_| "Не удалось сохранить настройки")?;
        return Ok(Some(f));
    }
    match f.try_lock() {
        Ok(()) => Ok(Some(f)),
        Err(std::fs::TryLockError::WouldBlock) => Ok(None),
        Err(_) => Err("Не удалось сохранить настройки".into()),
    }
}
fn exec_path(path: &Path) -> Result<String> {
    let p = path.to_str().ok_or("Некорректный адрес")?;
    if !path.is_absolute() || p.chars().any(char::is_control) {
        return Err("Некорректный адрес".into());
    }
    // Unit-file escaping and specifier/environment expansion apply even inside double quotes.
    Ok(format!(
        "\"{}\"",
        p.replace('\\', "\\\\")
            .replace('"', "\\\"")
            .replace('%', "%%")
            .replace('$', "$$")
    ))
}
fn systemctl(args: &[&str]) -> Result<()> {
    if Command::new("systemctl")
        .arg("--user")
        .args(args)
        .status()
        .is_ok_and(|s| s.success())
    {
        Ok(())
    } else {
        Err("Служба фонового обновления недоступна".into())
    }
}
pub fn configure(enabled: bool) -> Result<()> {
    let dir = config_dir()?.join("systemd/user");
    let service = dir.join(format!("{UNIT}.service"));
    let timer = dir.join(format!("{UNIT}.timer"));
    if !enabled {
        if timer.exists() {
            systemctl(&["disable", "--now", "meshfoss-session.timer"])?;
        }
        for f in [timer, service] {
            if f.exists() {
                fs::remove_file(f).map_err(|_| "Не удалось сохранить настройки")?;
            }
        }
        return systemctl(&["daemon-reload"]);
    }
    let exe = std::env::var_os("APPIMAGE")
        .map(PathBuf::from)
        .or_else(|| std::env::current_exe().ok())
        .ok_or("Некорректный адрес")?;
    let quoted = exec_path(&exe)?;
    fs::create_dir_all(&dir).map_err(|_| "Не удалось сохранить настройки")?;
    fs::write(&service,format!("[Unit]\nDescription=MESHFOSS session renewal\n[Service]\nType=oneshot\nExecStart={quoted} --refresh-session\n")).map_err(|_|"Не удалось сохранить настройки")?;
    fs::write(&timer,"[Unit]\nDescription=Renew the MESHFOSS session twice daily\n[Timer]\nOnCalendar=*-*-* 00,12:00:00\nPersistent=true\n[Install]\nWantedBy=timers.target\n").map_err(|_|"Не удалось сохранить настройки")?;
    systemctl(&["daemon-reload"])?;
    systemctl(&["enable", "--now", "meshfoss-session.timer"])
}
pub async fn renew() -> Result<()> {
    let dir = config_dir()?.join("ru.meshfoss.desktop");
    let Some(_guard) = lock(&dir, false)? else {
        return Ok(());
    };
    let settings = storage::Storage::new(dir).load()?;
    if !settings.prefs.renew_when_closed || settings.demo {
        return Ok(());
    }
    let Some(mut saved) = storage::read_secret()? else {
        return Ok(());
    };
    let mut client = Mesh::new(api::http()?, saved.token.clone());
    if let Err(err) = client.refresh().await {
        if err == "Требуется повторный вход" {
            let label = match settings.prefs.language.as_str() {
                "en" => "Please sign in again",
                "fr" => "Veuillez vous reconnecter",
                "es" => "Vuelve a iniciar sesión",
                "hu" => "Jelentkezzen be újra",
                _ => "Требуется повторный вход",
            };
            let _ = Command::new("notify-send")
                .args(["MESHFOSS", label])
                .status();
        }
        return Err(err);
    }
    saved.token = client.token;
    storage::write_secret(&saved)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn escapes_systemd_path_without_interpreting_percent_or_dollar() {
        assert_eq!(
            exec_path(Path::new("/tmp/My $app 50%.AppImage")).unwrap(),
            "\"/tmp/My $$app 50%%.AppImage\""
        );
        assert!(exec_path(Path::new("/tmp/foo\nbar")).is_err());
    }
}
