# МЭШ: API домашних заданий и ЦДЗ (цифровых домашних заданий)

Документ описывает, какие эндпоинты использует веб-дневник `school.mos.ru`, чтобы
показать домашнее задание с цифровыми материалами (ЦДЗ), узнать статус их
выполнения, запустить материал, получить задания, отправить ответы, завершить
попытку и посмотреть свои ответы. Всё снято с живой сессии ученика 20.11.2026
на примере ДЗ по химии на вторник 24.11.2026 (8 класс), веб-дневник версии
4.26.1. Для съёмки отправки ответов были пройдены дополнительные попытки:
«Облако Знаний» (1 ответ, завершено) и тест «Основные компоненты воздуха»
(попытка № 2).

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
        ├──► mes-physicon.mos.ru            («Облако Знаний», §8)
        │      вопросы: cdn.oblakoz.ru/IMUMKContent/…  (проверка в плеере)
        │      POST appmes.oblakoz.ru/api/mobilev1/resulttc   ответ / завершение (xAPI)
        │      POST appmes.oblakoz.ru/api/mobilev1/journal2   журнал попытки
        └──► uchebnik.mos.ru/exam/challenge/{id}   (тесты МЭШ, §9)
               POST /acl/api/session/v2/refresh                         свой токен uchebnik
               POST /webtests/exam/rest/secure/challenge/{id}/start-attempt   попытка + задания
               POST /webtests/exam/rest/secure/challenge/task/answer          ответ
               POST /webtests/exam/rest/secure/challenge/{id}/complete_attempt
               GET  /webtests/exam/rest/secure/challenge/{id}/attempt/{a}/result
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

Формула подтверждена на живом запросе `resulttc`: подпись, посчитанная по ней,
совпала с той, которую отправил плеер. `uid` — это `userLogin`.

Особенность: в `journal2` в функцию передаётся объект, а не строка, поэтому
последнее слагаемое — буквально `"[object Object]"`. При проверке сервер
принял и такой вариант, и подпись по JSON-строке `data`. Насколько строго
сервер проверяет `key` в остальных методах, мы не проверяли.

Методы, которые вызывает плеер:

| Метод | `data` | Назначение |
|---|---|---|
| `journal2` | `{"course":"16058","registration":"{uuid}"}` | журнал попытки: баллы и ответы по сценам |
| `resulttc` | xAPI-запрос, см. ниже | **отправка ответа** и завершение работы (прокси в LRS) |
| `addlit` | `{"ModuleExternalId":"{courseExtID}"}` | адаптивный трек |
| `newfeedback2` | `{}` (через `FormData`) | есть ли новые сообщения обратной связи |
| `trace` | `{moduleId, session, journalLoadMs, envLoadMs, sceneId, sceneLoadMs}` | телеметрия загрузки |

При ошибке любой метод отвечает `{"result":"Error","errorCode":…}`.

### 8.2. Откуда берутся вопросы

Курс состоит из сцен (одна сцена — одно задание). Список сцен — в манифесте
`https://mes-physicon.mos.ru/content/manifest/{courseID}`: это JS-файл
`var contentActivity='<?xml …><manifest xmlns="http://www.imsglobal.org/xsd/imscp_v1p1" …>'`
(IMS Content Packaging / SCORM 2004). В плеере он доступен как `CourseConfig.items`.
В примере пять сцен:

| `scene_id` | Название |
|---|---|
| `552388` | Состав воздуха |
| `552389` | Значение компонентов воздуха |
| `552390` | Изменение состава воздуха с высотой |
| `552391` | Свойства воздуха |
| `552393` | Вещества, загрязняющие воздух |

Содержимое сцены лежит в `https://cdn.oblakoz.ru/IMUMKContent/{courseID}/content/{scene_id}/{scene_id}.js`
как `var content{scene_id}='…'`: это XML вида `<page subtype="singlechoice">…`,
обфусцированный сдвигом символов. Правильность ответа **проверяет сам плеер**
(в браузере), а на сервер уходит уже готовый вердикт и баллы (см. 8.3). Формат
этого XML и способ его расшифровки мы намеренно не документируем. Для своего
клиента разумнее открывать официальный плеер в WebView (§7.2), чем рисовать
задания самим.

