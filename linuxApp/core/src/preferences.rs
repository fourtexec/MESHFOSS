use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(default)]
pub struct Preferences {
    pub theme: String,
    pub style: String,
    pub accent: String,
    pub background: String,
    pub surface: String,
    pub language: String,
    pub channel: String,
    pub renew_when_closed: bool,
}
impl Default for Preferences {
    fn default() -> Self {
        Self {
            theme: "system".into(),
            style: "plain".into(),
            accent: "#176759".into(),
            background: "#f4f6f1".into(),
            surface: "#ffffff".into(),
            language: "ru".into(),
            channel: "stable".into(),
            renew_when_closed: false,
        }
    }
}
pub const PALETTES: [(&str, &str, &str, &str, &str); 10] = [
    ("nebula", "Nebula", "#C4B5FD", "#0B0818", "#1C1633"),
    ("midnight", "Midnight", "#9DE8CE", "#101715", "#1A2420"),
    ("ocean", "Ocean", "#67E8F9", "#071923", "#102A3A"),
    ("forest", "Forest", "#86EFAC", "#0D1B16", "#183126"),
    ("sunset", "Sunset", "#FDA4AF", "#211318", "#352027"),
    ("lavender", "Lavender", "#CFBAFF", "#1C182B", "#312842"),
    ("amber", "Amber", "#FFD17B", "#211B10", "#39301D"),
    ("glacier", "Glacier", "#005F80", "#E7F8FF", "#F8FCFF"),
    ("sakura", "Sakura", "#993853", "#FCEEF4", "#FFF8FB"),
    ("graphite", "Graphite", "#D1D5DB", "#141517", "#2B2D31"),
];
impl Preferences {
    pub fn colors(&self) -> Option<(&str, &str, &str)> {
        if self.theme == "custom" {
            return Some((&self.accent, &self.background, &self.surface));
        }
        PALETTES
            .iter()
            .find(|p| p.0 == self.theme)
            .map(|p| (p.2, p.3, p.4))
    }
    pub fn base_theme(&self) -> &str {
        if let Some((_, bg, _)) = self.colors() {
            if let Ok(rgb) = u32::from_str_radix(bg.trim_start_matches('#'), 16) {
                return if ((rgb >> 16) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114)
                    < 128000
                {
                    "dark"
                } else {
                    "light"
                };
            }
        }
        &self.theme
    }
    pub fn valid(&self) -> bool {
        (["system", "light", "dark", "custom"].contains(&self.theme.as_str())
            || self.colors().is_some())
            && ["plain", "glass", "liquid"].contains(&self.style.as_str())
            && ["ru", "en", "hu", "es", "fr"].contains(&self.language.as_str())
            && ["stable", "preview"].contains(&self.channel.as_str())
            && [&self.accent, &self.background, &self.surface]
                .iter()
                .all(|s| {
                    s.len() == 7
                        && s.starts_with('#')
                        && s[1..].bytes().all(|b| b.is_ascii_hexdigit())
                })
    }
}
#[derive(Clone, Default, Debug, Serialize, Deserialize)]
pub struct Start {
    pub account: Option<super::Account>,
    pub prefs: Preferences,
    pub message: String,
}
