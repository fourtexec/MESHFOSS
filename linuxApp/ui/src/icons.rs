use leptos::prelude::*;

#[component]
pub fn Icon(#[prop(into)] name: String) -> impl IntoView {
    let d = match name.as_str() {
        "Расписание" => "M4 6h16v15H4z M4 10h16 M8 3v5 M16 3v5 M8 14h2 M14 14h2 M8 17h2",
        "Домашние задания" => "M6 3h12v18H6z M9 8h6 M9 12h6 M9 16h3",
        "Оценки" => "M3 20h18 M6 16v-5 M12 16V4 M18 16V8",
        "Предметы" => "M3 5q5-2 9 1 4-3 9-1v14q-5-2-9 1-4-3-9-1z M12 6v14",
        "Рейтинг" => "M3 20v-7h6v7 M9 20V5h6v15 M15 20V9h6v11Z",
        "Питание" => "M5 3v6q0 4 3 4t3-4V3 M8 3v18 M18 21v-7h-3q0-8 3-11z",
        "Москвёнок" | "qr" => {
            "M3 3h6v6H3z M15 3h6v6h-6z M3 15h6v6H3z M13 13h4v4h4v4h-5 M12 18v3 M20 12h1"
        }
        "Профиль" => "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-2a8 8 0 0 1 16 0v2",
        "Настройки" => {
            "M3 6h8m4 0h6 M3 12h2m4 0h12 M3 18h10m4 0h4 M11 3v6 M5 9v6 M13 15v6"
        }
        "refresh" => "M20 10a8 8 0 1 0-1 8 M20 4v6h-6",
        "next" => "m9 5 7 7-7 7",
        "back" => "m15 5-7 7 7 7",
        "close" => "m6 6 12 12 M18 6 6 18",
        "check" => "m4 12 5 5L20 6",
        "link" => "M14 4h6v6 M20 4l-9 9 M10 5H5v15h15v-6",
        "clock" => "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M12 6v6l4 2",
        "copy" => "M8 8h12v13H8z M16 5V3H4v13h1",
        "book" => "M4 3h15v17H6a2 2 0 0 1-2-2z M8 3v13 M4 17h15 M11 7h5 M11 10h3",
        "logout" => "M10 4H4v16h6 M9 12h12 M17 8l4 4-4 4",
        _ => "M4 12h16 m-6-6 6 6-6 6",
    };
    view! {
        <svg
            class="icon"
            aria-hidden="true"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
        >
            <path d=d />
        </svg>
    }
}