### 8.3. Отправка ответа (`resulttc`, verb `responded`)

Снято вживую: ответ на сцену 552388, вариант 1 («Азот»).

```
POST https://appmes.oblakoz.ru/api/mobilev1/resulttc
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
X-AppPlatform: windesktop

uid={userLogin}&data={JSON ниже}&key={SHA256(sessionKey + url + userLogin + data)}
```

`data`:

```json
{
  "method": "POST",
  "url": "/statements",
  "content": [{
    "context": {
      "registration": "eb119a24-252c-41b4-bc85-754cd57c528b",
      "instructor": { "mbox": "mailto:…" },
      "extensions": {}
    },
    "module": "552388",
    "progress": 100,
    "timestamp": "2026-11-20T17:32:36.188Z",
    "actor":  { "mbox": "mailto:{userLogin}" },
    "verb":   { "id": "http://adlnet.gov/expapi/verbs/responded" },
    "object": { "id": "http://fizikon.ru/Physicon_IMUMK_Course_375209/552388/" },
    "result": {
      "score": { "raw": 1, "max": 1, "scaled": 1 },
      "success": true,
      "completion": true,
      "response": {
        "state": "answered",
        "result": "correct",
        "choices": { "varianttext1": true, "varianttext2": false, "varianttext3": false, "varianttext4": false },
        "write": {}
      },
      "duration": "PT0H0M17S",
      "category": { "cs.2.2.3": 1, "cs.2.4.3": 1, "cce.7.2.2.13": 1 }
    }
  }]
}
```

- `registration` — тот же UUID, что пришёл в ссылке запуска (§7.1).
- `choices` — выбранные варианты (`varianttextN`, нумерация из XML сцены).
  Для заданий со вводом текста заполняется `write`.
- `category` — коды элементов содержания (кодификатор), к которым относится задание.

Ответ: `{"data":"744711e2-e5d4-40a1-9136-d818219ceb75","result":"Ok","errorMessage":null}`,
где `data` — id сохранённого statement (он же `StatementId` в `journal2`).

Кнопка «Заново» в плеере позволяет ответить на сцену ещё раз; это ещё один `responded`.

### 8.4. Завершение работы (`resulttc`, verb `completed`)

Иконка выхода → «Пропущено N заданий… Если завершить работу, то у
невыполненных заданий будет выставлен 0%. Эту работу нельзя отложить и
доделать позже» → «Завершить».

```json
{
  "method": "POST",
  "url": "/statements",
  "content": [{
    "timestamp": "2026-11-20T17:33:47.521Z",
    "actor":  { "mbox": "mailto:{userLogin}" },
    "verb":   { "id": "http://adlnet.gov/expapi/verbs/completed" },
    "object": { "id": "http://fizikon.ru/Physicon_IMUMK_Course_375209" },
    "result": {
      "duration": "PT0H1M28S",
      "score": { "raw": 1, "max": 5, "scaled": 0.2 }
    },
    "context": { "registration": "eb119a24-252c-41b4-bc85-754cd57c528b", "extensions": {} }
  }]
}
```

Ответ: `{"data":"59301aec-…","result":"Ok","errorMessage":null}`. После этого
плеер показывает итог («Отметка 2 · Время 01:28 · Набрано 20%»), а в LRS
дневника (§6) появляется запись `final: true`, `recommended_mark: 1`,
`recommended_mark_scale: 5`, `items` по сценам.

По коду `web.js` существуют ещё два варианта:

```jsonc
// «Отложить» работу
{ "method": "POST", "url": "/statements",
  "content": [{ "verb": {"id": "http://adlnet.gov/expapi/verbs/suspended"},
                "object": {"id": "http://fizikon.ru/{courseExtID}"},
                "result": {"duration": "PT…"}, "context": {"registration": "{uuid}"} }] }

// прогресс модуля (state API)
{ "method": "POST", "url": "/activities/state",
  "data": {"activityid": "http://fizikon.ru/{module}", "agent": {"mbox": "mailto:{userLogin}"}, "stateid": "progress"},
  "content": {"module": "…", "progress": 100} }
```

