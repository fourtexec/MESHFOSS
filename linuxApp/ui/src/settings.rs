use crate::{icons::Icon, state::Ui};
use leptos::prelude::*;
use mesh_core::{Action, Preferences, Start};
use serde_json::Value;
use wasm_bindgen_futures::spawn_local;

#[component]
pub fn Language(u: Ui) -> impl IntoView {
    view! {
        <select
            aria-label=move || u.t("Язык")
            class="language"
            on:change=move |e| {
                let mut p = u.prefs.get_untracked();
                p.language = event_target_value(&e);
                u.save(p)
            }
        >
            {[
                ("ru", "Русский"),
                ("en", "English"),
                ("hu", "Magyar"),
                ("es", "Español"),
                ("fr", "Français"),
            ]
                .into_iter()
                .map(|(code, name)| {
                    view! {
                        <option selected=move || u.prefs.get().language == code value=code>
                            {name}
                        </option>
                    }
                })
                .collect_view()}
        </select>
    }
}
#[component]
pub fn Settings(u: Ui) -> impl IntoView {
    let log = RwSignal::new(None::<String>);
    let change = move |func: fn(&mut Preferences, String), value: String| {
        let mut p = u.prefs.get_untracked();
        func(&mut p, value);
        u.save(p)
    };
    view! {
        <div class="settings-grid">
            <section class="settings-card">
                <h2>{move || u.t("Оформление")}</h2>
                <div class="setting">
                    <label for="theme">{move || u.t("Тема")}</label>
                    <select
                        id="theme"
                        on:change=move |e| change(|p, v| p.theme = v, event_target_value(&e))
                    >
                        {[
                            ("system", "Системная"),
                            ("light", "Светлая"),
                            ("dark", "Тёмная"),
                            ("custom", "Своя палитра"),
                        ]
                            .into_iter()
                            .chain(mesh_core::preferences::PALETTES.iter().map(|p| (p.0, p.1)))
                            .map(|(v, n)| {
                                view! {
                                    <option value=v selected=move || u.prefs.get().theme == v>
                                        {move || u.t(n)}
                                    </option>
                                }
                            })
                            .collect_view()}
                    </select>
                </div>
                <Show when=move || u.prefs.get().theme == "custom">
                    <div class="swatches">
                        {[
                            ("accent", "Акцент"),
                            ("background", "Фон"),
                            ("surface", "Панели"),
                        ]
                            .into_iter()
                            .map(|(which, n)| {
                                view! {
                                    <label>
                                        {move || u.t(n)}
                                        <input
                                            type="color"
                                            prop:value=move || {
                                                let p = u.prefs.get();
                                                match which {
                                                    "accent" => p.accent,
                                                    "surface" => p.surface,
                                                    _ => p.background,
                                                }
                                            }
                                            on:change=move |e| {
                                                let mut p = u.prefs.get_untracked();
                                                let value = event_target_value(&e);
                                                match which {
                                                    "accent" => p.accent = value,
                                                    "surface" => p.surface = value,
                                                    _ => p.background = value,
                                                };
                                                u.save(p)
                                            }
                                        />
                                    </label>
                                }
                            })
                            .collect_view()}
                    </div>
                </Show>
                <div class="setting">
                    <label for="style">{move || u.t("Стиль")}</label>
                    <select
                        id="style"
                        on:change=move |e| change(|p, v| p.style = v, event_target_value(&e))
                    >
                        {[
                            ("plain", "Обычный"),
                            ("glass", "Стекло"),
                            ("liquid", "Жидкое стекло"),
                        ]
                            .into_iter()
                            .map(|(v, n)| {
                                view! {
                                    <option value=v selected=move || u.prefs.get().style == v>
                                        {move || u.t(n)}
                                    </option>
                                }
                            })
                            .collect_view()}
                    </select>
                </div>
                <div class="setting">
                    <span>{move || u.t("Язык")}</span>
                    <Language u />
                </div>
            </section>
            <section class="settings-card">
                <h2>{move || u.t("Приложение")}</h2>
                <div class="setting">
                    <label for="channel">{move || u.t("Канал обновлений")}</label>
                    <select
                        id="channel"
                        on:change=move |e| change(|p, v| p.channel = v, event_target_value(&e))
                    >
                        {[
                            ("stable", "Стабильные"),
                            ("preview", "Предварительные"),
                        ]
                            .into_iter()
                            .map(|(v, n)| {
                                view! {
                                    <option value=v selected=move || u.prefs.get().channel == v>
                                        {move || u.t(n)}
                                    </option>
                                }
                            })
                            .collect_view()}
                    </select>
                </div>
                <button
                    class="secondary"
                    disabled=move || u.working.get()
                    on:click=move |_| u.updates(true)
                >
                    <Icon name="refresh" />
                    {move || u.t("Проверить обновления")}
                </button>
            </section>
            <section class="settings-card">
                <h2>{move || u.t("Аккаунт")}</h2>
                <p>
                    {move || {
                        u
                            .t(
                                if u.account.get().is_some_and(|a| a.demo) {
                                    "Демоверсия"
                                } else if u.account.get().is_some_and(|a| a.remembered) {
                                    "Сессия сохранена в связке ключей"
                                } else {
                                    "Сессия до закрытия приложения"
                                },
                            )
                    }}
                </p>
                {move || {
                    u
                        .account
                        .get()
                        .filter(|a| !a.token_info.is_empty())
                        .map(|a| {
                            view! {
                                <dl>
                                    {a
                                        .token_info
                                        .into_iter()
                                        .map(|f| {
                                            view! {
                                                <div>
                                                    <dt>{u.t(&f.label)}</dt>
                                                    <dd>{f.value}</dd>
                                                </div>
                                            }
                                        })
                                        .collect_view()}
                                </dl>
                            }
                        })
                }}
                <Show when=move || u.account.get().is_some_and(|a| a.remembered && !a.demo)>
                    <label class="remember background-setting">
                        <input
                            type="checkbox"
                            prop:checked=move || u.prefs.get().renew_when_closed
                            disabled=move || u.working.get()
                            on:change=move |e| {
                                let mut p = u.prefs.get_untracked();
                                p.renew_when_closed = event_target_checked(&e);
                                u.save(p)
                            }
                        />
                        {move || {
                            u
                                .t(
                                    "Продлевать сессию при закрытом окне",
                                )
                        }}
                    </label>
                </Show>
                <button
                    class="secondary"
                    disabled=move || u.working.get()
                    on:click=move |_| {
                        u
                            .action(
                                Action::Refresh,
                                move |s: Start| {
                                    u.accept(s);
                                    u.info.set("Сессия обновлена".into())
                                },
                            )
                    }
                >
                    {move || u.t("Обновить сессию")}
                </button>
                <button
                    class="text-btn"
                    disabled=move || u.working.get()
                    on:click=move |_| u.session(Action::Logout)
                >
                    <Icon name="logout" />
                    {move || u.t("Выйти")}
                </button>
            </section>
            <section class="settings-card">
                <h2>{move || u.t("Журнал Москвёнка")}</h2>
                <button
                    class="secondary"
                    on:click=move |_| {
                        u.action(Action::Log { clear: false }, move |s: String| log.set(Some(s)))
                    }
                >
                    {move || u.t("Показать журнал")}
                </button>
                {move || {
                    log
                        .get()
                        .map(|s| {
                            let text = s.clone();
                            view! {
                                <pre>
                                    {if s.is_empty() { u.t("Журнал пуст") } else { s }}
                                </pre>
                                <button
                                    class="text-btn"
                                    on:click=move |_| {
                                        let text = text.clone();
                                        spawn_local(async move {
                                            if wasm_bindgen_futures::JsFuture::from(
                                                    window().navigator().clipboard().write_text(&text),
                                                )
                                                .await
                                                .is_ok()
                                            {
                                                u.info.set("Скопировано".into())
                                            }
                                        })
                                    }
                                >
                                    {u.t("Скопировать")}
                                </button>
                                <button
                                    class="text-btn"
                                    on:click=move |_| {
                                        u
                                            .action(
                                                Action::Log { clear: true },
                                                move |_: Value| log.set(Some(String::new())),
                                            )
                                    }
                                >
                                    {u.t("Очистить журнал")}
                                </button>
                            }
                        })
                }}
            </section>
        </div>
    }
}
