use crate::*;
use serde_json::json;
use std::collections::{BTreeMap, BTreeSet};

pub fn account() -> Account {
    Account {
        students: vec![Student {
            id: "demo-student".into(),
            person: "demo-person".into(),
            name: "Александра Волкова".into(),
            class_name: "9 Б".into(),
            school: "Демонстрационная школа".into(),
            ..Default::default()
        }],
        active: "demo-student".into(),
        parent: "Елена Волкова".into(),
        demo: true,
        remembered: true,
        ..Default::default()
    }
}

const LESSONS: [(&str, &str, &str, &str); 5] = [
    ("08:30", "09:15", "Алгебра", "214"),
    ("09:25", "10:10", "Русский язык", "308"),
    ("10:30", "11:15", "Физика", "202"),
    ("11:35", "12:20", "История", "311"),
    ("12:40", "13:25", "Английский язык", "205"),
];
const TASKS: [&str; 5] = [
    "Решить задачи 142–145. Для каждой записать ход решения.",
    "Разобрать сложные предложения в упражнении 76.",
    "Построить график зависимости скорости от времени.",
    "Составить хронологию событий по параграфу 12.",
    "Прочитать текст и подготовить пять вопросов.",
];
pub fn board(q: &Query, done: &BTreeSet<String>) -> Board {
    let date = q.date.to_string();
    let homework_id = |i: usize| format!("demo-{date}-{i}");
    match q.section {
        Section::Schedule => {
            let activities=LESSONS.iter().enumerate().map(|(i,(start,end,title,room))|json!({
                "type":"LESSON","begin_time":start,"end_time":end,"room_number":room,"id":format!("lesson-{i}"),
                "lesson":{"subject_name":title,"subject_id":i,"teacher":"Мария Ивановна","homework":TASKS[i],"homework_entry_student_id":homework_id(i)}
            })).collect::<Vec<_>>();
            diary::schedule(&json!({"activities":activities}), &date)
        }
        Section::Homework => {
            let tasks=LESSONS.iter().enumerate().map(|(i,(_,_,title,_))|{
                let due=if q.mode=="assigned"{q.date+Days::new(1)}else{q.date};
                let id=format!("demo-{due}-{i}");
                json!({"homework_entry_student_id":id,"homework_entry_id":format!("entry-{i}"),"subject_name":title,
                    "subject_id":i,"description":TASKS[i],"date_prepared_for":due,"date_assigned_on":due-Days::new(1),
                    "is_done":done.contains(&id),"type":"oo","materials":[]})
            }).collect::<Vec<_>>();
            diary::homework(&json!(tasks), q)
        }
        Section::Grades if q.mode == "final" => {
            let yr = q.date.year() - i32::from(q.date.month() < 9);
            let year = if q.filter == "previous" { yr - 1 } else { yr };
            let data = LESSONS
                .iter()
                .enumerate()
                .map(|(i, (_, _, t, _))| {
                    json!({"id":i,"name":t,"year_mark":if year<yr{Some(5)}else{None},
                "periods":[{"name":"I","final_mark":5},{"name":"II","final_mark":4}]})
                })
                .collect::<Vec<_>>();
            let mut b = grades::finals(&json!({"subjects":data}));
            b.choices = vec![
                Choice {
                    id: "current".into(),
                    name: format!("{}–{}", yr, yr + 1),
                },
                Choice {
                    id: "previous".into(),
                    name: format!("{}–{}", yr - 1, yr),
                },
            ];
            b.selection = if q.filter == "previous" {
                "previous"
            } else {
                "current"
            }
            .into();
            b
        }
        Section::Subjects | Section::Grades
            if q.section == Section::Subjects || q.mode == "subject" =>
        {
            let data = LESSONS
                .iter()
                .enumerate()
                .map(|(i, (_, _, title, _))| {
                    json!({"subject_id":i,"subject_name":title,"average":4.6,
                "periods":[{"title":"I","value":5,"marks":[demo_mark(i,&date)]}]})
                })
                .collect::<Vec<_>>();
            grades::subjects(&json!(data))
        }
        Section::Grades | Section::Subjects => {
            let data = (0..22)
                .map(|i| demo_mark(i, &(q.date - Days::new(i as u64)).to_string()))
                .collect::<Vec<_>>();
            grades::marks(&json!(data))
        }
        Section::Ranking => {
            let choices = LESSONS
                .iter()
                .enumerate()
                .map(|(i, (_, _, s, _))| json!({"subjectId":i,"subjectName":s}))
                .collect::<Vec<_>>();
            let data=["Анна Л.","Александра В.","Иван М.","Дарья К.","Михаил Р.","Вера А."].iter().enumerate().map(|(i,n)|{
                let avg=4.93-i as f64*0.13-q.filter.parse::<f64>().unwrap_or(0.)/100.;
                json!({"name":n,"personId":if i==1{"demo-person".into()}else{format!("demo-rank-{i}")},
                    "rank":{"averageMarkFive":avg,"averageMarkFive30":avg-0.05,"rankPlace":i+1,"rankPlace30":i+1}})
            }).collect::<Vec<_>>();
            grades::ranking(
                &json!(data),
                &json!(choices),
                "demo-person",
                q.mode == "month",
                &BTreeMap::new(),
            )
        }
        Section::Food => services::food(
            &json!({"preorderAllowed":true}),
            &json!([{"balance":72450,"expenseDayLimit":25000}]),
            &json!([{"onDate":date,"items":[{"id":1,"name":"Горячий обед","kind":2,"paymentTypes":[0,1],"price":18500,
                "complexItems":[{"dish":{"name":"Суп овощной","weight":"250 г"}},{"dish":{"name":"Гречка с курицей","weight":"200 г"}}]}]}]),
            &json!({"orders":[{"id":101,"complexName":"Горячий обед","onDate":date,"price":18500,"status":"Принят"}]}),
            q,
        ),
        Section::Profile => services::profile_board(&account()),
        Section::Card => Board {
            card: Some(Card {
                state: "demo".into(),
                fields: vec![Field::new("Ученик", "Александра Волкова")],
                ..Default::default()
            }),
            ..Default::default()
        },
        Section::Settings => Board::default(),
    }
}
fn demo_mark(i: usize, date: &str) -> Value {
    json!({"id":format!("demo-mark-{i}"),"created_at":date,"subject_name":LESSONS[i%LESSONS.len()].2,
        "value":if i%3==1{4}else{5},"weight":if i%4==0{2}else{1},"control_form_name":(["Самостоятельная работа","Ответ на уроке","Практикум"][i%3])})
}
pub fn mark_detail() -> Row {
    grades::detail(
        &json!({"teacher":{"first_name":"Мария","middle_name":"Ивановна"},"activity":{"lesson_topic":"Закрепление материала"},
        "comment":"Решение обосновано.","class_results":{"total_students":24,"marks_distributions":[{"mark_value":{"five":5},"percentage_of_students":50,"number_of_students":12}]}}),
    )
}