### 8.5. Получение ответов попытки (`journal2`)

Тот же запрос, что при старте (8.1), после ответа возвращает журнал попытки:

```jsonc
{
  "startTime": "2026-11-20T17:32:19.187",
  "score": 1,
  "scoreMax": 5,
  "spentTime": "PT1M28S",
  "results": [
    {
      "module": "552388",                      // scene_id
      "status": "correct",
      "score": 1, "scoreMax": 1, "weight": 1,
      "spentTime": "PT17S",
      "timestamp": "2026-11-20T17:32:36.187Z",
      "StatementId": "744711e2-e5d4-40a1-9136-d818219ceb75",
      "value": "{\"state\":\"answered\",\"result\":\"correct\",\"choices\":{\"varianttext1\":true,…}}",  // JSON-строка = result.response
      "isCheckedByAi": false,
      "state": "", "step": ""
    }
  ],
  "result": "Ok"
}
```

До первого ответа: `{"startTime":"0001-01-01T00:00:00","score":0,"scoreMax":5,"spentTime":"PT0S","results":[],"result":"Ok"}`.

Журнал привязан к `registration`, то есть к одной попытке. Каждый запуск через
§7 даёт новый `registration` и, значит, новую пустую попытку.

Кроме этих, в `web.js` есть `getresult`, `nextadaptscene`, `setbookmark`,
`deletebookmark`, `attach`, `attachdl`, `attachdelete`, `listfiles`,
`savescenehtml`, `postfeedback`, `settings`, `deviceerrorlog`, `login`,
`register`, `password`, `activatepin`, `update`, `getbonuslog`,
`applicationzipurl3`.

Результат в итоге попадает в LRS МЭШ (`endpoint=https://uchebnik.mos.ru/lrs-dhw/`)
и становится виден через §6.

## 9. Тесты Библиотеки МЭШ (test_task_binding / test_spec_binding)

SPA `https://uchebnik.mos.ru/exam/`, бэкенд `https://uchebnik.mos.ru/webtests/exam/rest/secure`.
Всё ниже снято вживую на попытке № 2 задания «Основные компоненты воздуха»
(`challenge_id = 150651852`, одно задание).

Страницы:

- старт и прохождение: `/exam/challenge/{challenge_id}?activityId=…&actor=…&authurl=…&context=<JWT>&endpoint=…&fetch=…&registration=…&role=student`
  (сюда приводит лаунчер §7.2; эти параметры потом уходят в `start-attempt`)
- результат попытки: `/exam/challenge/{challenge_id}/attempt/{attempt_id}` (это `url_details` из §6)

### 9.1. Авторизация uchebnik

У uchebnik **свой токен**, это не `aupd_token` дневника, и свой `Profile-Id`
(в примере не равен `student_id` из дневника).

```
POST https://uchebnik.mos.ru/acl/api/session/v2/refresh
(cookie сессии uchebnik.mos.ru, которые ставит цепочка лаунчера)
```

Ответ:

```json
{ "accessTokenEom": "<JWT, ~1200 символов>", "accessTokenAupd": "<JWT, ~870 символов>", "profileId": "<8 цифр>" }
```

SPA кладёт `accessTokenEom` в cookie `auth_token`, разбирает его payload
(`usr` → `User-Id`, `prf[]` → профиль с `id == profileId`) и дальше шлёт на
все `/webtests/exam/rest/secure/*`:

| Заголовок | Значение |
|---|---|
| `Authorization` | `Bearer {accessTokenEom}` |
| `Profile-Id` | `{profileId}` |
| `Profile` | `{"id":<profileId>,"type":"student"}` |
| `User-Id` | `usr` из JWT |
| `X-Mes-Subsystem` | `testw` |
| `Accept` | `application/json;charset=UTF-8` |
| `Content-Type` | `application/json` (кроме multipart-запросов) |

