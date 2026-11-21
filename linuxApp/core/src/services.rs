use crate::*;
pub fn money(v: &Value, keys: &[&str]) -> String {
    number(v, keys)
        .map(|n| format!("{:.2} ₽", n / 100.).replace('.', ","))
        .unwrap_or_else(|| "—".into())
}
pub fn food(state: &Value, balance: &Value, menu: &Value, orders: &Value, query: &Query) -> Board {
    let mut board = Board::default();
    for account in items(balance) {
        board
            .stats
            .push(Field::new("Баланс", money(account, &["balance"])));
        if number(
            account,
            &["/expenseConstraints/expenseDayLimit", "expenseDayLimit"],
        )
        .is_some()
        {
            board.stats.push(Field::new(
                "Дневной лимит",
                money(
                    account,
                    &["/expenseConstraints/expenseDayLimit", "expenseDayLimit"],
                ),
            ));
        }
    }
    let state = state
        .get("allowedFoodState")
        .or_else(|| state.get("payload"))
        .unwrap_or(state);
    if query.mode == "orders" {
        for o in list(orders, &["orders", "items", "payload"]) {
            let mut row = Row {
                id: field(o, &["id", "orderId"]),
                title: field(o, &["name", "complexName", "/complex/name"]),
                subtitle: field(o, &["onDate", "date", "orderDate"]),
                badge: field(o, &["status", "state"]),
                ..Default::default()
            };
            if row.title.is_empty() {
                row.title = "Предзаказ".into()
            }
            row.field("Номер", row.id.clone());
            row.field(
                "Стоимость",
                money(
                    o,
                    &[
                        "price",
                        "amount",
                        "totalAmount",
                        "sum",
                        "cost",
                        "totalPrice",
                    ],
                ),
            );
            board.rows.push(row);
        }
    } else {
        for day in items(menu) {
            if day["onDate"]
                .as_str()
                .is_some_and(|d| iso(d) != query.date.to_string())
            {
                continue;
            }
            for meal in list(day, &["items", "complexes"]) {
                let types = list(meal, &["paymentTypes"]);
                let free = query.mode == "free";
                if !(types.is_empty() && !free
                    || types
                        .iter()
                        .any(|t| t.as_i64() == Some(if free { 0 } else { 1 })))
                {
                    continue;
                }
                let kind = match meal["kind"].as_i64() {
                    Some(0) => "Завтрак",
                    Some(1) => "Второй завтрак",
                    Some(2) => "Обед",
                    Some(3) => "Полдник",
                    Some(4) => "Ужин",
                    _ => "Питание",
                };
                let id = field(meal, &["kind"]);
                if !id.is_empty() && !board.choices.iter().any(|c| c.id == id) {
                    board.choices.push(Choice {
                        id: id.clone(),
                        name: kind.into(),
                    });
                }
                if !query.filter.is_empty() && query.filter != id {
                    continue;
                }
                let mut row = Row {
                    id: field(meal, &["id"]),
                    title: field(meal, &["name"]),
                    subtitle: kind.into(),
                    badge: if free {
                        "Льготное".into()
                    } else {
                        money(meal, &["price"])
                    },
                    ..Default::default()
                };
                if row.title.is_empty() {
                    row.title = kind.into()
                }
                row.field(
                    "Столовая",
                    field(day, &["/address/organizationName", "/address/text"]),
                );
                for item in list(meal, &["complexItems", "dishes"]) {
                    let d = item.get("dish").unwrap_or(item);
                    let mut dish = Row {
                        title: field(d, &["name"]),
                        subtitle: field(d, &["weight"]),
                        description: field(d, &["ingredients"]),
                        ..Default::default()
                    };
                    for (key, label) in [
                        ("calories", "Ккал"),
                        ("protein", "Белки"),
                        ("fat", "Жиры"),
                        ("carbohydrates", "Углеводы"),
                    ] {
                        dish.field(label, field(d, &[key]));
                    }
                    row.children.push(dish)
                }
                board.rows.push(row);
            }
        }
    }
    if state.get("preorderAllowed").is_some() {
        board.stats.push(Field::new(
            "Предзаказ",
            if yes(&state["preorderAllowed"]) {
                "Доступен"
            } else {
                "Недоступен"
            },
        ));
    }
    if state.get("discountedMeals").is_some() {
        board.stats.push(Field::new(
            "Льготное питание",
            if yes(&state["discountedMeals"]) {
                "Доступно"
            } else {
                "Недоступно"
            },
        ));
    }
    let days = [
        ("monday", "Пн"),
        ("tuesday", "Вт"),
        ("wednesday", "Ср"),
        ("thursday", "Чт"),
        ("friday", "Пт"),
        ("saturday", "Сб"),
        ("sunday", "Вс"),
    ];
    if state["feedingWeek"].is_object() {
        board.feeding_days = days
            .iter()
            .filter(|(key, _)| yes(&state["feedingWeek"][key]))
            .map(|(_, label)| label.to_string())
            .collect();
    }
    for a in list(state, &["addresses"]) {
        let addr = join(&[
            field(a, &["/address/organizationName"]),
            field(a, &["/address/text"]),
        ]);
        if !addr.is_empty() {
            board.stats.push(Field::new("Столовая", addr));
        }
    }
    board
}

pub fn profile_board(a: &Account) -> Board {
    let mut rows = vec![];
    for s in &a.students {
        let mut row = Row {
            id: s.id.clone(),
            title: s.name.clone(),
            subtitle: s.school.clone(),
            badge: s.class_name.clone(),
            kind: "student".into(),
            selected: a.active == s.id,
            ..Default::default()
        };
        for (k, v) in [
            ("Школа", &s.school),
            ("Класс", &s.class_name),
            ("Электронная почта", &s.email),
            ("Телефон", &s.phone),
        ] {
            row.field(k, v);
        }
        rows.push(row);
    }
    if !a.parent.is_empty() {
        let mut row = Row {
            title: a.parent.clone(),
            subtitle: "Родитель".into(),
            ..Default::default()
        };
        row.field("Электронная почта", &a.email);
        row.field("Телефон", &a.phone);
        rows.push(row);
    }
    Board {
        rows,
        ..Default::default()
    }
}
