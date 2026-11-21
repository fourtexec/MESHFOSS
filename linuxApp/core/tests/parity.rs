use mesh_core::*;
use serde_json::json;
use std::collections::BTreeMap;

#[test]
fn weighted_grades_match_android_fixture() {
    let data = json!([
        {"value":5,"weight":1,"subject_id":1,"subject_name":"Алгебра","created_at":"2026-10-01"},
        {"value":4,"weight":3,"subject_id":1,"subject_name":"Алгебра","created_at":"2026-10-02"},
        {"value":3,"weight":1,"subject_id":2,"subject_name":"Физика","created_at":"2026-10-03"}
    ]);
    let board = grades::marks(&data);
    assert_eq!(board.stats[0].value, "4");
    assert_eq!(board.stats[1].value, "4");
    assert_eq!(board.rows[0].title, "Физика");
    assert_eq!(
        board
            .chart
            .iter()
            .map(|v| v.value.as_str())
            .collect::<Vec<_>>(),
        ["0", "1", "1", "1"]
    );
    let nonuniform = grades::marks(&json!([{"value":5,"weight":1},{"value":"3","weight":3}]));
    assert_eq!(nonuniform.stats[1].value, "3,5");
}

#[test]
fn ranking_selects_monthly_values_and_uses_contest_places() {
    let rank = json!([
        {"personId":"b","rank":{"averageMarkFive":4.1,"averageMarkFive30":4.9,"rankPlace":3,"rankPlace30":1}},
        {"person_id":"a","rank":{"averageMarkFive":4.9,"averageMarkFive30":4.3,"rankPlace":1,"rankPlace30":3}},
        {"personId":"c","rank":{"averageMarkFive30":4.9,"rankPlace30":1}}
    ]);
    let board = grades::ranking(&rank, &json!([]), "a", true, &BTreeMap::new());
    assert!(board.rows[2].selected);
    assert_eq!(board.stats[0].value, "3");
    assert_eq!(board.stats[1].value, "4,3");
    assert_eq!(board.stats[2].value, "4,7");
    assert_eq!(board.stats[3].value, "4,9");
}

#[test]
fn lessons_open_the_exact_homework_and_only_web_materials() {
    let date = "2026-10-12";
    let schedule = diary::schedule(
        &json!({"activities":[{"type":"BREAK","begin_time":"08:00"},
        {"id":1,"lesson":{"subject_name":"Алгебра","homework_entry_id":32},"begin_time":"10:40:00","room":{"number":"19"}}]}),
        date,
    );
    assert_eq!(schedule.rows.len(), 1);
    assert_eq!(schedule.rows[0].leading, "10:40");
    let task = |entry: i64| {
        json!({"homework_entry_student_id":entry+100,"homework_entry_id":entry,"subject_name":"Алгебра",
        "date_prepared_for":date,"date_assigned_on":"2026-10-10","materials":[{"title":"Файл","urls":[{"url":"/file/2"},"file:///etc/hosts"]}]})
    };
    let mut q = Query::new(Section::Schedule, date.parse().unwrap());
    q.section = Section::Homework;
    let board = diary::homework(&json!({"items":[task(31),task(32)]}), &q);
    assert_eq!(diary::match_lesson(&schedule.rows[0], &board.rows), Some(1));
    assert_eq!(board.rows[1].links.len(), 1);
    assert_eq!(board.rows[1].links[0].url, "https://school.mos.ru/file/2");
    q.mode = "assigned".into();
    assert!(diary::homework(&json!([task(31)]), &q).rows.is_empty());
}

#[test]
fn balances_use_kopecks_and_payment_modes_overlap() {
    let menu = json!([{"items":[{"id":"both","paymentTypes":[1,0],"price":19358},{"id":"paid","paymentTypes":[1],"price":100}]}]);
    let mut q = Query::new(Section::Food, "2026-10-12".parse().unwrap());
    let b = services::food(
        &json!({}),
        &json!([{"balance":1033}]),
        &menu,
        &json!([]),
        &q,
    );
    assert_eq!(b.rows.len(), 2);
    assert_eq!(b.stats[0].value, "10,33 ₽");
    assert_eq!(b.rows[0].badge, "193,58 ₽");
    q.mode = "free".into();
    assert_eq!(
        services::food(&json!({}), &json!([]), &menu, &json!([]), &q)
            .rows
            .len(),
        1
    );
}

#[test]
fn detects_current_year_and_children_in_the_family_profile() {
    let p=profile(&json!({"children":[{"id":4,"contingent_guid":"child","representatives":[{"first_name":"Елена"}]}]})).unwrap();
    assert_eq!(p.parent, "Елена");
    assert_eq!(p.student().unwrap().id, "4");
    let (years, selected) =
        grades::academic_years(&json!([{"id":44,"current_year":true,"name":"2026–27"},{"id":21}]));
    assert_eq!(selected, "44");
    assert_eq!(years.len(), 2);
    let subjects = grades::finals(
        &json!({"subjects":[{"id":4,"name":"Физика","periods":[{"name":"I","final_mark":"5"}]}]}),
    );
    assert_eq!(subjects.rows[0].fields[0].value, "5");
}