Без этих заголовков (только cookie) — `401`. В коде SPA есть и вариант с
заголовком `Auth-Token` (режим ЭЖД и имперсонация), но в живом прохождении
ушёл `Authorization: Bearer`.

### 9.2. Информация о тесте

```
GET /webtests/exam/rest/secure/challenge/{challenge_id}/info
```

```json
{
  "title": "Цифровое Домашнее Задание",
  "subject_ids": [31937343],
  "use_case": "HOMEWORK",
  "allowed_tries": 0,
  "available_time_in_minutes": 0,
  "task_total_count": 1,
  "available_from": "2000-01-01T00:00:00Z",
  "available_until": "3000-01-01T00:00:00Z",
  "can_reanswer": true,
  "answer_order_random": true,
  "is_task_order_random": false,
  "number_of_attempt": 2,
  "last_attempt_id": null,
  "last_attempt_started_at": null,
  "attempt_time_limit_type": "FIXED"
}
```

`allowed_tries: 0` и `available_time_in_minutes: 0` означают «без ограничений»
(в UI: «Количество попыток: Не ограничено», «Время на попытку: Нет»).

Прочее: `GET /webtests/exam/rest/secure/auth/aclx/user_info` →
`{user_id, user_name, staff_id, person_id, rgn_id, user_profiles:[{id, profile_name, profile_original_name}]}`.

### 9.3. Старт попытки и получение заданий

Кнопка «Приступить к прохождению: попытка № N»:

```
POST /webtests/exam/rest/secure/challenge/{challenge_id}/start-attempt
Content-Type: application/json
```

```json
{
  "actorParam": "{\"objectType\":\"Agent\",\"account\":{\"name\":\"77:{person_id}\"}}",
  "registrationParam": "ff384c85-3204-407b-9949-1e73b285a651",
  "activityIdParam": "https://uchebnik.mos.ru/exam/challenge/150651852",
  "resultOverviewUrl": "https://uchebnik.mos.ru/exam/challenge/150651852/attempt/{attemptId}",
  "resultManagementUrl": "https://uchebnik.mos.ru/exam/challenge/150651852/context/{assignmentContextId}/attempt/{attemptId}",
  "context": "<JWT context из ссылки лаунчера>",
  "contextCorrupted": false
}
```

Именно `context` и `registrationParam` привязывают попытку к ДЗ: без них
результат не попадёт в LRS дневника. `{attemptId}` и `{assignmentContextId}`
передаются буквально, как шаблон.

Ответ — сразу все задания попытки (отдельного «получить задания» нет):

```jsonc
{
  "challenge_attempt_id": 409578240,
  "challenge_attempt_started_at": "2026-11-20T17:29:32.524495Z",
  "current_date_time": "2026-11-20T17:29:32.527278Z",
  "groups_context_info": {},
  "tasks_how_to_beat": {},
  "challenge_test_groups": [
    {
      "test_group_num": 0,
      "view_mode": "SINGLE_TASK",
      "challenge_tasks": [
        {
          "id": 802577977,                       // challenge_task_id
          "task_order": 0,
          "question_elements": [
            { "type": "content/text", "text": "Основными компонентами воздуха являются",
              "content": [], "text_styles": [], "block_styles": [] }
          ],
          "answer": {
            "type": "answer/single",
            "options_order": "random",
            "options": [
              { "id": "a9hdqt", "type": "option_type/default", "text": "кислород и азот",
                "content": [], "text_styles": [], "block_styles": [] }
              // … ещё 3 варианта
            ],
            "right_answer": { "id": null },      // во время попытки правильный ответ не отдаётся
            "reference_right_answer": null,
            "text_input_rules": null
          },
          "user_answer": null,                   // при продолжении попытки — сохранённый ответ
          "answer_feedback": null
        }
      ]
    }
  ]
}
```

Типы ответа (`answer.type` / `@answer_type`): в примере `answer/single`; по
коду SPA есть и другие (множественный выбор, ввод, свободный ответ с
файлами). Справочник: `GET /webtests/exam/rest/secure/meta/answer_type`.

### 9.4. Отправка ответа

Кнопка «Ответить» (и «Изменить ответ» — тот же запрос повторно):

