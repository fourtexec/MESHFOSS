use crate::{icons::Icon, state::Ui, PortalStatus};
use gloo_timers::future::TimeoutFuture;
use leptos::prelude::*;
use mesh_core::Action;
use mesh_core::*;
use serde_json::Value;
use wasm_bindgen_futures::{spawn_local, JsFuture};

#[component]
pub fn Dashboard(u: Ui) -> impl IntoView {
    view! {
        {move || {
            let b = u.board.get();
            (!b.stats.is_empty())
                .then(|| {
                    view! {
                        <div class="stats">
                            {b
                                .stats
                                .into_iter()
                                .map(|f| {
                                    view! {
                                        <div>
                                            <small>{u.t(&f.label)}</small>
                                            <strong>{u.t(&f.value)}</strong>
                                        </div>
                                    }
                                })
                                .collect_view()}
                        </div>
                    }
                })
        }}
        {move || {
            let days = u.board.get().feeding_days;
            (!days.is_empty())
                .then(|| {
                    view! {
                        <p class="feeding-days">
                            {u.t("Дни питания")}" · "
                            {days.iter().map(|d| u.t(d)).collect::<Vec<_>>().join(" / ")}
                        </p>
                    }
                })
        }}
        <Show when=move || !u.board.get().notice.is_empty()>
            <p role="status">{move || u.t(&u.board.get().notice)}</p>
        </Show>
        <Show when=move || u.busy.get()>
            <div class="progress" role="status">
                {move || u.t("Загрузка…")}
            </div>
        </Show>
        <Show
            when=move || u.q.get().section != Section::Card
            fallback=move || view! { <CardPanel u /> }
        >
            <div class="split" class:inspecting=move || u.selected.get().is_some()>
                <div class="list-col">
                    <Show
                        when=move || !u.board.get().rows.is_empty()
                        fallback=move || {
                            view! {
                                <Show when=move || !u.busy.get()>
                                    <div class="empty">
                                        <Icon name="book" />
                                        <h3>
                                            {move || {
                                                u
                                                    .t(
                                                        if !u.error.get().is_empty() {
                                                            "Не удалось загрузить данные"
                                                        } else {
                                                            match u.q.get().section {
                                                                Section::Schedule => {
                                                                    "В этот день уроков нет"
                                                                }
                                                                Section::Homework => {
                                                                    "На эту дату заданий нет"
                                                                }
                                                                Section::Grades => {
                                                                    "За этот период оценок нет"
                                                                }
                                                                _ => "Пока нет данных",
                                                            }
                                                        },
                                                    )
                                            }}
                                        </h3>
                                        <button class="secondary" on:click=move |_| u.reload()>
                                            {move || u.t("Обновить")}
                                        </button>
                                    </div>
                                </Show>
                            }
                        }
                    >
                        <div class="record-list" role="list">
                            <div class="list-caption">
                                <span>
                                    {move || {
                                        u
                                            .t(
                                                match u.q.get().section {
                                                    Section::Schedule => "Уроки",
                                                    Section::Homework => "Задания",
                                                    Section::Ranking => "Рейтинг класса",
                                                    Section::Food => "Питание",
                                                    s => s.label(),
                                                },
                                            )
                                    }}
                                </span>
                                <span>{move || u.board.get().rows.len()}</span>
                            </div>
                            {move || {
                                u
                                    .board
                                    .get()
                                    .rows
                                    .into_iter()
                                    .map(|row| {
                                        let id = row.id.clone();
                                        let focus_id = id.clone();
                                        let chosen = row.clone();
                                        let show = row.clone();
                                        view! {
                                            <div role="listitem">
                                                <button
                                                    id=format!("row-{id}")
                                                    class="record"
                                                    class:picked=move || {
                                                        u
                                                            .selected
                                                            .get()
                                                            .is_some_and(|r| r.id == focus_id && r.title == show.title)
                                                    }
                                                    class:mine=row.selected
                                                    on:click=move |_| u.select(chosen.clone(), false)
                                                >
                                                    <span
                                                        class="row-lead"
                                                        class:hidden=row.leading.is_empty() && row.done.is_none()
                                                        class:time=row.kind == "lesson"
                                                        class:graded=row.kind == "mark"
                                                        class:checked=row.done == Some(true)
                                                    >
                                                        {if let Some(done) = row.done {
                                                            if done { "✓".into() } else { "○".into() }
                                                        } else {
                                                            row.leading.clone()
                                                        }}
                                                    </span>
                                                    <span class="row-main">
                                                        <b>{u.t(&row.title)}</b>
                                                        <small>
                                                            {if row.kind == "mark" {
                                                                join(&[row.assigned, row.subtitle])
                                                            } else {
                                                                u.t(&row.subtitle)
                                                            }}
                                                        </small>
                                                    </span>
                                                    {(!row.badge.is_empty())
                                                        .then(|| {
                                                            view! { <span class="badge">{u.t(&row.badge)}</span> }
                                                        })}
                                                    <span class="row-arrow">
                                                        <Icon name="next" />
                                                    </span>
                                                </button>
                                            </div>
                                        }
                                    })
                                    .collect_view()
                            }}
                        </div>
                    </Show>
                    <Show when=move || {
                        u.q.get().section == Section::Grades && u.q.get().mode.is_empty()
                            && u.q.get().days < 366 && !u.busy.get()
                    }>
                        <button
                            class="secondary older"
                            on:click=move |_| {
                                let mut q = u.q.get_untracked();
                                q.days = (q.days + 30).min(366);
                                u.load(q, None, true)
                            }
                        >
                            {move || u.t("Загрузить более ранние оценки")}
                        </button>
                    </Show>
                    <Charts u />
                </div>
                {move || u.selected.get().map(|row| view! { <Detail u row /> })}
            </div>
        </Show>
    }
}
#[component]
fn Charts(u: Ui) -> impl IntoView {
    view! {
        <div class="charts">
            {move || {
                let board = u.board.get();
                [
                    ("Распределение оценок", board.chart),
                    ("Динамика по неделям", board.trend),
                ]
                    .into_iter()
                    .filter(|(_, v)| !v.is_empty())
                    .map(|(name, values)| {
                        let max = if name == "Динамика по неделям" {
                            5.0
                        } else {
                            values
                                .iter()
                                .filter_map(|v| v.value.parse::<f64>().ok())
                                .fold(1.0, f64::max)
                        };
                        view! {
                            <section>
                                <h3>{u.t(name)}</h3>
                                <div class="bars">
                                    {values
                                        .into_iter()
                                        .map(|v| {
                                            let height = v
                                                .value
                                                .replace(',', ".")
                                                .parse::<f64>()
                                                .unwrap_or(0.) * 100. / max;
                                            let tooltip = format!("{} · {}", v.label, v.value);
                                            view! {
                                                <div class="bar" title=tooltip>
                                                    <b>{v.value}</b>
                                                    <div class="shaft">
                                                        <i style=format!("height:{}%", height.clamp(0., 100.))></i>
                                                    </div>
                                                    <small>{v.label}</small>
                                                </div>
                                            }
                                        })
                                        .collect_view()}
                                </div>
                            </section>
                        }
                    })
                    .collect_view()
            }}
        </div>
    }
}
#[component]
fn Detail(u: Ui, row: Row) -> impl IntoView {
    let missing_id = row.id.is_empty();
    let toggle = row.clone();
    let lesson = row.clone();
    let subject = row.title.clone();
    let profile_id = row.id.clone();
    let value = row.description.clone();
    view! {
        <aside class="detail" aria-label=move || u.t("Подробности")>
            <div class="detail-top">
                <span>{u.t("Подробности")}</span>
                <button
                    class="icon-btn"
                    aria-label=move || u.t("Назад")
                    on:click=move |_| u.close_detail()
                >
                    <Icon name="close" />
                </button>
            </div>
            <h2>{u.t(&row.title)}</h2>
            {(!row.leading.is_empty())
                .then(|| view! { <div class="detail-lead">{row.leading.clone()}</div> })}
            <Fields u fields=row.fields />
            {(!row.description.is_empty())
                .then(|| {
                    view! {
                        <div class="description">
                            <p>{row.description.clone()}</p>
                            {(row.done.is_some() || row.kind == "lesson")
                                .then(|| {
                                    view! {
                                        <button
                                            class="text-btn"
                                            on:click=move |_| {
                                                let text = value.clone();
                                                spawn_local(async move {
                                                    let result = JsFuture::from(
                                                            window().navigator().clipboard().write_text(&text),
                                                        )
                                                        .await;
                                                    if result.is_ok() {
                                                        u.info.set("Скопировано".into())
                                                    } else {
                                                        u.error
                                                            .set("Не удалось скопировать".into())
                                                    }
                                                })
                                            }
                                        >
                                            <Icon name="copy" />
                                            {u.t("Скопировать")}
                                        </button>
                                    }
                                })}
                        </div>
                    }
                })}
            {row
                .done
                .map(|done| {
                    view! {
                        <button
                            class="primary wide"
                            disabled=move || u.working.get() || missing_id
                            on:click=move |_| u.toggle(toggle.clone())
                        >
                            <Icon name="check" />
                            {u
                                .t(
                                    if done {
                                        "Снять отметку"
                                    } else {
                                        "Отметить выполненным"
                                    },
                                )}
                        </button>
                    }
                })}
            {(row.kind == "lesson")
                .then(|| {
                    view! {
                        <button
                            class="secondary wide"
                            on:click=move |_| {
                                let mut q = u.q.get_untracked();
                                q.section = Section::Homework;
                                q.mode.clear();
                                q.filter.clear();
                                u.load(q, Some(lesson.clone()), false);
                            }
                        >
                            {u.t("Домашнее задание")}
                            <Icon name="next" />
                        </button>
                    }
                })}
            {(row.kind == "student" && !row.selected)
                .then(|| {
                    view! {
                        <button
                            class="secondary"
                            on:click=move |_| {
                                u
                                    .session(Action::SelectStudent {
                                        id: profile_id.clone(),
                                    })
                            }
                        >
                            {u.t("Выбрать ученика")}
                        </button>
                    }
                })}
            {row
                .links
                .into_iter()
                .map(|resource| {
                    let link = resource.clone();
                    let entry = row.entry.clone();
                    view! {
                        <button
                            class="resource"
                            disabled=move || u.working.get()
                            on:click=move |_| {
                                u
                                    .action(
                                        Action::Material {
                                            entry: entry.clone(),
                                            resource: link.clone(),
                                        },
                                        |_: Value| {},
                                    )
                            }
                        >
                            <Icon name="link" />
                            {resource.title}
                        </button>
                    }
                })
                .collect_view()}
            {(!row.children.is_empty())
                .then(|| {
                    view! {
                        <div class="child-list">
                            {row
                                .children
                                .into_iter()
                                .map(|mut child| {
                                    if child.title.is_empty() {
                                        child.title = subject.clone();
                                    }
                                    let button = child.clone();
                                    view! {
                                        <button on:click=move |_| u.select(button.clone(), true)>
                                            <b>
                                                {if child.leading.is_empty() {
                                                    child.title
                                                } else {
                                                    child.leading
                                                }}
                                            </b>
                                            <span>{join(&[child.assigned, child.subtitle])}</span>
                                            <Icon name="next" />
                                        </button>
                                    }
                                })
                                .collect_view()}
                        </div>
                    }
                })}
        </aside>
    }
}
#[component]
fn Fields(u: Ui, fields: Vec<Field>) -> impl IntoView {
    view! {
        <dl>
            {fields
                .into_iter()
                .map(|f| {
                    view! {
                        <div>
                            <dt>{u.t(&f.label)}</dt>
                            <dd>
                                {if let Ok(day) = f.value.parse::<chrono::NaiveDate>() {
                                    crate::date_name(day, &u.prefs.get().language, false)
                                } else {
                                    u.t(&f.value)
                                }}
                            </dd>
                        </div>
                    }
                })
                .collect_view()}
        </dl>
    }
}
#[component]
fn CardPanel(u: Ui) -> impl IntoView {
    let now = RwSignal::new(js_sys::Date::now() as i64);
    let timer =
        gloo_timers::callback::Interval::new(1000, move || now.set(js_sys::Date::now() as i64));
    let timer = leptos::prelude::StoredValue::new_local(Some(timer));
    on_cleanup(move || {
        timer.update_value(|t| {
            t.take();
        })
    });
    let polls = RwSignal::new(0u8);
    Effect::new(move || {
        if u.board.get().card.is_some_and(|c| c.state == "processing") && polls.get_untracked() < 8
        {
            polls.update(|p| *p += 1);
            let ticket = u.ticket.get_untracked();
            spawn_local(async move {
                TimeoutFuture::new(3000).await;
                if u.ticket.get_untracked() == ticket {
                    u.card_action("load")
                }
            })
        }
    });
    view! {
        <Show when=move || u.portal.get().is_empty() fallback=move || view! { <PortalStatus u /> }>
            {move || {
                u
                    .board
                    .get()
                    .card
                    .map(|c| {
                        let expires = c.expires;
                        let state = c.state.clone();
                        let action = if state == "disabled" {
                            "enable"
                        } else if state == "ready" {
                            "refresh"
                        } else {
                            "load"
                        };
                        view! {
                            <section class="school-card">
                                <div class="card-head">
                                    <Icon name="Москвёнок" />
                                    <h2>{u.t("Карта школьника")}</h2>
                                </div>
                                {(!c.image.is_empty())
                                    .then(|| {
                                        view! {
                                            <Show
                                                when=move || expires == 0 || now.get() < expires
                                                fallback=move || {
                                                    view! {
                                                        <p>
                                                            {u.t("Срок действия QR-кода истёк")}
                                                        </p>
                                                    }
                                                }
                                            >
                                                <img
                                                    class="pass-qr"
                                                    src=c.image.clone()
                                                    alt=u.t("QR-код карты")
                                                />
                                            </Show>
                                        }
                                    })}
                                {(expires > 0)
                                    .then(|| {
                                        view! {
                                            <p>
                                                {move || {
                                                    let seconds = ((expires - now.get()) / 1000).max(0);
                                                    format!(
                                                        "{} {:02}:{:02}",
                                                        u.t("Осталось"),
                                                        seconds / 60,
                                                        seconds % 60,
                                                    )
                                                }}
                                            </p>
                                        }
                                    })}
                                <p>
                                    {u
                                        .t(
                                            match state.as_str() {
                                                "demo" => "Демонстрационная карта",
                                                "login" => {
                                                    "Для карты нужен отдельный вход на mos.ru"
                                                }
                                                "disabled" => "Карта ещё не подключена",
                                                "processing" => "Документ оформляется",
                                                "unavailable" => {
                                                    "Карта ученика недоступна"
                                                }
                                                _ => "",
                                            },
                                        )}
                                </p>
                                <Fields u fields=c.fields />
                                {if state == "login" {
                                    view! {
                                        <button
                                            class="primary"
                                            on:click=move |_| u.start_portal("card")
                                        >
                                            {u.t("Войти через mos.ru")}
                                        </button>
                                    }
                                        .into_any()
                                } else if state == "demo" {
                                    ().into_any()
                                } else {
                                    view! {
                                        <button
                                            class="secondary"
                                            disabled=move || u.working.get()
                                            on:click=move |_| {
                                                polls.set(0);
                                                u.card_action(action)
                                            }
                                        >
                                            {u
                                                .t(
                                                    if action == "enable" {
                                                        "Подключить карту"
                                                    } else {
                                                        "Обновить"
                                                    },
                                                )}
                                        </button>
                                    }
                                        .into_any()
                                }}
                            </section>
                        }
                    })
            }}
        </Show>
    }
}
