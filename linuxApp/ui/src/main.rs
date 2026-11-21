mod i18n;
mod icons;
mod screens;
mod settings;
mod state;

use chrono::{Datelike, Days, NaiveDate};
use icons::Icon;
use leptos::prelude::*;
use mesh_core::Action;
use mesh_core::*;
use serde_json::Value;
use state::{today, Ui};

pub fn date_name(day: NaiveDate, lang: &str, long: bool) -> String {
    let date = js_sys::Date::new_with_year_month_day(
        day.year() as u32,
        day.month0() as i32,
        day.day() as i32,
    );
    let options = js_sys::Object::new();
    for (k, v) in if long {
        vec![("weekday", "long"), ("day", "numeric"), ("month", "long")]
    } else {
        vec![("day", "numeric"), ("month", "short")]
    } {
        let _ = js_sys::Reflect::set(&options, &k.into(), &v.into());
    }
    date.to_locale_date_string(lang, &options)
        .as_string()
        .unwrap_or_else(|| day.to_string())
}
#[component]
fn Brand() -> impl IntoView {
    view! {
        <div class="brand">
            <span class="logo">
                <Icon name="book" />
            </span>
            <b>"МЭШ"<span class="brand-foss">"FOSS"</span></b>
        </div>
    }
}

#[component]
fn Notices(u: Ui) -> impl IntoView {
    view! {
        <div class="messages" aria-live="polite">
            {move || {
                (!u.error.get().is_empty())
                    .then(|| {
                        view! {
                            <div class="message error">
                                <span>{u.t(&u.error.get())}</span>
                                <button
                                    aria-label=move || u.t("Закрыть")
                                    class="icon-btn"
                                    on:click=move |_| u.error.set(String::new())
                                >
                                    <Icon name="close" />
                                </button>
                            </div>
                        }
                    })
            }}
            {move || {
                (!u.info.get().is_empty())
                    .then(|| {
                        view! {
                            <div class="message">
                                <span>{u.t(&u.info.get())}</span>
                                <button
                                    aria-label=move || u.t("Закрыть")
                                    class="icon-btn"
                                    on:click=move |_| u.info.set(String::new())
                                >
                                    <Icon name="close" />
                                </button>
                            </div>
                        }
                    })
            }}
            {move || {
                u.update
                    .get()
                    .map(|(version, url)| {
                        view! {
                            <div class="message">
                                <span>
                                    {u.t("Доступна новая версия")}" "{version}
                                </span>
                                <button on:click=move |_| {
                                    u.open(url.clone())
                                }>{u.t("Скачать")}</button>
                                <button on:click=move |_| {
                                    u.update.set(None)
                                }>{u.t("Позже")}</button>
                            </div>
                        }
                    })
            }}
        </div>
    }
}
#[component]
fn PortalStatus(u: Ui) -> impl IntoView {
    view! {
        <div class="portal-panel">
            <p>
                {move || {
                    u.t(
                        if u.portal.get() == "qr" {
                            "В окне mos.ru выберите QR-код и подтвердите вход на телефоне."
                        } else {
                            "Завершите авторизацию в окне mos.ru и вернитесь сюда."
                        },
                    )
                }}
            </p>
            <button
                class="primary"
                disabled=move || u.working.get()
                on:click=move |_| u.portal_finish()
            >
                {move || u.t("Завершить вход")}
            </button>
            <button
                class="quiet"
                on:click=move |_| {
                    u.action(Action::CancelPortal, move |_: Value| u.portal.set(String::new()))
                }
            >
                {move || u.t("Отмена")}
            </button>
        </div>
    }
}
#[component]
fn Login(u: Ui) -> impl IntoView {
    let token = RwSignal::new(String::new());
    view! {
        <main class="welcome">
            <div class="welcome-header">
                <Brand />
                <settings::Language u />
            </div>
            <div class="welcome-grid">
                <section class="intro">
                    <h1>{move || u.t("Школьный дневник")}</h1>
                    <p>
                        {move || {
                            u.t(
                                "Расписание, задания и оценки Московской электронной школы.",
                            )
                        }}
                    </p>
                    <div class="preview">
                        <div class="preview-top">
                            <span>
                                <Icon name="Расписание" />
                                {move || u.t("Пример расписания")}
                            </span>
                            <i></i>
                        </div>
                        {[
                            ("08:30", "Алгебра"),
                            ("09:25", "Русский язык"),
                            ("10:30", "Физика"),
                        ]
                            .into_iter()
                            .map(|(time, name)| {
                                view! {
                                    <div class="preview-row">
                                        <time>{time}</time>
                                        <b>{move || u.t(name)}</b>
                                    </div>
                                }
                            })
                            .collect_view()}
                    </div>
                </section>
                <section class="login-card">
                    <span class="login-symbol">
                        <Icon name="book" />
                    </span>
                    <h2>{move || u.t("Вход в МЭШ")}</h2>
                    <Show
                        when=move || u.portal.get().is_empty()
                        fallback=move || view! { <PortalStatus u /> }
                    >
                        <button
                            class="primary wide"
                            disabled=move || u.working.get()
                            on:click=move |_| u.start_portal("school")
                        >
                            {move || u.t("Войти через mos.ru")}
                            <Icon name="link" />
                        </button>
                        <button
                            class="secondary wide"
                            disabled=move || u.working.get()
                            on:click=move |_| u.start_portal("qr")
                        >
                            <Icon name="qr" />
                            {move || u.t("По QR-коду")}
                        </button>
                        <details class="token">
                            <summary>{move || u.t("Ввести токен")}</summary>
                            <form on:submit=move |e| {
                                e.prevent_default();
                                u.session(Action::Login {
                                    token: token.get_untracked(),
                                    remember: u.remember.get_untracked(),
                                });
                            }>
                                <label>
                                    {move || u.t("Токен МЭШ")}
                                    <input
                                        type="password"
                                        autocomplete="off"
                                        required
                                        prop:value=move || token.get()
                                        on:input=move |e| token.set(event_target_value(&e))
                                    />
                                </label>
                                <button
                                    type="button"
                                    class="text-btn"
                                    on:click=move |_| {
                                        u.open(
                                            "https://school.mos.ru/v2/token/refresh?roleId=1&subsystem=2"
                                                .into(),
                                        )
                                    }
                                >
                                    {move || {
                                        u.t("Получить токен на сайте МЭШ")
                                    }}
                                    <Icon name="link" />
                                </button>
                                <button
                                    class="secondary"
                                    type="submit"
                                    disabled=move || {
                                        u.working.get() || token.get().trim().is_empty()
                                    }
                                >
                                    {move || u.t("Войти")}
                                </button>
                            </form>
                        </details>
                        <label class="remember">
                            <input
                                type="checkbox"
                                prop:checked=move || u.remember.get()
                                on:change=move |e| u.remember.set(event_target_checked(&e))
                            />
                            {move || u.t("Запомнить меня на этом ПК")}
                        </label>
                        <div class="login-bottom">
                            <button
                                class="text-btn"
                                disabled=move || u.working.get()
                                on:click=move |_| u.session(Action::Demo)
                            >
                                {move || u.t("Открыть демоверсию")}
                                <Icon name="next" />
                            </button>
                        </div>
                    </Show>
                    <Show when=move || u.working.get()>
                        <span class="progress" role="status">
                            {move || u.t("Загрузка…")}
                        </span>
                    </Show>
                    <Notices u />
                    <Show when=move || !u.info.get().is_empty() && u.portal.get().is_empty()>
                        <button class="text-btn" on:click=move |_| u.session(Action::Start)>
                            {move || u.t("Обновить")}
                        </button>
                    </Show>
                </section>
            </div>
        </main>
    }
}