```
POST /webtests/exam/rest/secure/challenge/task/answer
Content-Type: multipart/form-data; boundary=…
```

Одна часть `request` с `Content-Type: application/json`:

```json
{
  "challenge_task_id": 802577977,
  "challenge_attempt_id": 409578240,
  "answer": { "@answer_type": "answer/single", "id": "a9hdqt" }
}
```

`answer` = `{"@answer_type": <answer.type>, ...user_answer}`. Для `answer/single`
это `id` выбранного варианта. Для свободного ответа (`FREE`) рядом добавляются
части `files` (по одной на файл).

Ответ: `{"type":"EMPTY"}`. Правильность во время попытки **не сообщается**.

### 9.5. Завершение попытки

«Завершить» → «Вы уверены, что хотите выйти?» → «Подтвердить» →
«Вы уверены, что хотите завершить тестирование?» → «Завершить»:

```
POST /webtests/exam/rest/secure/challenge/{challenge_id}/complete_attempt
{"challenge_id":150651852}
```

Ответ: `{}`. Сразу после этого SPA запрашивает результат (9.6) и показывает
окно «Нам важно ваше мнение» (отзыв, `POST /webtests/exam/rest/secure/attempt/feedback/{id}`,
необязателен).

Результат сразу появляется в LRS дневника (§6) с тем же `registration`.

### 9.6. Результат и ответы попытки

```
GET /webtests/exam/rest/secure/challenge/{challenge_id}/attempt/{attempt_id}/result
GET /webtests/exam/rest/secure/challenge/task/answers?challenge_id={challenge_id}&challenge_attempt_id={attempt_id}
```

Оба возвращают одно и то же (у `task/answers` есть ещё `can_assess_task_ids: []`):

```jsonc
{
  "use_case": "HOMEWORK",
  "attempt_info": {
    "attempt_id": 409578240,
    "attempt_num": 2,
    "start_attempt": "2026-11-20T17:29:32.524495Z",
    "complete_attempt": "2026-11-20T17:30:54.612004Z",
    "time_duration": 82087,                 // мс
    "percent_complete": 100,
    "person_id": "{person_id}",
    "max_score": 1,
    "got_score": 1,
    "amount_correct_answer": 1,
    "amount_partially_correct_answer": 0,
    "amount_wrong_answer": 0,
    "amount_waiting_review_answer": 0,
    "amount_missed_answer": 0
  },
  "results": [
    {
      "test_group_num": 0,
      "view_mode": "SINGLE_TASK",
      "challenge_tasks": [
        {
          "id": 802577977,
          "task_order": 0,
          "score": 1, "max_score": 1, "criteria_score": null,
          "status": "DONE",
          "result_impact": "INCLUDED",
          "question_elements": [ … ],         // как в 9.3
          "answer": { "type": "answer/single", "options": [ … ], "right_answer": { "id": null }, … },
          "user_answer": { "@answer_type": "answer/single", "id": "a9hdqt" },
          "answer_feedback": { "type": "JUST_CORRECTNESS", "correctness": "CORRECT" }
        }
      ]
    }
  ],
  "groups_context_info": {},
  "tasks_how_to_beat": {},
  "tasks_how_to_verify": {},
  "evaluation_criteria": []
}
```

- `answer_feedback.type: "JUST_CORRECTNESS"` — тест показывает только
  «верно/неверно» (`correctness`: `CORRECT` / … ), а правильный вариант
  (`right_answer`) и после завершения остаётся `null`.
- Остальные методы из кода SPA: `GET /challenge/{id}/assignments/my[?attemptId=]`,
  `GET /challenge/assignment/context/{id}/result`,
  `POST /challenge/task/answer/estimation` (ручная оценка учителем),
  `POST /challenge/generate_by/{task|spec|binding}` (создание испытания).

### 9.7. Тесты Библиотеки МЭШ и типы заданий

**Поиск тестов:** `POST https://uchebnik.mos.ru/search/api/v3/materials`
(`Authorization: Bearer {accessTokenEom}`, `Profile-Id`):

