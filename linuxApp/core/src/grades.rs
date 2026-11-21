use crate::*;
use std::collections::BTreeMap;

pub fn mark(v: &Value, subject: &str) -> Row {
    let title = field(v, &["subject_name", "subjectName"]);
    let date = iso(&field(v, &["created_at", "date"]));
    let mut row = Row {
        id: field(v, &["id", "mark_id"]),
        title: if title.is_empty() {
            subject.into()
        } else {
            title
        },
        leading: field(v, &["value", "mark", "/mark_value/five"]),
        assigned: date.clone(),
        kind: "mark".into(),
        subtitle: field(
            v,
            &["control_form_name", "controlForm", "/control_form/name"],
        ),
        description: field(v, &["comment"]),
        ..Default::default()
    };
    row.field("Дата", date);
    row.field("Вид работы", row.subtitle.clone());
    row.field("Вес", display(Some(number(v, &["weight"]).unwrap_or(1.0))));
    row
}

pub fn marks(raw: &Value) -> Board {
    let mut board = Board::default();
    let mut distribution = BTreeMap::<i32, u32>::new();
    let mut weeks = BTreeMap::<NaiveDate, (f64, u32)>::new();
    let (mut total, mut weighted, mut weights, mut count) = (0.0, 0.0, 0.0, 0u32);
    for v in items(raw) {
        let row = mark(v, "");
        if let Some(n) = number(v, &["value", "mark"]) {
            let weight = number(v, &["weight"]).unwrap_or(1.0);
            total += n;
            weighted += weight * n;
            weights += weight;
            count += 1;
            *distribution.entry(n.round() as i32).or_default() += 1;
            if let Ok(date) = row.assigned.parse::<NaiveDate>() {
                let key = date - Days::new(date.weekday().num_days_from_monday() as u64);
                let entry = weeks.entry(key).or_default();
                entry.0 += n;
                entry.1 += 1;
            }
        }
        board.rows.push(row);
    }
    board.rows.sort_by(|a, b| b.assigned.cmp(&a.assigned));
    board.stats = vec![
        Field::new(
            "Средний балл",
            display((count > 0).then(|| total / count as f64)),
        ),
        Field::new(
            "Средневзвешенный",
            display((weights > 0.0).then(|| weighted / weights)),
        ),
        Field::new("Всего оценок", count),
    ];
    if count > 0 {
        board.chart = (2..=5)
            .map(|i| Field::new(&i.to_string(), distribution.get(&i).unwrap_or(&0)))
            .collect();
        let skip = weeks.len().saturating_sub(10);
        board.trend = weeks
            .into_iter()
            .skip(skip)
            .map(|(week, (sum, n))| {
                Field::new(
                    &week.format("%d.%m").to_string(),
                    display(Some(sum / n as f64)),
                )
            })
            .collect();
    }
    board
}

pub fn subjects(raw: &Value) -> Board {
    let mut rows = vec![];
    for s in items(raw) {
        let mut row = Row {
            id: field(s, &["subject_id", "subjectId", "id"]),
            title: field(s, &["subject_name", "subjectName", "name"]),
            leading: display(number(s, &["average", "/rank/averageMarkFive"])),
            kind: "subject".into(),
            ..Default::default()
        };
        row.field("Средний балл", row.leading.clone());
        row.field("Годовая", field(s, &["year_mark", "yearMark"]));
        row.field("Динамика", field(s, &["dynamic"]));
        for p in list(s, &["periods"]) {
            let title = field(p, &["title", "name"]);
            let value = field(p, &["value", "fixed_value"]);
            if !value.is_empty() {
                row.field(&title, value);
            }
            for m in list(p, &["marks"]) {
                row.children.push(mark(m, &row.title));
            }
        }
        if row.children.is_empty() {
            for m in list(s, &["marks"]) {
                let mut child = mark(m, &row.title);
                if m.is_number() || m.is_string() {
                    child.leading = scalar(m);
                }
                row.children.push(child);
            }
        }
        row.children.sort_by(|a, b| b.assigned.cmp(&a.assigned));
        row.badge = row.children.len().to_string();
        rows.push(row);
    }
    Board {
        rows,
        ..Default::default()
    }
}

pub fn detail(v: &Value) -> Row {
    let mut row = mark(v, "");
    row.field("Учитель", fullname(&v["teacher"]));
    row.field(
        "Тема урока",
        field(v, &["/activity/lesson_topic", "lesson_topic"]),
    );
    let results = &v["class_results"];
    row.field("Учеников в классе", field(results, &["total_students"]));
    for bucket in list(results, &["marks_distributions"]) {
        let grade = field(bucket, &["/mark_value/five", "value"]);
        if !grade.is_empty() {
            row.field(
                &grade,
                format!(
                    "{} % ({})",
                    field(bucket, &["percentage_of_students"]),
                    field(bucket, &["number_of_students"])
                ),
            );
        }
    }
    row
}