#[component]
fn Nav(u: Ui, section: Section) -> impl IntoView {
    view! {
        <button
            class="nav-item"
            class:active=move || u.q.get().section == section
            aria-current=move || if u.q.get().section == section { "page" } else { "false" }
            title=move || u.t(section.label())
            aria-label=move || u.t(section.label())
            on:click=move |_| u.navigate(section)
        >
            <Icon name=section.label() />
            <span>{move || u.t(section.label())}</span>
        </button>
    }
}
#[component]
fn Shell(u: Ui) -> impl IntoView {
    view! {
        <div class="shell">
            <aside class="sidebar">
                <Brand />
                <nav aria-label=move || {
                    u.t("Разделы")
                }>
                    {Section::ALL[..7]
                        .iter()
                        .copied()
                        .map(|section| view! { <Nav u section /> })
                        .collect_view()}
                </nav>
                <div class="side-foot">
                    <Nav u section=Section::Settings />
                    <button
                        class="account"
                        title=move || u.t("Профиль")
                        on:click=move |_| u.navigate(Section::Profile)
                    >
                        <span class="avatar">
                            {move || {
                                u.account
                                    .get()
                                    .and_then(|a| {
                                        a.student().map(|s| s.name.chars().next().unwrap_or('М'))
                                    })
                            }}
                        </span>
                        <span>
                            <b>
                                {move || {
                                    u.account
                                        .get()
                                        .and_then(|a| a.student().map(|s| s.name.clone()))
                                }}
                            </b>
                            <small>
                                {move || {
                                    u.account
                                        .get()
                                        .and_then(|a| {
                                            a.student()
                                                .map(|s| {
                                                    if a.demo {
                                                        u.t("Демоверсия")
                                                    } else {
                                                        s.class_name.clone()
                                                    }
                                                })
                                        })
                                }}
                            </small>
                        </span>
                    </button>
                </div>
            </aside>
            <main class="workspace">
                <header class="header">
                    <div>
                        <div class="date-line">
                            {move || date_name(today(), &u.prefs.get().language, true)}
                        </div>
                        <h1>{move || u.t(u.q.get().section.label())}</h1>
                    </div>
                    <Show when=move || u.q.get().section != Section::Settings>
                        <button
                            class="icon-btn refresh"
                            title=move || u.t("Обновить")
                            aria-label=move || u.t("Обновить")
                            disabled=move || u.busy.get()
                            on:click=move |_| u.reload()
                        >
                            <Icon name="refresh" />
                        </button>
                    </Show>
                </header>
                <Notices u />
                <Show
                    when=move || u.q.get().section != Section::Settings
                    fallback=move || view! { <settings::Settings u /> }
                >
                    <Toolbar u />
                    <screens::Dashboard u />
                </Show>
            </main>
        </div>
    }
}
#[component]
fn Toolbar(u: Ui) -> impl IntoView {
    let change = move |f: fn(&mut Query)| {
        let mut q = u.q.get_untracked();
        f(&mut q);
        u.load(q, None, false)
    };
    view! {
        <div class="toolbar">
            {move || {
                let q = u.q.get();
                let choices: &[(&str, &str)] = match q.section {
                    Section::Grades => {
                        &[
                            ("", "По дате"),
                            ("subject", "По предмету"),
                            ("final", "Итоговые"),
                        ]
                    }
                    Section::Ranking => &[("", "Год"), ("month", "Месяц")],
                    Section::Food => {
                        &[
                            ("", "Платное"),
                            ("free", "Льготное"),
                            ("orders", "Предзаказы"),
                        ]
                    }
                    Section::Homework => &[("", "К сроку"), ("assigned", "Задано")],
                    _ => &[],
                };
                (!choices.is_empty())
                    .then(|| {
                        view! {
                            <div class="segments">
                                {choices
                                    .iter()
                                    .copied()
                                    .map(|(value, label)| {
                                        view! {
                                            <button
                                                class:chosen=move || u.q.get().mode == value
                                                aria-pressed=move || u.q.get().mode == value
                                                on:click=move |_| {
                                                    let mut q = u.q.get_untracked();
                                                    q.mode = value.into();
                                                    q.filter.clear();
                                                    u.load(q, None, false)
                                                }
                                            >
                                                {move || u.t(label)}
                                            </button>
                                        }
                                    })
                                    .collect_view()}
                            </div>
                        }
                    })
            }}
            <Show when=move || {
                matches!(
                    u.q.get().section,
                    Section::Schedule | Section::Homework | Section::Food | Section::Ranking
                ) || (u.q.get().section == Section::Grades && u.q.get().mode.is_empty())
            }>
                <div class="date-picker">
                    <button
                        class="icon-btn"
                        aria-label=move || u.t("Предыдущий день")
                        on:click=move |_| change(|q| q.date = q.date - Days::new(1))
                    >
                        <Icon name="back" />
                    </button>
                    <input
                        type="date"
                        aria-label=move || u.t("Дата")
                        prop:value=move || u.q.get().date.to_string()
                        on:change=move |e| {
                            if let Ok(day) = event_target_value(&e).parse() {
                                let mut q = u.q.get_untracked();
                                q.date = day;
                                u.load(q, None, false)
                            }
                        }
                    />
                    <button
                        class="icon-btn"
                        aria-label=move || u.t("Следующий день")
                        on:click=move |_| change(|q| q.date = q.date + Days::new(1))
                    >
                        <Icon name="next" />
                    </button>
                    <button class="today" on:click=move |_| change(|q| q.date = today())>
                        {move || u.t("Сегодня")}
                    </button>
                </div>
            </Show>
            {move || {
                let b = u.board.get();
                if b.choices.is_empty() {
                    return None;
                }
                let q = u.q.get();
                let mut value = q.filter;
                if value.is_empty() {
                    value = b.selection;
                }
                Some(
                    view! {
                        <select
                            aria-label=move || u.t("Фильтр")
                            on:change=move |e| {
                                let mut q = u.q.get_untracked();
                                q.filter = event_target_value(&e);
                                u.load(q, None, false)
                            }
                        >
                            {(matches!(q.section, Section::Ranking | Section::Food))
                                .then(|| {
                                    view! {
                                        <option selected=value.is_empty() value="">
                                            {u
                                                .t(
                                                    if q.section == Section::Food {
                                                        "Все приёмы пищи"
                                                    } else {
                                                        "Все предметы"
                                                    },
                                                )}
                                        </option>
                                    }
                                })}
                            {b
                                .choices
                                .into_iter()
                                .map(|c| {
                                    view! {
                                        <option selected=c.id == value value=c.id>
                                            {u.t(&c.name)}
                                        </option>
                                    }
                                })
                                .collect_view()}
                        </select>
                    },
                )
            }}
        </div>
        <Show when=move || u.q.get().section == Section::Schedule>
            <div class="week">
                {move || {
                    let q = u.q.get();
                    let monday = q.date - Days::new(q.date.weekday().num_days_from_monday() as u64);
                    ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"]
                        .into_iter()
                        .enumerate()
                        .map(|(i, label)| {
                            let d = monday + Days::new(i as u64);
                            view! {
                                <button
                                    class:chosen=d == q.date
                                    aria-pressed=d == q.date
                                    on:click=move |_| {
                                        let mut q = u.q.get_untracked();
                                        q.date = d;
                                        u.load(q, None, false)
                                    }
                                >
                                    <small>{u.t(label)}</small>
                                    <strong>{d.day()}</strong>
                                </button>
                            }
                        })
                        .collect_view()
                }}
            </div>
        </Show>
    }
}
#[component]
fn App() -> impl IntoView {
    let u = Ui::new();
    u.session(Action::Start);
    let listener = window_event_listener(leptos::ev::keydown, move |e| {
        if e.key() == "Escape" && u.selected.get_untracked().is_some() {
            e.prevent_default();
            u.close_detail();
        }
        if (e.key() == "F5" || (e.ctrl_key() && e.key().eq_ignore_ascii_case("r")))
            && u.account.get_untracked().is_some()
        {
            e.prevent_default();
            u.reload();
        }
    });
    on_cleanup(move || listener.remove());
    let auto = RwSignal::new(false);
    Effect::new(move || {
        if u.account.get().is_some_and(|a| !a.demo) && !auto.get_untracked() {
            auto.set(true);
            u.updates(false)
        }
    });
    Effect::new(move || {
        let lang = u.prefs.get().language;
        if let Some(el) = document().document_element() {
            let _ = el.set_attribute("lang", &lang);
        }
    });
    view! {
        <div
            class="application"
            data-theme=move || u.prefs.get().base_theme().to_string()
            data-style=move || u.prefs.get().style
            style=move || {
                let p = u.prefs.get();
                p.colors()
                    .map(|(accent, bg, panel)| {
                        format!("--accent:{accent};--canvas:{bg};--panel:{panel}")
                    })
                    .unwrap_or_default()
            }
        >
            <Show
                when=move || u.ready.get()
                fallback=move || {
                    view! {
                        <div class="boot">
                            <Brand />
                            <p>{u.t("Загрузка…")}</p>
                        </div>
                    }
                }
            >
                <Show when=move || u.account.get().is_some() fallback=move || view! { <Login u /> }>
                    <Shell u />
                </Show>
            </Show>
        </div>
    }
}
fn main() {
    leptos::mount::mount_to_body(|| view! { <App /> });
}