```json
{"query":{"search":"тест","filter":{"subject_program_ids":[31937343],"types":"test_specification"}},
 "sort":{"field":"score","order":"desc"},"scope":"catalogue","page":1,"per_page":10}
```

Ответ: `{data:[…], page:{size,total,number,total_entities}, spellcheck, facets}`.
Фильтр `types` почти не работает: в выдачу попадают и игры (`type: "GameApp"`),
поэтому тесты надо отбирать по `type == "TestSpecification"`. Подтип теста лежит
в `business_type`: `thematic_test`, `final_test`, `short_test`,
`test_specification`, `oge_test`, `olympiad_task`, `functional_grammar_test`.
Карточка теста: `GET /cms/api/v5/materials/{uuid}`. Списка заданий в ней нет.

**Запуск тренировки:** `GET /cms/materials/{uuid}/launch` →
`/launcher/api/v1/launch?activity_url=…&sgn=…` (SPA-страница) → редирект на
`/exam/challenge/{challenge_id}`. Для каждого запуска создаётся новый `challenge_id`.
Дальше всё как в 9.3–9.6, только `start-attempt` отправляется **без тела**:
такая попытка не привязана к ДЗ и в дневник не попадает.

**Важно:** `task/answer` возвращает `200 {"type":"EMPTY"}` на любое тело, даже
`{"foo":1}`. Правильный ли формат, видно только по `user_answer` и баллам в
результате после `complete_attempt`. В результате тренировки `right_answer` тоже
пустой.

#### Типы ответа

Всего в SPA 23 типа (`answer.type` = `user_answer["@answer_type"]`). `user_answer`
имеет ту же форму, что и `right_answer` (в коде есть
`convertToWithRightAnswer`). «Живой» — тип встретился в реальных тестах, и сервер
сохранил отправленный ответ в этом формате.

| Тип | Что в `answer` | `user_answer` | Проверено |
|---|---|---|---|
| `answer/single` | `options[{id,text,content,type:"option_type/default"}]`, `options_order` | `{"id":"<option>"}` | живой |
| `answer/multiple` | то же | `{"ids":["<option>",…]}` | живой |
| `answer/number` | — | `{"number":[20]}` | живой |
| `answer/string` | `right_answer.caseSensitive` | `{"string":["…"]}` | живой |
| `answer/order` | `options[]`, `direction: vertical/horizontal` | `{"ids_order":[все id по порядку]}` | живой |
| `answer/match` | `options[]` двух видов: `option_type/match/source` и `…/target`, `source_label`, `target_label`, `mix_source`, `mix_target` | `{"match":{"<source>":["<target>",…]}}` | живой |
| `answer/groups` | `options[]`: группы (`option_type/group`) и элементы (`option_type/default`), `mix_groups`, `mix_options` | `{"groups":[{"group_id":"…","options_ids":["…"]}]}` | живой |
| `answer/gap/text/input` | в `question_elements` есть `content/text/identificational` с `text_id`; `text_position[{position_id,text_id,position}]`, `expected_length`, `substitution_mode: copying`; в `right_answer.text_input_answer` приходит заготовка с пустыми `input_strings` | `{"text_input_answer":[{"position_id","text_id","caseSensitive":false,"input_strings":["…"],"group":0}]}` | живой |
| `answer/gap/match/text` | `text_position[]` + `options[]` (слова для пропусков), `substitution_mode: moving` | `{"text_position_answer":[…], "text_input_answer":[]}` | структура живая, формат элемента не снят |
| `answer/table` | `options[0].content[0]` = `content/table`: `table.cells["r"]["c"]=[текст]`, `rows`, `columns`, `rowHeaders`, `columnHeaders`; `cell_types` | `{"cells":{…}}` | структура живая, формат ячеек не снят |
| `answer/free` | `allow_upload_file_answer` | multipart: `request` + части `files` | структура живая, поля не сняты |
| `answer/string/multiple` | `text_input_rules` | `{"answers":[{"id","strings":[…],"num","group"}],"case_sensitive","with_order","quantity"}` | по коду |
| `answer/order/multiple` | `group_options[]` | `{"grouped_options":[{"order_group_id","ids_order":[…]}]}` | по коду |
| `answer/grid` | `cells[]` | `{"cells":[…]}` | по коду |
| `answer/inline/choice/single` | `text_position[]` | `{"text_position_answer":[…]}` | по коду |
| `answer/color/match`, `answer/gap/match/text/image`, `answer/text/line_highlighting/match` | `text_position[]` + цвета, картинки или стили | `{"text_position_answer":[…]}` | по коду |
| `answer/gap/match/background/image` | `background_image`, `image_gaps_positions[]` | `{"position_answer":[…]}` | по коду |
| `answer/gap/match/background/image/selection` | то же + `gaps_selection_mode` | `{"position_ids":[…]}` | по коду |
| `answer/match/rebus` | `option_positions`, `area_positions` | `{"match":[…]}` | по коду |
| `answer/match/timeline` | `options`: `option_type/match/timeline/date` и `…/event` | `{"match":{"<date>":["<event>"]}}` | по коду |
| `answer/number/interval` | `reference_right_answer.number` | `{"step":…}` (точный формат неизвестен) | по коду |