pub fn academic_years(raw: &Value) -> (Vec<Choice>, String) {
    let mut choices = vec![];
    let mut current = String::new();
    for y in items(raw) {
        let id = field(y, &["id"]);
        if id.is_empty() {
            continue;
        }
        if yes(&y["current_year"]) {
            current = id.clone();
        }
        choices.push(Choice {
            id,
            name: field(y, &["name"]),
        });
    }
    if current.is_empty() {
        current = choices.last().map(|c| c.id.clone()).unwrap_or_default()
    }
    (choices, current)
}
pub fn finals(raw: &Value) -> Board {
    let mut rows = vec![];
    for v in list(raw, &["subjects"]) {
        let mut row = Row {
            id: field(v, &["id"]),
            title: field(v, &["name"]),
            kind: "final".into(),
            leading: display(number(v, &["year_mark"])),
            ..Default::default()
        };
        for p in list(v, &["periods"]) {
            row.field(
                &field(p, &["name", "ordinal"]),
                display(number(p, &["final_mark", "value"])),
            );
        }
        row.field("Годовая", row.leading.clone());
        row.field("Аттестация", field(v, &["intermediate_attestation_mark"]));
        row.field("Учебные сборы", field(v, &["training_camp_mark"]));
        rows.push(row);
    }
    Board {
        rows,
        ..Default::default()
    }
}

pub fn ranking(
    raw: &Value,
    subjects: &Value,
    me: &str,
    monthly: bool,
    names: &BTreeMap<String, String>,
) -> Board {
    let mut board = Board::default();
    board.choices = items(subjects)
        .iter()
        .map(|s| Choice {
            id: field(s, &["subjectId", "subject_id", "id"]),
            name: field(s, &["subjectName", "subject_name", "name"]),
        })
        .collect();
    let avg = if monthly {
        "averageMarkFive30"
    } else {
        "averageMarkFive"
    };
    let place = if monthly { "rankPlace30" } else { "rankPlace" };
    let mut scores = vec![];
    let (mut my_place, mut my_score) = (String::from("—"), String::from("—"));
    for (i, member) in items(raw).iter().enumerate() {
        let r = &member["rank"];
        let n = number(r, &[avg, "averageMarkFive", "average_mark"])
            .or_else(|| number(member, &["averageMarkFive"]));
        if let Some(n) = n {
            scores.push(n)
        }
        let rank = number(r, &[place, "rankPlace", "place"]).unwrap_or((i + 1) as f64) as i64;
        let id = field(member, &["personId", "person_id"]);
        let name = names
            .get(&id)
            .cloned()
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| fullname(member));
        let previous = member
            .get("previousRank")
            .unwrap_or(&member["previous_rank"]);
        let mut row = Row {
            id: id.clone(),
            title: if name.is_empty() {
                "Ученик".into()
            } else {
                name
            },
            leading: rank.to_string(),
            badge: display(n),
            selected: id == me,
            kind: "rank".into(),
            ..Default::default()
        };
        row.field("Место", rank);
        row.field("Средний балл", row.badge.clone());
        row.field(
            "Статус",
            field(
                r,
                &[
                    if monthly {
                        "rankStatus30"
                    } else {
                        "rankStatus"
                    },
                    "rankStatus",
                ],
            ),
        );
        row.field(
            "Предыдущее место",
            field(previous, &[place, "rankPlace", "place"]),
        );
        if let (Some(now), Some(old)) = (
            n,
            number(previous, &[avg, "averageMarkFive", "average_mark"]),
        ) {
            row.subtitle = format!("{:+.2}", now - old);
        }
        if id == me {
            my_place = rank.to_string();
            my_score = row.badge.clone();
        }
        board.rows.push(row);
    }
    board
        .rows
        .sort_by_key(|r| r.leading.parse::<i64>().unwrap_or(i64::MAX));
    scores.sort_by(f64::total_cmp);
    let n = scores.len();
    let median = (n > 0).then(|| (scores[(n - 1) / 2] + scores[n / 2]) / 2.0);
    board.stats = vec![
        Field::new("Моё место", my_place),
        Field::new("Мой балл", my_score),
        Field::new(
            "Среднее по классу",
            display((n > 0).then(|| scores.iter().sum::<f64>() / n as f64)),
        ),
        Field::new("Медиана", display(median)),
    ];
    board
}
