use crate::*;

pub fn schedule(raw: &Value, date: &str) -> Board {
    let mut rows = vec![];
    for entry in list(raw, &["activities", "payload", "items", "data", "lessons"]) {
        let nested = ["lessons", "lesson_list", "items"]
            .iter()
            .find_map(|k| entry[k].as_array());
        let entries = nested
            .map(Vec::as_slice)
            .unwrap_or(std::slice::from_ref(entry));
        for item in entries {
            let title = field(
                item,
                &[
                    "/lesson/subject_name",
                    "/lesson/subjectName",
                    "subject_name",
                    "subjectName",
                    "/subject/name",
                ],
            );
            if title.is_empty()
                || matches!(
                    item["type"].as_str(),
                    Some("BREAK" | "BREAK_TIME" | "ORGANIZER")
                )
            {
                continue;
            }
            let time = |keys: &[&str]| {
                let s = field(item, keys);
                s.split('T')
                    .next_back()
                    .unwrap_or(&s)
                    .chars()
                    .take(5)
                    .collect::<String>()
            };
            let begin = time(&["begin_time", "start_time", "startTime", "time_from"]);
            let end = time(&["end_time", "finish_time", "endTime", "time_to"]);
            let room = field(
                item,
                &[
                    "room_number",
                    "/room/number",
                    "/lesson/room_number",
                    "room_name",
                    "room",
                    "/room/name",
                    "/lesson/room_name",
                    "cabinet",
                ],
            );
            let teacher = fullname(item.pointer("/lesson/teacher").unwrap_or(&item["teacher"]));
            let subject = field(
                item,
                &[
                    "/lesson/subject_id",
                    "subject_id",
                    "subjectId",
                    "/subject/id",
                ],
            );
            let mut id = field(item, &["id", "/lesson/id"]);
            if id.is_empty() {
                id = format!("{date}-{begin}-{subject}-{}", rows.len())
            }
            let mut row = Row {
                id,
                title,
                subject,
                leading: begin.clone(),
                subtitle: join(&[end.clone(), room.clone()]),
                kind: "lesson".into(),
                description: field(
                    item,
                    &["/lesson/homework", "homework", "home_task", "description"],
                ),
                entry: field(
                    item,
                    &[
                        "/lesson/homework_entry_student_id",
                        "/lesson/homework_entry_id",
                        "/lesson/homework_id",
                        "homework_entry_student_id",
                        "homework_entry_id",
                        "homework_id",
                    ],
                ),
                due: date.into(),
                ..Default::default()
            };
            row.field("Время", join(&[begin, end]));
            row.field("Кабинет", room);
            row.field("Учитель", teacher);
            if yes(&item["is_cancel"])
                || yes(&item["isCancelled"])
                || yes(&item["lesson"]["is_cancelled"])
            {
                row.badge = "Отменён".into();
            }
            for mark in list(item, &["/lesson/marks", "marks"]) {
                row.children.push(crate::grades::mark(mark, &row.title));
            }
            rows.push(row);
        }
    }
    rows.sort_by_key(|row| row.leading.clone());
    Board {
        rows,
        ..Default::default()
    }
}

fn material_urls(v: &Value, out: &mut Vec<String>) {
    match v {
        Value::String(s) => {
            if let Some(url) = web_url(s) {
                if !out.contains(&url) {
                    out.push(url)
                }
            }
        }
        Value::Array(values) => {
            for value in values {
                material_urls(value, out)
            }
        }
        Value::Object(_) => {
            for k in [
                "url",
                "urls",
                "link",
                "href",
                "address",
                "web_url",
                "webUrl",
                "action_url",
                "actionUrl",
                "target_url",
                "resource_url",
                "action",
                "target",
                "resource",
            ] {
                if let Some(value) = v.get(k) {
                    material_urls(value, out)
                }
            }
        }
        _ => {}
    }
}
pub fn homework(raw: &Value, query: &Query) -> Board {
    let mut rows = vec![];
    let date = query.date.to_string();
    for v in items(raw) {
        let due = iso(&field(
            v,
            &["date_prepared_for", "date_prepared", "deadline", "due_date"],
        ));
        let assigned = iso(&field(
            v,
            &[
                "date_assigned_on",
                "date_assigned",
                "date",
                "lesson_date_time",
            ],
        ));
        if (if query.mode == "assigned" {
            &assigned
        } else {
            &due
        }) != &date
        {
            continue;
        }
        let mut row = Row {
            id: field(
                v,
                &[
                    "homework_entry_student_id",
                    "homeworkEntryStudentId",
                    "homework_id",
                    "id",
                ],
            ),
            title: field(v, &["subject_name", "subjectName"]),
            description: field(v, &["description", "homework", "text"]),
            subject: field(v, &["subject_id", "subjectId"]),
            entry: field(v, &["homework_entry_id", "homeworkEntryId"]),
            kind: field(v, &["type"]).to_uppercase(),
            done: Some(yes(&v["is_done"]) || yes(&v["isDone"])),
            due: due.clone(),
            assigned: assigned.clone(),
            ..Default::default()
        };
        if row.kind.is_empty() {
            row.kind = "OO".into()
        }
        row.field("Задано", assigned);
        row.field("Сдать", due);
        row.subtitle = row.description.clone();
        for m in list(v, &["materials"])
            .iter()
            .chain(list(v, &["attachments"]))
        {
            let mut urls = vec![];
            material_urls(m, &mut urls);
            let title = field(m, &["title", "name", "file_name", "type_name"]);
            let title = if title.is_empty() {
                "Материал".to_string()
            } else {
                title
            };
            let material = field(m, &["uuid", "material_id", "id"]);
            if urls.is_empty() && !material.is_empty() {
                row.links.push(Resource {
                    title,
                    material,
                    url: String::new(),
                })
            } else {
                for url in urls {
                    row.links.push(Resource {
                        title: title.clone(),
                        url,
                        material: material.clone(),
                    })
                }
            }
        }
        rows.push(row);
    }
    Board {
        rows,
        ..Default::default()
    }
}

pub fn match_lesson(lesson: &Row, rows: &[Row]) -> Option<usize> {
    if !lesson.entry.is_empty() {
        if let Some(i) = rows
            .iter()
            .position(|h| h.id == lesson.entry || h.entry == lesson.entry)
        {
            return Some(i);
        }
    }
    let mut matches = rows.iter().enumerate().filter(|(_, h)| {
        (!lesson.subject.is_empty() && lesson.subject == h.subject) || lesson.title == h.title
    });
    let first = matches.next()?;
    let exact = |h: &Row| {
        !lesson.description.trim().is_empty() && lesson.description.trim() == h.description.trim()
    };
    if exact(first.1) {
        return Some(first.0);
    }
    let rest = matches.collect::<Vec<_>>();
    if rest.is_empty() {
        Some(first.0)
    } else {
        rest.into_iter().find(|(_, h)| exact(h)).map(|(i, _)| i)
    }
}