Типы содержимого в `question_elements` / `options[].content`: `content/text`,
`content/text/identificational` (текст с пропусками, `text_id`), `content/table`,
`content/math`, `content/link`, `content/atomic` (медиа: `image`, `sound`,
`video`, `file`). Справочник типов: `GET /webtests/exam/rest/secure/meta/answer_type`.

Задания в тесте разбиты на `challenge_test_groups`. В тренировках это группы по
2 задания, `view_mode: "SINGLE_TASK"`. Режимы оценивания из кода: `default`,
`single_penalty`, `multiple*`, `pair*`, `each_option`, `each_answer`, `manual`, `grid_row` и др.

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
| `registration` | UUID | ответ launch | LRS, плеер партнёра, `start-attempt.registrationParam`, xAPI `context.registration` |
| uchebnik `profileId` | 8 цифр | `session/v2/refresh` | `Profile-Id`, `Profile` на uchebnik |
| `challenge_attempt_id` | `409578240` | `start-attempt` | `task/answer`, `…/attempt/{id}/result`, `task/answers` |
| `challenge_task_id` | `802577977` | `start-attempt` → `challenge_tasks[].id` | `task/answer` |
| option `id` | `a9hdqt` | `answer.options[].id` | `task/answer` → `answer.id` |
| `scene_id` / `module` | `552388` | манифест курса | xAPI `module`, `object.id`, `journal2.results[].module` |
| `userLogin`, `sessionKey` | `…@school.imumk.ru`, 64 hex | HTML плеера (`var data`) | `uid`, подпись `key` |

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
6. Открытие материала «Облака Знаний» сразу запускает попытку. Если закрыть
   плеер, ничего не ответив, в LRS всё равно появляется завершённая
   (`final: true`) попытка с 0 баллов. Так произошло с двумя запусками при
   снятии этой документации.
7. У uchebnik.mos.ru свой токен и свой `Profile-Id` (`/acl/api/session/v2/refresh`);
   `aupd_token` дневника туда не подходит.
8. Тест МЭШ во время попытки не говорит, верен ли ответ (`{"type":"EMPTY"}`),
    и даже после завершения не раскрывает правильный вариант
    (`answer_feedback.type: JUST_CORRECTNESS`, `right_answer.id: null`).
9. В «Облаке Знаний», наоборот, ответ проверяет сам плеер, а на сервер
    уходит готовый вердикт (`result: "correct"`, `score`).
10. В LRS хранятся все попытки (`content[]`); какая из них идёт в оценку,
    решает дневник/учитель, API этого не показывает.
11. Статус «выполнено» по ЦДЗ на карточках веб берёт из LRS (`/api/lrs/v2/results`).
   В примере у ДЗ `is_done: false`, хотя один из трёх материалов уже выполнен:
   `is_done` — это галочка ДЗ целиком, а не статус ЦДЗ.
12. Веб кеширует ответы (React Query). Повторное открытие той же карточки
   может не дать сетевых запросов, и это нормально.
