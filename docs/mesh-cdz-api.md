# МЭШ: API домашних заданий и ЦДЗ (цифровых домашних заданий)

Документ описывает, какие эндпоинты использует веб-дневник `school.mos.ru`, чтобы
показать домашнее задание с цифровыми материалами (ЦДЗ), узнать статус их
выполнения и запустить материал на прохождение. Всё снято с живой сессии
ученика 20.11.2026 на примере ДЗ по химии на вторник 24.11.2026 (8 класс), веб-дневник
версии 4.26.1.

> Идентификаторы в примерах заменены на плейсхолдеры: `{student_id}`,
> `{person_id}` и т. п. Значения токенов и подписей не приводятся.

## Оглавление

1. [Общая схема](#1-общая-схема)
2. [Авторизация на school.mos.ru](#2-авторизация-на-schoolmosru)
3. [Список ДЗ](#3-список-дз)
4. [Карточка урока с ДЗ](#4-карточка-урока-с-дз)
5. [Расширенные данные материалов](#5-расширенные-данные-материалов)
6. [Статус выполнения материала (LRS)](#6-статус-выполнения-материала-lrs)
7. [Запуск материала](#7-запуск-материала)
8. [Партнёр «Облако Знаний» (fizikon_module)](#8-партнёр-облако-знаний-fizikon_module)
9. [Тесты Библиотеки МЭШ (test_task_binding / test_spec_binding)](#9-тесты-библиотеки-мэш-test_task_binding--test_spec_binding)
10. [Тематическое планирование](#10-тематическое-планирование)
11. [Сводная таблица идентификаторов](#11-сводная-таблица-идентификаторов)
12. [Подводные камни](#12-подводные-камни)

---

## 1. Общая схема

```
school.mos.ru (дневник)
 ├─ GET  /api/family/web/v1/homeworks                    список ДЗ за период
 ├─ GET  /api/family/web/v1/lesson_schedule_items/{id}   урок + ДЗ + материалы
 ├─ POST /api/family/materials/v1/additional_materials   карточки материалов + ссылки
 ├─ GET  /api/lrs/v2/results                             выполнено / баллы (по материалу)
 └─ GET  /api/ej/partners/v1/homeworks/launch            подписанная ссылка запуска
        │  (тело ответа — URL)
        ▼
     /api/launcher/v1/launch?activity_url=…&context=<JWT>&registration=…&role=student
        │  OAuth school.mos.ru/v1/oauth/authorize?client_id=<партнёр>
        ├──► mes-physicon.mos.ru  →  appmes.oblakoz.ru/api/mobilev1/*   («Облако Знаний»)
        └──► uchebnik.mos.ru/exam/challenge/{id}  →  /webtests/exam/rest/secure/*   (тесты МЭШ)
                          │
                          ▼
              результат пишется в LRS (xAPI), откуда его читает /api/lrs/v2/results
```

## 2. Авторизация на school.mos.ru

Токен — значение cookie `aupd_token` (JWT). Его можно взять из браузера или
получить входом (см. `QrLogin.kt`, `CookieRefresh.kt`).

Все запросы ниже к `school.mos.ru/api/...` отправляются с заголовками:

| Заголовок | Значение | Примечание |
|---|---|---|
| `Authorization` | `Bearer {aupd_token}` | обязательно |
| `Profile-Id` | `{student_id}` (напр. `37…66`) | id профиля ученика |
| `Profile-Type` | `student` | |
| `X-Mes-Subsystem` | `familyweb` | |
| `X-Mes-RoleId` | `1` | 1 = ученик |
| `Accept` | `application/json, text/plain, */*` | |
| `Content-Type` | `application/json` | для POST |

Только cookie (без `Authorization`) не хватает: шлюз отвечает
`403 {"message":"You're not allowed to access this resource"}`.

Для `POST /api/family/materials/v1/additional_materials` веб шлёт немного другой
набор: без `Profile-Id`/`Profile-Type`, но с `X-Mes-GlobalRoleId: 1`.

Ответы идут через шлюз Gravitee (в заголовках `x-gravitee-request-id`,
`x-gravitee-transaction-id`).

## 3. Список ДЗ

```
GET https://school.mos.ru/api/family/web/v1/homeworks
    ?from=2026-11-24&to=2026-11-24&student_id={student_id}
```

Ответ: `{ "payload": [ … ] }`, один элемент — одно ДЗ к одному уроку.

```jsonc
{
  "type": "oo",                         // тип обучения (OO — основное)
  "description": "Продолжение устного опроса + цдз + …",
  "homework": "Продолжение устного опроса + цдз + …",
  "comments": [],
  "materials": [                        // краткие карточки ЦДЗ (urls здесь пустые!)
    {
      "uuid": "30f933ab-2414-55c7-b0c9-9ce588f5008d",
      "type": "fizikon_module",
      "selected_mode": "execute",
      "type_name": "Облако знаний",
      "title": "Воздух и его состав",
      "urls": [], "id": null, "description": null, "content_type": null,
      "action_id": 1, "action_name": "Пройти"
    }
  ],
  "attachments": [],
  "homework_entry_student_id": 5305857296,  // для отметки «выполнено»
  "homework_entry_id": 355435138,           // для запуска материала и LRS
  "homework_id": 354577732,
  "subject_id": 33623577,
  "subject_name": "Химия",
  "group_id": 13375601,
  "date": "2026-11-24",
  "date_assigned_on": "2026-11-10",
  "date_prepared_for": "2026-11-24T00:00:00",
  "lesson_date_time": "2026-11-24T09:40:00",
  "is_done": false,
  "has_teacher_answer": false,
  "written_answer": null,
  "homework_created_at": "2026-11-10 08:58:00",
  "homework_updated_at": "2026-11-10 08:58:00"
}
```

Связь с уроком в этом ответе явно не указана: id урока (`lesson_schedule_item`)
веб берёт из расписания, а URL страницы урока имеет вид
`/diary/homeworks/homeworks/{schedule_item_id}_normal?active_tab=homework&sidebar=homeworks_{homework_entry_id}`.

Отметка «выполнено» (уже реализована в клиенте):
`POST`/`DELETE /api/family/web/v1/homeworks/{homework_entry_student_id}/done`.

## 4. Карточка урока с ДЗ

Открывается при клике на ДЗ в списке.

```
GET https://school.mos.ru/api/family/web/v1/lesson_schedule_items/{schedule_item_id}
    ?student_id={student_id}&type=OO&person_id={person_id}
```

- `schedule_item_id` — id урока в расписании (в примере `638611811`).
- `type` — `OO` (обычный урок); у других типов уроков другое значение
  (см. `lesson_education_type`).
- `person_id` — UUID персоны ученика (из профиля).

Ответ (сокращён):

```jsonc
{
  "id": 638611811,
  "plan_id": 21980303,                // → тематическое планирование, §10
  "group_id": 13375601,
  "subject_id": 33623577,
  "subject_name": "Химия",
  "date": "2026-11-24",
  "begin_time": "09:00", "end_time": "09:40",
  "begin_utc": 1795500000, "end_utc": 1795502400,
  "teacher": { "last_name": "…", "first_name": "…", "middle_name": "…",
               "birth_date": null, "sex": null, "user_id": null },
  "room_number": "407", "room_name": "Кабинет химии", "building_name": "…",
  "lesson_type": "NORMAL",
  "lesson_education_type": "OO",
  "is_missed_lesson": false,
  "is_virtual": false,
  "marks": [],
  "homework_presence_status_id": 3,
  "lesson_homeworks": [
    {
      "homework": "Продолжение устного опроса + цдз + …",
      "homework_id": 354577732,
      "homework_entry_id": 355435138,
      "homework_entry_student_id": 5305857296,
      "attachments": [],
      "is_done": false,
      "is_smart": false,
      "auto_generated": false,
      "written_answer": null,
      "date_assigned_on": "2026-11-10T00:00:00",
      "date_prepared_for": "2026-11-24T00:00:00",
      "homework_created_at": "2026-11-10 08:58:00",
      "homework_updated_at": "2026-11-10 08:58:00",
      "additional_materials": [                 // те же краткие карточки, urls пустые
        { "uuid": "30f933ab-…", "type": "fizikon_module",   "type_name": "Облако знаний",
          "title": "Воздух и его состав",          "selected_mode": "execute", "action_name": "Пройти" },
        { "uuid": "52125fc8-…", "type": "test_task_binding", "type_name": "Прочее",
          "title": "Основные компоненты воздуха",  "selected_mode": "execute", "action_name": "Пройти" },
        { "uuid": "c22cfe5d-…", "type": "test_spec_binding", "type_name": "Тестовая спецификация (Тест)",
          "title": "Входное тестирование",         "selected_mode": "execute", "action_name": "Пройти" }
      ]
    }
  ],
  "details": {
    "lessonId": 1660484206,                  // id урока в поурочном плане
    "lesson_topic": "Физические и химические свойства кислорода. …",
    "theme": {
      "theme_frames": [
        { "id": 31944810, "title": "Свойства кислорода. Понятие об оксидах",
          "oge_task_name": ["10", …], "ege_task_name": null, "theme_frames": [] }
      ]
    },
    "additional_materials": [ … ],           // материалы урока (не ДЗ), type_name "Другое"
    "content": []
  },
  "teacher_comments": [], "kr_attachments": [],
  "homework_to_give": null, "control": null, "evaluation": null,
  "remote_lesson": null, "student_work": null, "theme_mastery": null
}
```

Типы материалов ЦДЗ, встреченные в примере:

| `type` | Где выполняется | Подпись в UI |
|---|---|---|
| `fizikon_module` | «Облако Знаний», `mes-physicon.mos.ru` | Самостоятельная работа |
| `test_task_binding` | `uchebnik.mos.ru/exam` | Задание |
| `test_spec_binding` | `uchebnik.mos.ru/exam` | Тематический тест |

## 5. Расширенные данные материалов

Краткие карточки из §3–4 не содержат ссылок, издателя и описания. Веб
дозапрашивает их пачкой:

```
POST https://school.mos.ru/api/family/materials/v1/additional_materials
Content-Type: application/json
```

```json
{
  "materials": [
    { "uuid": "30f933ab-2414-55c7-b0c9-9ce588f5008d", "purpose": "for_home",
      "selected_mode": "execute", "homework_entry_student_id": 5305857296 },
    { "uuid": "52125fc8-7abb-570d-8619-a477fa54da29", "purpose": "for_home",
      "selected_mode": "execute", "homework_entry_student_id": 5305857296 }
  ]
}
```

Ответ — материалы, сгруппированные по назначению и режиму:

```jsonc
{
  "additional_materials": [
    {
      "purpose": "for_home",
      "material_groups": [
        {
          "selected_mode": "execute",
          "homework_entry_student_id": 5305857296,
          "amount": 3,
          "materials": [
            {
              "uuid": "30f933ab-2414-55c7-b0c9-9ce588f5008d",
              "type": "fizikon_module",
              "type_name": "Самостоятельная работа",
              "title": "Воздух и его состав",
              "description": "Приложение разработано для уроков по теме «Во…",
              "action_id": 1, "action_name": "Пройти",
              "partner":          { "id": 282, "name": "Облако Знаний", "icon_url": "https://uchebnik.mos.ru/system/publishing_houses/icons/…" },
              "publishing_house": { "id": 282, "name": "Облако Знаний", "icon_url": "…" },
              "author": null, "content_type": null,
              "urls": [
                { "type": "view",   "url": "https://school.mos.ru/api/launcher/v1/launch?activity_url=https://uchebnik.mos.ru/material/{cms_uuid}" },
                { "type": "edit",   "url": "null" },
                { "type": "launch", "url": "https://school.mos.ru/api/launcher/v1/launch?activity_url=https://uchebnik.mos.ru/cms/materials/{cms_uuid}/launch?" }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

Шаблоны `activity_url` по типам:

| `type` | `activity_url` |
|---|---|
| `fizikon_module` | `https://uchebnik.mos.ru/cms/materials/{cms_uuid}/launch?` (view: `/material/{cms_uuid}`) |
| `test_task_binding` | `https://uchebnik.mos.ru/exam/challenge/{challenge_id}` |
| `test_spec_binding` | `https://uchebnik.mos.ru/exam/challenge/{challenge_id}` |

Обрати внимание: `cms_uuid`/`challenge_id` — это не `uuid` материала из ДЗ.

**Важно:** ссылки из `urls[]` *без контекста*. Если открыть их напрямую,
результат не привяжется к ДЗ. Для прохождения ЦДЗ используй §7.

Порядок материалов в ответе может отличаться от порядка в `lesson_homeworks`.

## 6. Статус выполнения материала (LRS)

Отсюда берутся «Выполнено 20 ноя в 18:39 за 1 м 46 с», «1/1 баллов» и
«Не выполнено» на карточках. Один запрос на каждый материал:

```
GET https://school.mos.ru/api/lrs/v2/results?context={url-encoded JSON}&size=20
```

`context`:

```json
{
  "material_id": "52125fc8-7abb-570d-8619-a477fa54da29",
  "person_id":   "{person_id}",
  "work_type":   "1",
  "work_id":     355435138
}
```

- `work_type` — строка `"1"` (= домашнее задание).
- `work_id` — `homework_entry_id` (не `homework_entry_student_id`).

Ответ — страница Spring Data (`content`, `pageable`, `totalElements`, …).
Пустой `content` означает «не выполнено». Элемент `content[]`:

```jsonc
{
  "result_id": 360018600,
  "final": true,
  "status": 2,                          // 2 — завершено
  "success": true,
  "started_at": "2026-11-20T15:39:29.694538Z",
  "duration": 106,                      // секунды
  "recommended_mark": 1,
  "recommended_mark_scale": 1,
  "score": null, "max_score": null,     // баллы по попытке — в items[]
  "completion_initiator": 0,
  "url_details": "https://uchebnik.mos.ru/exam/challenge/{challenge_id}/attempt/{attempt_id}",
  "url_control": "https://uchebnik.mos.ru/exam/challenge/…",
  "url_improve": null,
  "expert_comment": null,
  "activity_id": "https://uchebnik.mos.ru/exam/challenge/{challenge_id}",
  "registration": "f95b2884-…",          // registration из запуска (§7)
  "actor": { "objectType": "Agent", "account": { "name": "77:{person_id}" } },
  "actual_person_id": "{person_id}",
  "actual_staff_id": null,
  "context": {
    "work_id": 355435138, "work_type": "1",
    "person_id": "{person_id}", "material_id": "52125fc8-…",
    "start_at": 1795189138283
  },
  "items": [                            // по заданиям
    { "task_number": 802577977, "status": 0, "type": 1,
      "score": 1, "max_score": 1, "mastery_score": 1,
      "has_answer": true, "education_level_id": 2,
      "topic_id": [31944805, …], "theme_id": 0,
      "duration": null, "started_at": null,
      "url_details": null, "url_control": null, "expert_comment": null }
  ]
}
```

«Результат» в UI открывает `url_details`.

## 7. Запуск материала

Кнопка «Перейти» делает два шага.

### 7.1. Получить подписанную ссылку

```
GET https://school.mos.ru/api/ej/partners/v1/homeworks/launch
    ?homework_entry_id=355435138
    &material_id=30f933ab-2414-55c7-b0c9-9ce588f5008d
```

Заголовки — как в §2.

- `homework_entry_id` — именно `homework_entry_id` (355435138). Если передать
  `homework_entry_student_id`, ответ будет
  `400 {"code":400,"message":"Произошла непредвиденная ошибка…","error_text":"argument \"content\" is null"}`.
- `material_id` — `uuid` материала из ДЗ.

Ответ: **статус 302**, но `Location` нет; URL лежит в **теле** ответа
(`Content-Type: application/json`, тело — строка без кавычек):

```
https://school.mos.ru/api/launcher/v1/launch
  ?activity_url=https%3A%2F%2Fuchebnik.mos.ru%2Fcms%2Fmaterials%2F{cms_uuid}%2Flaunch%3F
  &context=<JWT>
  &registration={uuid}
  &role=student
```

В браузере веб добавляет ещё `utm_source=familyw&utm_medium=lesson&utm_campaign=1`
и открывает ссылку через `window.open`.

`context` — JWT (HS256), подписанный сервером МЭШ. Payload:

```json
{
  "wt":  "1",                     // work_type
  "wti": 355435138,               // work_id = homework_entry_id
  "pid": "{person_id}",
  "dt":  1795189952688,           // время выдачи, мс
  "mi":  "30f933ab-…"             // material_id
}
```

`registration` — новый UUID на каждый запуск, он же потом попадает в LRS (§6).

### 7.2. Открыть ссылку

`/api/launcher/v1/launch` — это цепочка редиректов, работающая на cookie
сессии `school.mos.ru`, поэтому её нужно открывать в браузере или WebView, где
пользователь залогинен. Bearer-токена приложения для неё недостаточно.

Цепочка для «Облака Знаний»:

```
/api/launcher/v1/launch?…
 → https://school.mos.ru/v1/oauth/authorize
       ?client_id=fizicon-cloud
       &scope=openid,email,oic_given_name,oic_family_name,oic_middle_name,oic_updated_at,oic_education
       &response_type=code&response_mode=query
       &redirect_uri=https://mes-physicon.mos.ru/auth/login
       &state={uuid}
 → https://mes-physicon.mos.ru/auth/login?code=…&state=…
 → https://mes-physicon.mos.ru/content/{course_ext_id}?activityId=…&actor=…&auth_token=…&context=…&endpoint=…&…
```

Для тестов МЭШ конечная точка — `https://uchebnik.mos.ru/exam/challenge/{challenge_id}`
(сессия на uchebnik поднимается сама, см. §9).

**Ссылка одноразовая.** Повторное открытие той же ссылки (или F5 на странице
партнёра) показывает «Вы повторно перешли по ранее использованной ссылке».
Для каждого открытия нужен новый вызов 7.1.

Сейчас клиент (`MeshClient.launchHomeworkMaterial`) делает 7.1 и открывает
результат в браузере, это корректно.

## 8. Партнёр «Облако Знаний» (fizikon_module)

Страница плеера:

```
https://mes-physicon.mos.ru/content/Physicon_IMUMK_Course_375209
  ?activityId=https://uchebnik.mos.ru/cms/materials/{cms_uuid}/launch
  &actor={"objectType":"Agent","account":{"name":"77:{person_id}"}}
  &actorid={user_id}
  &auth_token=…                   (секрет)
  &authurl=https://school.mos.ru
  &context=<JWT из 7.1>
  &endpoint=https://uchebnik.mos.ru/lrs-dhw/      (xAPI LRS)
  &fetch=…                        (секрет)
  &person_id={person_id}
  &profile_id={profile_id}
  &registration={uuid}
  &role=student
  &user_id={user_id}
  &state=…
  #/contents/slide/1/{scene_id}
```

Сервер рендерит в HTML `var data = {…}` с параметрами плеера: `userLogin`
(вида `…@school.imumk.ru`), `sessionKey` (64 hex), `courseID` (`"16058"`),
`courseExtID`, `courseTitle`, `mode` (`"exam"`), `sessionId`, `registration`,
`API_URL` (`https://appmes.oblakoz.ru/api/`), `classTitle`, `subjectTitle`,
`testTypeTitle` (`"СР"`), `subjectCode` (`"Chemistry"`), флаги `showSolution`,
`solveAgain` и т. д.

Статика: `https://cdn.oblakoz.ru/IMUMKWeb/{ver}/web.js`, контент сцен —
`https://cdn.oblakoz.ru/IMUMKContent/{courseID}/content/{scene_id}/{scene_id}.js`,
манифест курса — `https://mes-physicon.mos.ru/content/manifest/{courseID}`.

**После загрузки страницы запускается таймер работы** (в примере осталось
«1 ч 24 мин»), так что просто открыть материал «посмотреть» уже значит начать попытку.

### 8.1. API плеера `https://appmes.oblakoz.ru/api/mobilev1/*`

Все методы: `POST`, `application/x-www-form-urlencoded`, заголовок
`X-AppPlatform: windesktop`, тело из трёх полей:

| Поле | Значение |
|---|---|
| `uid` | `userLogin` |
| `data` | JSON-строка с параметрами метода |
| `key` | подпись запроса |

Подпись (функция из `web.js`):

```js
key = SHA256_hex( sessionKey + url + userLogin + data )
// url — полный URL метода, напр. "https://appmes.oblakoz.ru/api/mobilev1/journal2"
```

Особенность: в `journal2` в функцию передаётся объект, а не строка, поэтому
последнее слагаемое — буквально `"[object Object]"`. При проверке сервер
принял и такой вариант, и подпись по JSON-строке `data`. Насколько строго
сервер проверяет `key` в остальных методах, мы не проверяли.

Методы, вызванные при открытии ЦДЗ:

| Метод | `data` | Назначение |
|---|---|---|
| `journal2` | `{"course":"16058","registration":"{uuid}"}` | журнал прогресса по курсу |
| `resulttc` | xAPI-запрос, см. ниже | отправка statements/state (прокси в LRS) |
| `addlit` | `{"ModuleExternalId":"{courseExtID}"}` | адаптивный трек |
| `newfeedback2` | `{}` (через `FormData`) | есть ли новые сообщения обратной связи |
| `trace` | `{moduleId, session, journalLoadMs, envLoadMs, sceneId, sceneLoadMs}` | телеметрия загрузки |

Ответ `journal2` до начала работы:

```json
{"startTime":"0001-01-01T00:00:00","score":0,"scoreMax":5,"spentTime":"PT0S","results":[],"result":"Ok"}
```

При ошибке приходит `{"result":"Error","errorCode":…}`.

`resulttc` оборачивает xAPI-запросы. Формат ниже восстановлен по коду `web.js`:
сами тела в сети мы не перехватывали, потому что задания не решались.

```jsonc
// попытка / ответ на сцену
{ "method": "POST", "url": "/statements",
  "content": [{ "timestamp": "…", "actor": {"mbox": "mailto:{userLogin}"},
                "verb": {"id": "http://adlnet.gov/expapi/verbs/attempted"},
                "object": {"id": "http://fizikon.ru/{course}/{module}"},
                "result": {"completion": false, "duration": "PT…"} }] }

// завершение работы («Завершить работу»)
{ "method": "POST", "url": "/statements",
  "content": [{ "verb": {"id": "http://adlnet.gov/expapi/verbs/completed"},
                "object": {"id": "http://fizikon.ru/{course}"},
                "result": {"duration": "PT…", "score": {"raw": 3, "max": 5, "scaled": 0.6}},
                "context": {"registration": "{uuid}"} }] }

// «Отложить»: verb .../suspended
// прогресс модуля
{ "method": "POST", "url": "/activities/state",
  "data": {"activityid": "http://fizikon.ru/{module}", "agent": {"mbox": "mailto:{userLogin}"}, "stateid": "progress"},
  "content": {"module": "…", "progress": 100} }
```

Кроме этих, в `web.js` есть `getresult`, `nextadaptscene`, `setbookmark`,
`deletebookmark`, `attach`, `attachdl`, `attachdelete`, `listfiles`,
`savescenehtml`, `postfeedback`, `settings`, `deviceerrorlog`, `login`,
`register`, `password`, `activatepin`, `update`, `getbonuslog`,
`applicationzipurl3`.

Результат в итоге попадает в LRS МЭШ (`endpoint=https://uchebnik.mos.ru/lrs-dhw/`)
и становится виден через §6.

## 9. Тесты Библиотеки МЭШ (test_task_binding / test_spec_binding)

SPA `https://uchebnik.mos.ru/exam/`. Страницы:

- прохождение: `/exam/challenge/{challenge_id}`
- результат попытки: `/exam/challenge/{challenge_id}/attempt/{attempt_id}`
  (это `url_details` из §6)

Запросы при открытии результата:

| Запрос | Назначение |
|---|---|
| `GET /exam/config.json`, `GET /exam/abConfig.json` | конфиг SPA |
| `POST /acl/api/session/v2/refresh` | поднимает или обновляет сессию uchebnik по cookie |
| `GET /webtests/exam/rest/secure/auth/aclx/user_info` | профиль пользователя |
| `GET /webtests/exam/rest/secure/challenge/task/answers?challenge_id={id}&challenge_attempt_id={attempt_id}` | ответы и проверка по попытке |

Заголовки для `/webtests/exam/rest/secure/*` (по коду `main.module_*.js`):

| Заголовок | Источник |
|---|---|
| `Auth-Token` | cookie `auth_token` на `uchebnik.mos.ru` |
| `Profile-Id` | cookie `Profile-Id` |
| `User-Id` | cookie `User-Id` |
| `Accept` | `application/json;charset=UTF-8` |
| `Content-Type` | `application/json` |

Без этих заголовков (только cookie) — `401`. `auth_token` для uchebnik — JWT,
его payload содержит `usr` (user id) и `prf[]` (профили `{id, type}`).

Сам процесс прохождения теста (старт попытки, отправка ответов) мы не снимали,
чтобы не тратить попытку по невыполненному «Входному тестированию».

## 10. Тематическое планирование

Подгружается вместе с карточкой урока (вкладка «Об уроке»):

```
GET https://school.mos.ru/api/ej/plan/family/v1/lesson_plans
    ?plan_id=21980303&school_id={school_id}
    &ignore_owner=true&with_modules=true&with_topics=true&with_lessons=true
    &status=for_calendar_plan
```

Ответ — массив планов:
`[{ id, name: "26/27. Химия-8", class_level_id, subject_id, teacher_id,
module_count, lesson_count, week_hours, status, modules: [{ id, name, ordinal,
topics: [{ id, name, ordinal, color, theme_frame_id, lessons: [{ id, name,
ordinal, is_test_planned, is_hw_planned, scripts /* JSON-строка */,
theme_frame_ids, controllable_items }] }] }] }]`.

`details.lessonId` из §4 совпадает с `lessons[].id` плана.

Вместе с ним веб запрашивает `GET /api/usersettings/v1?name=settings_group_v1&subsystem_id=1`
(настройки интерфейса).

## 11. Сводная таблица идентификаторов

| Поле | Пример | Откуда | Куда передаётся |
|---|---|---|---|
| `student_id` / `Profile-Id` | `37…66` | профиль | `homeworks`, `lesson_schedule_items`, заголовок |
| `person_id` | UUID | профиль | `lesson_schedule_items`, LRS `context.person_id` |
| `schedule_item_id` | `638611811` | расписание | `lesson_schedule_items/{id}` |
| `homework_entry_id` | `355435138` | ДЗ | **launch**, LRS `work_id` |
| `homework_entry_student_id` | `5305857296` | ДЗ | `…/homeworks/{id}/done`, `additional_materials` |
| `homework_id` | `354577732` | ДЗ | — |
| `material uuid` | `30f933ab-…` | `materials[]` | launch `material_id`, LRS `material_id`, `additional_materials` |
| `plan_id` | `21980303` | урок | `lesson_plans` |
| `challenge_id` / `cms_uuid` | `150651852` / `e7cf…` | `urls[].activity_url` | страницы uchebnik |
| `registration` | UUID | ответ launch | LRS, плеер партнёра |

## 12. Подводные камни

1. `homework_entry_id` и `homework_entry_student_id` легко перепутать.
   Launch и LRS работают с первым, отметка «выполнено» и `additional_materials` — со вторым.
2. `partners/v1/homeworks/launch` возвращает 302 **с URL в теле**. HTTP-клиент
   нужно настроить без следования редиректам и читать тело даже при 3xx.
3. Ссылка запуска одноразовая. Её нельзя кешировать, а повторное открытие
   (в т. ч. перезагрузка страницы партнёра) сломается.
4. `/api/launcher/v1/launch` требует cookie-сессии `school.mos.ru` (OAuth к
   партнёру). В Android-клиенте её лучше открывать в WebView/Custom Tab, где
   пользователь залогинен, или прокидывать cookie `aupd_token` в WebView.
5. Ссылки из `additional_materials.urls[]` не содержат `context`: прохождение
   по ним не засчитается в ДЗ.
6. Открытие материала «Облака Знаний» сразу запускает таймер работы.
7. Статус «выполнено» по ЦДЗ на карточках веб берёт из LRS (`/api/lrs/v2/results`).
   В примере у ДЗ `is_done: false`, хотя один из трёх материалов уже выполнен:
   `is_done` — это галочка ДЗ целиком, а не статус ЦДЗ.
8. Веб кеширует ответы (React Query). Повторное открытие той же карточки
   может не дать сетевых запросов, и это нормально.
