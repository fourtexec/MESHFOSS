use crate::*;
#[derive(Clone, Serialize, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case")]
pub enum Action {
    Start,
    Demo,
    Login {
        token: String,
        remember: bool,
    },
    Portal {
        kind: String,
    },
    FinishPortal {
        kind: String,
        remember: bool,
    },
    CancelPortal,
    Logout,
    Refresh,
    SelectStudent {
        id: String,
    },
    Page {
        query: Query,
    },
    Done {
        id: String,
        kind: String,
        done: bool,
    },
    Mark {
        id: String,
    },
    Material {
        entry: String,
        resource: Resource,
    },
    Card {
        action: String,
    },
    SavePrefs {
        prefs: Preferences,
    },
    Updates {
        manual: bool,
    },
    Open {
        url: String,
    },
    Log {
        clear: bool,
    },
}
