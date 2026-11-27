# ЦДЗ на uchebnik.mos.ru — полный разбор API

Документ собран по двум HAR-файлам и одному стороннему разбору:

| Источник | Что даёт |
|---|---|
| `uchebnik.mos.ru_Archive [26-11-21 11-29-02].har` | полный цикл «открытие → старт → история → продолжение → ответ → завершение → результат» (1 задание) |
| `uchebnik.mos.ru_Archive [26-11-21 12-05-51].har` | реальный прогон ЦДЗ из 8 заданий: `answer/single` ×7 + `answer/match`, 10 ответов (в т.ч. переответы), полный результат |
| `mesh-ranker-app/docs/mesh-cdz-api.md` | только часть про `uchebnik.mos.ru`: страница `/exam/challenge/{id}`, запуск из дневника, `context`/`registration`, LRS-поля, заголовки (с оговорками — см. §13.9) |

**HAR #1** (`11:29-02`):

* браузер: Firefox 160, Linux
* страница: `https://uchebnik.mos.ru/exam/challenge/150779500?...`
* интервал: `2026-11-21 11:25:55 — 11:28:58` (+03:00)
* записей: 111, из них к ЦДЗ относятся 12

**HAR #2** (`12-05-51`):

* браузер: Firefox 160, Linux
* страница: `https://uchebnik.mos.ru/exam/challenge/152171589?...` (запись начата в середине: без `refresh`/`info`)
* интервал: `2026-11-21 11:51:18 — 12:05:48` (+03:00)
* записей: 111, из них к ЦДЗ относятся 13 (1 старт, 10 ответов, завершение, результат)

Документ описывает **только HTTP API `uchebnik.mos.ru`**. Всё, что относится к
`school.mos.ru` (список ДЗ, лаунчер, LRS-чтение), затрагивается лишь как контекст
(§15) — без разбора чужих эндпоинтов.

> В документе нет живых токенов, cookie и ссылок. Все секреты заменены на `<...>`.
> Числовые ID (challenge, attempt, task, context) оставлены как есть — это не секреты, а идентификаторы объекта.

---

## Содержание

1. [TL;DR — минимальная последовательность](#1-tldr--минимальная-последовательность)
2. [Идентификаторы (оба HAR)](#2-идентификаторы-оба-har)
3. [Авторизация](#3-авторизация)
4. [Открытие ЦДЗ](#4-открытие-цдз)
5. [История попыток](#5-история-попыток)
6. [Начать / продолжить попытку](#6-начать--продолжить-попытку)
7. [Ответ на задание](#7-ответ-на-задание)
8. [Завершение попытки](#8-завершение-попытки)
9. [Результат попытки](#9-результат-попытки)
10. [Сводная таблица эндпоинтов](#10-сводная-таблица-эндпоинтов)
11. [Хронология из HAR](#11-хронология-из-har-как-это-выглядит-вживую)
12. [Пример на Python](#12-пример-на-python-эскиз)
13. [Что НЕ видно в HAR (открытые вопросы)](#13-что-не-видно-в-har-открытые-вопросы)
14. [Безопасность и приватность](#14-безопасность-и-приватность)
15. [Связь с ДЗ, запуск и LRS (uchebnik-часть)](#15-связь-с-дз-запуск-и-lrs-uchebnik-часть)
16. [Данные из JS-бандла: типы ответов и скрытые методы](#16-данные-из-js-бандла-типы-ответов-и-скрытые-методы)


---

## 1. TL;DR — минимальная последовательность

```
0. (один раз / раз в ~15 мин)
   POST /acl/api/session/v2/refresh                 -> accessTokenEom + cookie сессии

1. Открытие ЦДЗ
   GET  /webtests/exam/rest/secure/auth/aclx/user_info
   GET  /webtests/exam/rest/secure/challenge/{challenge_id}/info

2. История попыток (кнопка «История»/«Результаты»)
   GET  /webtests/exam/rest/secure/challenge/{challenge_id}/assignments/my
   GET  /webtests/exam/rest/secure/challenge/assignment/context/{assignment_context_id}/result

3. Начать ИЛИ продолжить попытку (один и тот же вызов)
   POST /webtests/exam/rest/secure/challenge/{challenge_id}/start-attempt

4. Ответить на задание
   POST /webtests/exam/rest/secure/challenge/task/answer     (multipart/form-data)

5. Завершить попытку
   POST /webtests/exam/rest/secure/challenge/{challenge_id}/complete_attempt

6. Результат попытки
   GET  /webtests/exam/rest/secure/challenge/{challenge_id}/attempt/{attempt_id}/result
```

Все эндпоинты — `https://uchebnik.mos.ru` + путь. Все ответы — `200 OK` + JSON
(`Content-Type: application/json;charset=UTF-8`), кроме `refresh` (`application/json`).

---

## 2. Идентификаторы (оба HAR)

### 2.1. HAR #1 — одиночное задание (`challenge 150779500`)

| Имя | Значение | Откуда берётся |
|---|---|---|
| `challenge_id` | `150779500` | последний сегмент URL страницы `/exam/challenge/150779500` |
| `assignment_context_id` | `1646494213` | `assignments/my` → `assignment.assignment_context_id` |
| `attempt_id` | `409678691` | `start-attempt` → `challenge_attempt_id` |
| `challenge_task_id` | `802577977` | `start-attempt` → `challenge_test_groups[].challenge_tasks[].id` |
| `registration` | `38c4e62a-e7b3-41f4-8955-0958e19e3ced` | параметр `registration` URL страницы |
| `user_id` | `1002824909` | заголовок `User-Id`, `user_info.user_id`, `accessTokenEom.sub` |
| `profile_id` | `1002991417` | заголовок `Profile-Id`, `accessTokenEom.prf[0].id`, `Session-Eom-Profile` |
| `person_id` | `0345c036-12a8-4757-a00b-9555588736e1` | `user_info.person_id`, `accessTokenEom.prs`, `actor.account.name` |
| `internal_user_id` | `4726853` | `assignments/my` → `user_data.internal_user_id` |
| `rgn` | `77` | `accessTokenEom.rgn`, `user_info.rgn_id`, префикс `actor` |
| `activityId` | `https://uchebnik.mos.ru/exam/challenge/150779500` | параметр URL страницы |
| `wti` (в `context`) | `355554650` | `context.wti` = `work_id` = `homework_entry_id` (см. §15) |
| `mi` (в `context`) | `901cab69-5a5b-5312-a247-32ce3f38101a` | `context.mi` = `material_id` (UUID материала, см. §15) |

### 2.2. HAR #2 — 8 заданий (`challenge 152171589`)

| Имя | Значение | Откуда берётся |
|---|---|---|
| `challenge_id` | `152171589` | URL страницы `/exam/challenge/152171589` |
| `attempt_id` | `409688940` | `start-attempt` → `challenge_attempt_id` |
| `registration` | `be706690-ae6f-44af-af61-dba484d64fd6` | параметр `registration` URL страницы |
| `challenge_task_id` | `802613910` … `802613917` | `start-attempt` → `challenge_tasks[].id` (8 шт., `task_order` 0–7) |
| `wti` (в `context`) | `356825924` | `context.wti` = `homework_entry_id` |
| `mi` (в `context`) | `7c4c1990-ce43-598f-af29-aa99b2d7f105` | `context.mi` = `material_id` |
| `assignment_context_id` | — | в HAR #2 не запрашивался (`assignments/my` не вызывался) |
| `user_id` / `profile_id` / `person_id` / `rgn` | те же | те же заголовки и токен |

> Обратите внимание: `wti` в `context` — это **не** `challenge_id`, а `work_id`
> (`homework_entry_id`) из дневника. В двух разных ЦДЗ он разный (`355554650` / `356825924`),
> хотя `person_id` один и тот же.

---

## 3. Авторизация

### 3.1. Что используется

Два независимых слоя:

1. **Bearer-токен** `accessTokenEom` — JWT RS256, `iss = https://school.mos.ru`.
2. **Cookie-сессия** `Session-Id-External` + `Session-Eom-Profile` (ставится `refresh`-ем, живёт 7 дней),
   плюс обычные SSO-cookie МЭШ (`aupd_token`, `Ltpatoken2`, …).

Для API ЦДЗ критичен **Bearer**. Cookie нужны, потому что на них завязан `refresh`.
Токен хранится ещё и в `localStorage` как `auth_token`; точная схема получения и
заголовков разобрана по JS-бандлу в §16.1.

### 3.2. `accessTokenEom` (payload)

```json
{
  "prs": "0345c036-12a8-4757-a00b-9555588736e1",
  "sub": "1002824909",
  "nbf": 1795249594,
  "usr": "1002824909",
  "prf": [
    {
      "id": "1002991417",
      "type": "student",
      "aupdId": 1,
      "organizationIds": []
    }
  ],
  "kid": "5cb1e4867f60ede18322874cc89711dc50299ef45884da503cf9d854d36798bc",
  "iss": "https://school.mos.ru",
  "rgn": "77",
  "exp": 1795250494,
  "iat": 1795249594,
  "jti": "479669b2-fb01-45e0-8424-0ba6c9db8077"
}
```

| claim | смысл |
|---|---|
| `sub` / `usr` | `user_id` (ID пользователя school.mos.ru) |
| `prs` | `person_id` (UUID персоны) |
| `prf[]` | активные профили: `id` = `profile_id`, `type` = `student` / `teacher` / … |
| `aupdId` | 1 — признак профиля из АУПД (МЭШ) |
| `rgn` | код региона (`77` — Москва), используется в `actor.account.name` |
| `iss` | всегда `https://school.mos.ru` |
| `kid` | ID ключа подписи RS256 |
| TTL | `exp - iat = 900` сек (15 минут) |

Заголовок JWT: `{"alg":"RS256"}` (без `typ`).

### 3.3. `accessTokenAupd`

Отдельный JWT, `iss = https://school.mos.ru`, но это **не** токен для webtests API:

```json
{
  "sub": "4755386",
  "scp": "email phone profile oic_given_name oic_family_name oic_middle_name oic_birthdate",
  "prt": "library",
  "ath": "sudir",
  "msh": "0345c036-12a8-4757-a00b-9555588736e1",
  "rls": "{1:[20:2:[],30:4:[],40:1:[],...]}",
  "iss": "https://school.mos.ru",
  "exp": 1795335435,
  "iat": 1795249035
}
```

TTL — 24 часа. Используется библиотекой/другими сервисами МЭШ, для ЦДЗ не нужен.
То же значение лежит в cookie `aupd_token` (TTL 24 ч).

### 3.4. Cookie-сессия

`POST /acl/api/session/v2/refresh` возвращает два `Set-Cookie`:

```
Session-Id-External=<JWT>; Path=/; Max-Age=604800; Expires=...; Secure; HttpOnly
Session-Eom-Profile=1002991417; Path=/; Max-Age=604800; Expires=...; Secure; HttpOnly
```

`Session-Id-External` — JWT RS256 c «зашифрованным» `sub`:

```json
{
  "sub": "w5m81U6uryeRKAXBdaWYp0HdlMNsnjnumnugV4/ANipmbI9OAdh4+iq1q3BZb93DXPkd0D+BAVZA/qe9DRjDWKYr7KccVyRGNF8=",
  "nbf": 1795249594,
  "exp": 1795854394,
  "iat": 1795249594,
  "jti": "6e79c0be-279d-42d0-9419-de916382f6e4"
}
```

TTL — 7 суток. `Session-Eom-Profile` — просто `profile_id`.

### 3.5. `POST https://uchebnik.mos.ru/acl/api/session/v2/refresh`

Вызывается фронтом при каждой загрузке страницы ЦДЗ (в HAR — 3 раза: 11:26:34, 11:27:59, 11:28:35).
Обновляет `accessTokenEom` (и заодно cookie сессии), поэтому его надо звать перед длинной работой:
токен живёт 15 минут.

**Запрос**

```http
POST /acl/api/session/v2/refresh HTTP/1.1
Host: uchebnik.mos.ru
Accept: application/json;charset=UTF-8
Content-Type: application/json
User-Id: 1002824909
Profile-Id: 1002991417
Profile: {"id":"1002991417","type":"student"}
Authorization: Bearer <accessTokenEom>
X-Mes-Subsystem: testw
Origin: https://uchebnik.mos.ru
Referer: https://uchebnik.mos.ru/exam/challenge/150779500?<полный query страницы>
Cookie: <весь cookie-jar>
```

Тело — **литерал JSON `null`** (4 байта):

```json
null
```

**Ответ `200`**

```json
{
  "accessTokenEom": "<JWT RS256, TTL 15 мин>",
  "accessTokenAupd": "<JWT, TTL 24 ч>",
  "profileId": "1002991417"
}
```

Плюс два `Set-Cookie` (см. выше).

**Замечания**

* Токен в `Authorization` — это *предыдущий* `accessTokenEom`; тело от него не зависит.
* Источник самого первого `accessTokenEom` в этом HAR не виден: страница открыта уже с токеном
  (он получен на `school.mos.ru` до записи). Дальше токен продлевается только этим вызовом.
* `User-Id` / `Profile-Id` / `Profile` дублируют содержимое токена; сервер сверяет их с `sub` / `prf`.
* В ответе нет `expires_in` — срок считается из `exp` JWT.

### 3.6. Обязательные заголовки всех запросов к `/webtests/...`

```http
Accept: application/json;charset=UTF-8
Content-Type: application/json          # даже у GET, тело пустое
User-Id: 1002824909
Profile-Id: 1002991417
Profile: {"id":"1002991417","type":"student"}
Authorization: Bearer <accessTokenEom>
X-Mes-Subsystem: testw
Origin: https://uchebnik.mos.ru
Referer: https://uchebnik.mos.ru/exam/challenge/150779500?<query страницы>
Cookie: <cookie-jar>
```

`Sec-Fetch-Dest: empty`, `Sec-Fetch-Mode: cors`, `Sec-Fetch-Site: same-origin` — браузерные,
для не-браузерного клиента не обязательны (но безопасно эмулировать).

`X-Mes-Subsystem: testw` — идентификатор подсистемы («тесты»). Присутствует во всех запросах ЦДЗ.

Cookie, которые реально уходят на API (набор из HAR):

```
uwyii, auth_flag, Ltpatoken2, sbp_sid, ghur, aupd_token, user_login, obr_id,
subsystem_id, wc_h_chat_connect_requested, Session-Id-External,
Session-Eom-Profile, uwyiert, _cookieTabId, oxxfgh
```

Из них для API существенны `aupd_token`, `Session-Id-External`, `Session-Eom-Profile`;
остальные — SSO/аналитика/UI. Практический минимум для запроса — `Authorization` + cookie сессии.

### 3.7. Ошибки авторизации

В HAR ошибок нет (все ответы `200`). Точный формат `401/403` не зафиксирован.
Практика МЭШ: `401` — истёк/неверен Bearer (лечится `refresh`), `403` — нет прав на профиль/объект.
Формат тела ошибки в этом HAR не наблюдался — не выдумываем.

---

## 4. Открытие ЦДЗ

### 4.1. Страница

```
GET https://uchebnik.mos.ru/exam/challenge/{challenge_id}?<параметры>
```

Это SPA-страница (`exam-front`), отдаёт HTML (`Content-Type: text/html`, gzip).
После загрузки JS сам делает `refresh`, `user_info`, `info`.

**Параметры URL** (порядок как в HAR):

| Параметр | Пример | Смысл |
|---|---|---|
| `activityId` | `https://uchebnik.mos.ru/exam/challenge/150779500` | канонический URL активности (совпадает с текущим) |
| `actor` | `{"objectType":"Agent","account":{"name":"77:0345c036-…"}}` | JSON-строка: кто выполняет. `name = "{rgn}:{person_id}"` |
| `authurl` | `https://school.mos.ru` | куда идти за SSO, если сессии нет |
| `context` | `<JWT HS256>` | контекст запуска, выдаётся `school.mos.ru` (см. ниже) |
| `endpoint` | `https://uchebnik.mos.ru/lrs-dhw/` | LRS-endpoint (хранилище результатов) |
| `fetch` | `https://uchebnik.mos.ru/lrs-dhw/token/fetch/b57c194fa7c2af0a327d1501e5fd0a7d` | URL получения LRS-токена (в HAR не вызывался) |
| `registration` | `38c4e62a-e7b3-41f4-8955-0958e19e3ced` | ID регистрации «ученик × задание»; идёт в `start-attempt` |
| `role` | `student` | роль |
| `utm_campaign` | `1` | аналитика |
| `utm_medium` | `lesson` | аналитика |
| `utm_source` | `familyw` | аналитика |

`activityId`, `actor`, `authurl`, `context`, `endpoint`, `fetch`, `registration`, `role` — рабочие;
`utm_*` — только метрика.

**Referer страницы** — лаунчер:

```
https://uchebnik.mos.ru/launcher/api/v1/launch?activityId=...&activity_url=...&actor=...&context=...&elt=JvB9fQGtFZiZpTqETutxzJ-7QSM&endpoint=...&fetch=...&registration=...&role=student&utm_...
```

`elt` есть только в referer лаунчера (одноразовый токен запуска), в URL страницы его нет.

### 4.2. `context` (JWT HS256)

Заголовок:

```json
{"alg":"HS256"}
```

Payload:

```json
{
  "wt": "1",
  "wti": 355554650,
  "pid": "0345c036-12a8-4757-a00b-9555588736e1",
  "dt": 1795249507718,
  "mi": "901cab69-5a5b-5312-a247-32ce3f38101a"
}
```

| Поле | Смысл | Подтверждение |
|---|---|---|
| `wt` | `work_type`, строка `"1"` = домашнее задание | `mesh-cdz-api.md` §7.1 |
| `wti` | `work_id` = **`homework_entry_id`** из дневника (не `challenge_id`!) | `mesh-cdz-api.md` §7.1; в двух HAR значения `355554650` / `356825924` при разных `challenge_id` |
| `pid` | `person_id` | оба HAR + `mesh-cdz-api.md` |
| `dt` | время выдачи, epoch **миллисекунды** | оба HAR |
| `mi` | `material_id` — UUID материала ЦДЗ в ДЗ | `mesh-cdz-api.md` §7.1; в HAR #2 `7c4c1990-…` |

Дополнительно из `mesh-cdz-api.md` §7.1: `registration` — **новый UUID на каждый запуск**
материала; именно он потом попадает в LRS и в `start-attempt` как `registrationParam`.

Подпись HS256 проверяется сервером. Этот токен **нельзя** пересоздать без ключа —
его нужно брать из URL страницы и передавать в `start-attempt` как есть.

Второй пример payload (HAR #2):

```json
{
  "wt": "1",
  "wti": 356825924,
  "pid": "0345c036-12a8-4757-a00b-9555588736e1",
  "dt": 1795251057944,
  "mi": "7c4c1990-ce43-598f-af29-aa99b2d7f105"
}
```

`dt = 1795251057944` → `2026-11-21T08:50:57.944Z`; `start-attempt` прошёл в `08:51:22.462Z`,
т.е. между выдачей `context` и стартом попытки было ~25 секунд.

### 4.3. `GET /webtests/exam/rest/secure/auth/aclx/user_info`

Кто я. Вызывается сразу после `refresh`, до `info`.

**Запрос:** заголовки из §3.6, тела нет.

**Ответ `200`**

```json
{
  "user_id": 1002824909,
  "user_name": "Боровский Рукие Даниил",
  "staff_id": null,
  "person_id": "0345c036-12a8-4757-a00b-9555588736e1",
  "rgn_id": "77",
  "user_profiles": [
    {
      "id": "1002991417",
      "profile_name": "Ученик",
      "profile_original_name": "student"
    }
  ]
}
```

| Поле | Смысл |
|---|---|
| `user_id` | ID пользователя (совпадает с `sub` токена) |
| `user_name` | ФИО |
| `staff_id` | ID сотрудника или `null` (ученик — `null`) |
| `person_id` | UUID персоны |
| `rgn_id` | регион строкой |
| `user_profiles[]` | доступные профили: `id`, отображаемое имя, системное имя |

### 4.4. `GET /webtests/exam/rest/secure/challenge/{challenge_id}/info`

Метаданные ЦДЗ. Главный вызов для «открытия»: по нему фронт решает,
показывать ли «Начать», «Продолжить», «Историю».

**Запрос:** заголовки §3.6, тела нет.

**Ответ `200`**

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
  "number_of_attempt": 0,
  "last_attempt_id": null,
  "last_attempt_started_at": null,
  "attempt_time_limit_type": "FIXED"
}
```

| Поле | Тип | Смысл |
|---|---|---|
| `title` | string | заголовок |
| `subject_ids` | int[] | ID предметов |
| `use_case` | enum | `HOMEWORK` (ЦДЗ), также бывают `TEST`/`OLYMPIAD` и т.п. |
| `allowed_tries` | int | лимит попыток; `0` — «не ограничено/не задано» |
| `available_time_in_minutes` | int | лимит времени на попытку в минутах; `0` — нет лимита |
| `task_total_count` | int | число заданий |
| `available_from` / `available_until` | ISO-8601 UTC | окно доступности; здесь «бесконечное» |
| `can_reanswer` | bool | можно ли переотвечать |
| `answer_order_random` | bool | перемешивать варианты ответа |
| `is_task_order_random` | bool | перемешивать задания |
| `number_of_attempt` | int | **сколько попыток уже начато** (включая незавершённую) |
| `last_attempt_id` | int \| null | ID **незавершённой** попытки; `null` — незавершённых нет |
| `last_attempt_started_at` | ISO \| null | время старта незавершённой попытки |
| `attempt_time_limit_type` | enum | `FIXED` — фиксированное время (см. `available_time_in_minutes`) |

**Как читать (проверено по HAR):**

| Момент | `number_of_attempt` | `last_attempt_id` | `last_attempt_started_at` |
|---|---|---|---|
| до первой попытки (11:26:35) | `0` | `null` | `null` |
| попытка идёт (11:28:00) | `1` | `409678691` | `2026-11-21T08:27:11.251813Z` |
| попытка завершена (11:28:36) | `1` | `null` | `null` |

То есть:

* есть незавершённая попытка ⇔ `last_attempt_id != null` → показываем **«Продолжить»**;
* иначе, если `can_reanswer`/лимиты позволяют → **«Начать»**;
* `number_of_attempt` — счётчик всех начатых попыток.

### 4.5. `GET /exam/config.json` (необязательно)

Публичный конфиг фронта (кэшируется, в HAR — `304`). Полезные константы:

```json
{
  "REST_SUPPORT_URL": "/webtests/exam/rest",
  "BASE_API_URL": "/webtests/exam",
  "REST_SECURE_URL": "/webtests/exam/rest/secure",
  "CHALLENGE_PLAYER_URL": "/exam/challenge/",
  "REVIEW_CHALLENGE_PLAYER_URL": "/exam/challenge/{challenge_id}/context/{assigment_context_id}",
  "IS_AUTOSAVE_ENABLED": true,
  "USE_NEW_LRS_API": true,
  "LRS_EXCLUDE_FETCH_TOKEN_VALIDATION": true
}
```

Все боевые вызовы строятся от `REST_SECURE_URL = /webtests/exam/rest/secure`.

---

## 5. История попыток

История — это два запроса подряд: сначала узнать `assignment_context_id`, потом список попыток.

### 5.1. `GET /webtests/exam/rest/secure/challenge/{challenge_id}/assignments/my`

**Запрос:** заголовки §3.6, тела нет.

**Ответ `200`**

```json
{
  "user_data": {
    "internal_user_id": 4726853,
    "full_name": "",
    "staff_id": null,
    "person_id": "0345c036-12a8-4757-a00b-9555588736e1",
    "profiles": [
      {
        "profileId": 1002991417,
        "profileType": "student"
      }
    ]
  },
  "assignment": {
    "assignment_context_id": 1646494213,
    "url": "https://uchebnik.mos.ru/exam/challenge/150779500/context/1646494213"
  }
}
```

| Поле | Смысл |
|---|---|
| `user_data.internal_user_id` | внутренний ID пользователя в сервисе тестов (**не** равен `user_id`) |
| `user_data.full_name` | ФИО (здесь пустая строка) |
| `user_data.staff_id` | `null` для ученика |
| `user_data.person_id` | UUID персоны |
| `user_data.profiles[]` | `profileId` (число) + `profileType` (`student`) |
| `assignment.assignment_context_id` | **ID контекста назначения** — ключ к истории и к ссылкам `resultManagementUrl` |
| `assignment.url` | URL страницы «контекст»: `/exam/challenge/{challenge_id}/context/{assignment_context_id}` |

Если у пользователя нет назначения, вероятен `assignment: null` (в HAR не наблюдалось).

### 5.2. `GET /webtests/exam/rest/secure/challenge/assignment/context/{assignment_context_id}/result`

Список попыток (**история**). Вызывается после `assignments/my` (и после завершения попытки — тоже).

**Запрос:** заголовки §3.6, тела нет.

**Ответ `200` — до завершения попытки (11:28:04)**

```json
{
  "challenge_id": 150779500,
  "user_results": [
    {
      "user_data": {
        "internal_user_id": 4726853,
        "full_name": "",
        "staff_id": null,
        "person_id": "0345c036-12a8-4757-a00b-9555588736e1",
        "profiles": [
          { "profileId": 1002991417, "profileType": "student" }
        ]
      },
      "assignment_context_id": 1646494213,
      "max_amount_attempt": 0,
      "used_amount_attempt": 0,
      "attempts": []
    }
  ]
}
```

**Ответ `200` — после завершения (11:28:38)**

```json
{
  "challenge_id": 150779500,
  "user_results": [
    {
      "user_data": {
        "internal_user_id": 4726853,
        "full_name": "",
        "staff_id": null,
        "person_id": "0345c036-12a8-4757-a00b-9555588736e1",
        "profiles": [
          { "profileId": 1002991417, "profileType": "student" }
        ]
      },
      "assignment_context_id": 1646494213,
      "max_amount_attempt": 0,
      "used_amount_attempt": 1,
      "attempts": [
        {
          "attempt_id": 409678691,
          "attempt_num": 1,
          "start_attempt": "2026-11-21T08:27:11.251813Z",
          "complete_attempt": "2026-11-21T08:28:21.977349Z",
          "time_duration": 70725,
          "percent_complete": 100.0,
          "person_id": "0345c036-12a8-4757-a00b-9555588736e1",
          "max_score": 1,
          "got_score": 1,
          "amount_correct_answer": 1,
          "amount_partially_correct_answer": 0,
          "amount_wrong_answer": 0,
          "amount_waiting_review_answer": 0,
          "amount_missed_answer": 0
        }
      ]
    }
  ]
}
```

| Поле | Смысл |
|---|---|
| `challenge_id` | ID ЦДЗ |
| `user_results[]` | по элементу на пользователя (учитель увидит несколько) |
| `user_results[].max_amount_attempt` | максимум попыток; `0` — без ограничения/не задано |
| `user_results[].used_amount_attempt` | сколько попыток **завершено** |
| `user_results[].attempts[]` | только **завершённые** попытки (незавершённая сюда не попадает) |
| `attempts[].attempt_id` | ID попытки |
| `attempts[].attempt_num` | номер попытки (с 1) |
| `attempts[].start_attempt` | старт, ISO-8601 UTC (`Z`) |
| `attempts[].complete_attempt` | завершение, ISO-8601 UTC |
| `attempts[].time_duration` | длительность в **миллисекундах** |
| `attempts[].percent_complete` | процент выполнения (float) |
| `attempts[].person_id` | UUID персоны |
| `attempts[].max_score` | максимум баллов |
| `attempts[].got_score` | набрано баллов |
| `amount_correct_answer` | число верных |
| `amount_partially_correct_answer` | частично верных |
| `amount_wrong_answer` | неверных |
| `amount_waiting_review_answer` | ждут проверки учителем |
| `amount_missed_answer` | пропущено |

**Важно:** объект попытки в истории **идентичен** `attempt_info` из
`GET .../attempt/{id}/result` (см. §9). Это позволяет рисовать историю без N+1 запросов,
а детали подтягивать только по клику.

---

## 6. Начать / продолжить попытку

### 6.1. `POST /webtests/exam/rest/secure/challenge/{challenge_id}/start-attempt`

**Один и тот же запрос для «Начать» и «Продолжить».**
Разницы в теле и URL нет. Если незавершённая попытка существует (`info.last_attempt_id != null`),
сервер вернёт **ту же** попытку с её `challenge_attempt_started_at` и уже сохранёнными ответами;
иначе создаст новую. Это и есть «продолжить попытку».

**Запрос**

```http
POST /webtests/exam/rest/secure/challenge/150779500/start-attempt HTTP/1.1
Host: uchebnik.mos.ru
Accept: application/json;charset=UTF-8
Content-Type: application/json
User-Id: 1002824909
Profile-Id: 1002991417
Profile: {"id":"1002991417","type":"student"}
Authorization: Bearer <accessTokenEom>
X-Mes-Subsystem: testw
Origin: https://uchebnik.mos.ru
Referer: https://uchebnik.mos.ru/exam/challenge/150779500?<query страницы>
Cookie: <cookie-jar>
```

**Тело**

```json
{
  "actorParam": "{\"objectType\":\"Agent\",\"account\":{\"name\":\"77:0345c036-12a8-4757-a00b-9555588736e1\"}}",
  "registrationParam": "38c4e62a-e7b3-41f4-8955-0958e19e3ced",
  "activityIdParam": "https://uchebnik.mos.ru/exam/challenge/150779500",
  "resultOverviewUrl": "https://uchebnik.mos.ru/exam/challenge/150779500/attempt/{attemptId}",
  "resultManagementUrl": "https://uchebnik.mos.ru/exam/challenge/150779500/context/{assignmentContextId}/attempt/{attemptId}",
  "context": "<JWT HS256 из URL страницы>",
  "contextCorrupted": false
}
```

| Поле | Тип | Источник | Смысл |
|---|---|---|---|
| `actorParam` | **string** (JSON внутри строки) | параметр `actor` URL | дублируется строкой, а не объектом — важно |
| `registrationParam` | string | параметр `registration` URL | ID регистрации |
| `activityIdParam` | string | параметр `activityId` URL | канонический URL активности |
| `resultOverviewUrl` | string | шаблон фронта | ссылка на результат попытки; `{attemptId}` подставляет сервер/фронт |
| `resultManagementUrl` | string | шаблон фронта | ссылка управления попыткой в контексте; `{assignmentContextId}`, `{attemptId}` |
| `context` | string (JWT) | параметр `context` URL | HS256-контекст запуска, verbatim |
| `contextCorrupted` | bool | `false` | флаг «контекст битый»; всегда `false` в норме |

* `actorParam` — именно строка: `"{\"objectType\":\"Agent\",...}"`. Внутри:
  `objectType = "Agent"`, `account.name = "{rgn}:{person_id}"`.
* `resultOverviewUrl` / `resultManagementUrl` — это маршруты SPA, которые потом используются
  в отчётах/LRS. В HAR они ровно такие, с плейсхолдерами в фигурных скобках.

**Ответ `200` (новая попытка)**

```json
{
  "challenge_test_groups": [
    {
      "test_group_num": 0,
      "challenge_tasks": [
        {
          "id": 802577977,
          "task_order": 0,
          "question_elements": [
            {
              "text_styles": [],
              "block_styles": [],
              "content": [],
              "text": "Основными компонентами воздуха являются",
              "type": "content/text"
            }
          ],
          "answer": {
            "options": [
              { "id": "a9hdqt", "text": "кислород и азот",   "content": [], "text_styles": [], "block_styles": [], "type": "option_type/default" },
              { "id": "q85i22", "text": "озон и кислород",    "content": [], "text_styles": [], "block_styles": [], "type": "option_type/default" },
              { "id": "xp5e7i", "text": "кислород и гелий",   "content": [], "text_styles": [], "block_styles": [], "type": "option_type/default" },
              { "id": "xsuoum", "text": "азот и водород",     "content": [], "text_styles": [], "block_styles": [], "type": "option_type/default" }
            ],
            "right_answer": { "id": null },
            "options_order": "random",
            "reference_right_answer": null,
            "type": "answer/single",
            "text_input_rules": null
          },
          "user_answer": null,
          "answer_feedback": null
        }
      ],
      "view_mode": "SINGLE_TASK"
    }
  ],
  "challenge_attempt_id": 409678691,
  "groups_context_info": {},
  "tasks_how_to_beat": {},
  "challenge_attempt_started_at": "2026-11-21T08:27:11.251813Z",
  "current_date_time": "2026-11-21T08:27:11.259609Z"
}
```

**Разбор полей**

| Поле | Смысл |
|---|---|
| `challenge_test_groups[]` | группы заданий (в ЦДЗ обычно одна, `test_group_num: 0`) |
| `challenge_test_groups[].challenge_tasks[]` | задания |
| `...challenge_tasks[].id` | `challenge_task_id` — нужен для ответа |
| `...challenge_tasks[].task_order` | порядок задания |
| `...challenge_tasks[].question_elements[]` | блоки условия: `type: "content/text"`, `text`, оформление (`text_styles`, `block_styles`, `content`) |
| `...challenge_tasks[].answer` | описание типа ответа и вариантов |
| `answer.type` | тип ответа: `answer/single` (один вариант) |
| `answer.options[]` | варианты: `id` (короткая строка, напр. `a9hdqt`), `text`, `type: "option_type/default"` |
| `answer.right_answer` | правильный ответ; в режиме прохождения `{"id": null}` (скрыт) |
| `answer.options_order` | `random` / `fixed` — порядок показа |
| `answer.reference_right_answer` | эталонный ответ (обычно `null` до проверки) |
| `answer.text_input_rules` | правила текстового ввода или `null` |
| `...user_answer` | уже сохранённый ответ (при продолжении); иначе `null` |
| `...answer_feedback` | фидбек (при продолжении/после проверки); иначе `null` |
| `...view_mode` | `SINGLE_TASK` — показывать по одному заданию |
| `challenge_attempt_id` | ID попытки → `attempt_id` |
| `groups_context_info` | доп. контекст групп (обычно `{}`) |
| `tasks_how_to_beat` | подсказки «как пройти» (обычно `{}`) |
| `challenge_attempt_started_at` | старт попытки, ISO-8601 UTC |
| `current_date_time` | серверное «сейчас», ISO-8601 UTC — для расчёта таймера |

**Ответ при «Продолжить»** — структура та же. В HAR (11:28:08) отличалось только
`current_date_time`, а `challenge_attempt_id` / `challenge_attempt_started_at` / задания
совпали с первым вызовом (11:27:11):

```
start #1: attempt_id=409678691, started_at=2026-11-21T08:27:11.251813Z
start #2: attempt_id=409678691, started_at=2026-11-21T08:27:11.251813Z  <-- та же попытка
```

### 6.2. Практические замечания

* Тело `start-attempt` **не содержит** ни `challenge_id`, ни `attempt_id` — только путь и `context`.
* Если `context` протух/подменён, сервер вернёт ошибку (формат не зафиксирован).
* `contextCorrupted: true` фронт выставляет, если не смог распарсить `context`; тогда ожидается
  ветка «начать заново». В норме всегда `false`.
* Повторный вызов после завершения попытки создаст **новую** попытку (если разрешено).
* Ответ содержит правильные ответы в скрытом виде (`right_answer.id = null`) — не пытайтесь
  вытащить их из `start-attempt`; после завершения они тоже не раскрываются
  (`right_answer.id` остаётся `null`, см. §9).
* «Продолжить» после перезагрузки страницы на сервере неотличимо от «начать»: тот же вызов,
  то же тело (в HAR #1 тело совпало байт-в-байт), отличается только `current_date_time`
  в ответе.

### 6.3. Многозадачный ЦДЗ (HAR #2)

В HAR #2 один `start-attempt` вернул **8 заданий** в одной группе:

```
challenge_attempt_id = 409688940
challenge_attempt_started_at = 2026-11-21T08:51:22.462887Z
challenge_test_groups[0].test_group_num = 0
challenge_test_groups[0].view_mode = "SINGLE_TASK"
challenge_test_groups[0].challenge_tasks[].task_order = 0..7
```

Задания:

| `id` | `task_order` | `answer.type` | Вариантов |
|---|---|---|---|
| `802613910` | 0 | `answer/single` | 4 |
| `802613911` | 1 | `answer/single` | 4 |
| `802613912` | 2 | `answer/single` | 4 |
| `802613913` | 3 | `answer/single` | 4 |
| `802613914` | 4 | `answer/single` | 4 |
| `802613915` | 5 | `answer/single` | 4 |
| `802613916` | 6 | `answer/single` | 4 |
| `802613917` | 7 | `answer/match` | 8 (4 source + 4 target) |

`groups_context_info`, `tasks_how_to_beat` — пустые (`{}`), как и в HAR #1.
`current_date_time` = `2026-11-21T08:51:22.467821Z` (на 5 мс позже старта).

> В HAR #1 `challenge_task_id` был коротким числом, а `id` вариантов — 6-символьными
> строками (`a9hdqt`). В HAR #2 `id` вариантов — **UUID** (`2255d8aa-0400-41e1-…`).
> Формат `id` не фиксирован: всегда используйте значение как opaque string.

---

## 7. Ответ на задание

### 7.1. `POST /webtests/exam/rest/secure/challenge/task/answer`

Важно: путь **без** `challenge_id`.

**Запрос — `multipart/form-data`**, одна часть:

```
Content-Type: multipart/form-data; boundary=----geckoformboundary6af25484417940724f0a4f1ad16336a9

------geckoformboundary6af25484417940724f0a4f1ad16336a9
Content-Disposition: form-data; name="request"; filename="blob"
Content-Type: application/json

{"challenge_task_id":802577977,"challenge_attempt_id":409678691,"answer":{"@answer_type":"answer/single","id":"a9hdqt"}}
------geckoformboundary6af25484417940724f0a4f1ad16336a9--
```

Параметры части:

| Что | Значение |
|---|---|
| `name` | `request` |
| `filename` | `blob` (обязательно: именно файл, а не обычное поле) |
| `Content-Type` части | `application/json` |
| тело части | компактный JSON (без переносов) |

Заголовки запроса — §3.6, плюс:

```http
Content-Type: multipart/form-data; boundary=<...>
Origin: https://uchebnik.mos.ru
```

`Content-Length` в HAR — `337` (для этого ответа).

**JSON внутри части**

```json
{
  "challenge_task_id": 802577977,
  "challenge_attempt_id": 409678691,
  "answer": {
    "@answer_type": "answer/single",
    "id": "a9hdqt"
  }
}
```

| Поле | Тип | Смысл |
|---|---|---|
| `challenge_task_id` | int | `id` задания из `start-attempt` |
| `challenge_attempt_id` | int | `challenge_attempt_id` из `start-attempt` |
| `answer` | object | сам ответ |
| `answer.@answer_type` | enum | тип ответа, **повторяет** `answer.type` из задания (`answer/single`) |
| `answer.id` | string | `id` выбранного варианта из `answer.options[]` |

Для `answer/single` — ровно `{"@answer_type":"answer/single","id":"<option_id>"}`.

**Ответ `200`**

```json
{"type":"EMPTY"}
```

`type` — тип фидбека/результата проверки. `EMPTY` = «принято, немедленного фидбека нет»
(типично для ЦДЗ, где проверка идёт после завершения). В обоих HAR **все 11 ответов**
вернули ровно `{"type":"EMPTY"}` — и верные, и неверные, и повторные.

Фронт при `IS_AUTOSAVE_ENABLED: true` может звать этот эндпоинт несколько раз
(автосохранение при переключении задания); последний ответ по заданию перезаписывает предыдущий.

Полный перечень типов заданий и форматов `user_answer` для них — в §16.2 / §16.3
(всего 23 типа в бандле; в HAR встречались только `single` и `match`).

### 7.2. `answer/match` — сопоставление (HAR #2)

Задание-сопоставление (`task 802613917`) устроено иначе: `options[]` содержит два вида
элементов, которые надо связать.

```json
{
  "id": 802613917,
  "task_order": 7,
  "question_elements": [
    { "text_styles": [], "block_styles": [], "content": [],
      "text": "Соотнесите название общевоинского устава с его назначением.",
      "type": "content/text" }
  ],
  "answer": {
    "options": [
      {
        "id": "a21e4f2d-ff6b-45b0-9815-7280bdd0d9ff",
        "text": "Устав внутренней службы",
        "content": [
          {
            "description": "Устав внутренней службы",
            "type": "content/atomic",
            "atomic_id": "13526746",
            "relative_url": "https://uchebnik.mos.ru/cms/system/atomic_objects/files/013/526/746/original/uvs-1.jpg",
            "preview_url": "https://uchebnik.mos.ru/cms/system/atomic_objects/files/013/526/746/icon/uvs-1.jpg",
            "atomic_type": "image"
          }
        ],
        "text_styles": [],
        "block_styles": [],
        "type": "option_type/match/source"
      },
      {
        "id": "1b807e07-0d33-4b58-b155-45541a6ef6d7",
        "text": "Определяет общие права и обязанности военнослужащих …",
        "content": [],
        "text_styles": [],
        "block_styles": [],
        "type": "option_type/match/target"
      }
    ],
    "right_answer": { "match": {} },
    "options_order": "none",
    "initial_options_order": [],
    "mix_source": false,
    "mix_target": false,
    "reference_right_answer": null,
    "source_label": "",
    "target_label": "",
    "type": "answer/match",
    "text_input_rules": null
  },
  "user_answer": null,
  "answer_feedback": null
}
```

| Поле | Смысл |
|---|---|
| `answer.type` | `answer/match` |
| `options[].type` | `option_type/match/source` (левая колонка) или `option_type/match/target` (правая) |
| `options[].content[]` | вложения: `type: "content/atomic"`, `atomic_type: "image"`, `atomic_id`, `relative_url`, `preview_url`, `description` |
| `right_answer` | `{"match": {}}` — пары скрыты (в отличие от `answer/single`, где `{"id": null}`) |
| `options_order` | `"none"` — порядок не перемешивается |
| `initial_options_order` | `[]` — исходный порядок (пусто) |
| `mix_source` / `mix_target` | перемешивать левую/правую колонки |
| `source_label` / `target_label` | подписи колонок (здесь пустые) |

**Тело ответа** (тот же multipart, часть `request` / `blob`):

```json
{
  "challenge_task_id": 802613917,
  "challenge_attempt_id": 409688940,
  "answer": {
    "@answer_type": "answer/match",
    "match": {
      "ed3669ff-f46e-49c3-847d-2afd66803c22": ["b40795d2-5234-4abe-ac20-153c3a5e001f"],
      "a21e4f2d-ff6b-45b0-9815-7280bdd0d9ff": ["1b807e07-0d33-4b58-b155-45541a6ef6d7"],
      "c889b930-fe0a-4bd5-a120-9f9405ef3d3f": ["56e0db88-d6ac-4371-b001-b3f9bfd5f123"],
      "ca4a51ff-4f5d-49e0-b68b-6c9a263aa076": ["4e2c3324-f375-4db2-89aa-d66ae751f480"]
    }
  }
}
```

Схема:

```
"answer": {
  "@answer_type": "answer/match",
  "match": {
    "<id опции с type=option_type/match/source>": [
      "<id опции с type=option_type/match/target>"
    ],
    ...
  }
}
```

* Ключ — **source**, значение — **массив target** (массив, а не строка; допускает
  несколько target на один source).
* В примере — 4 source и 4 target, все связи «один-к-одному».
* Ответ снова `{"type":"EMPTY"}`.

### 7.3. Повторные ответы и «последний побеждает»

В HAR #2 задание `802613910` отвечено **три раза** (11:55:30, 11:55:51, 11:55:54):

```
1) id = 2255d8aa-…  (первый вариант)
2) id = cbe2b49d-…  (другой вариант)
3) id = 2255d8aa-…  (снова первый)
```

Все три вернули `{"type":"EMPTY"}`, а в итоговом результате сохранён **последний** ответ
(`2255d8aa-…`) и он засчитан как `CORRECT`.

Отсюда практика:

* отдельного «удалить ответ» нет — перезапись тем же эндпоинтом;
* можно смело отправлять ответ несколько раз (автосейв, смена варианта);
* финальное состояние фиксируется на `complete_attempt`.

---

## 8. Завершение попытки

### 8.1. `POST /webtests/exam/rest/secure/challenge/{challenge_id}/complete_attempt`

**Запрос**

```http
POST /webtests/exam/rest/secure/challenge/150779500/complete_attempt HTTP/1.1
Content-Type: application/json
...заголовки §3.6...
```

**Тело**

```json
{"challenge_id":150779500}
```

| Поле | Тип | Смысл |
|---|---|---|
| `challenge_id` | int | ID ЦДЗ (дублирует путь) |

**Ответ `200`**

```json
{}
```

Пустой объект. Никаких баллов/статусов здесь нет — результат забирается отдельным вызовом.

В HAR #2 тело было таким же по форме, с другим ID и без иных полей:

```json
{"challenge_id":152171589}
```

→ `200`, тело `{}`.

### 8.2. Что происходит дальше

Сразу после `complete_attempt` фронт делает (HAR #1: `11:28:21.951` → `11:28:22.089`,
HAR #2: `12:05:41.853` → `12:05:42.056` — задержка ~100–200 мс):

```
GET /webtests/exam/rest/secure/challenge/{challenge_id}/attempt/{attempt_id}/result
```

и обновляет историю:

```
GET .../assignments/my
GET .../assignment/context/{assignment_context_id}/result
```

После завершения `info` меняется: `last_attempt_id` → `null` (незавершённых нет),
`number_of_attempt` остаётся `1`.

---

## 9. Результат попытки

### 9.1. `GET /webtests/exam/rest/secure/challenge/{challenge_id}/attempt/{attempt_id}/result`

**Запрос:** заголовки §3.6, тела нет.

**Ответ `200`** (один и тот же и сразу после завершения, и при повторном открытии из истории):

```json
{
  "groups_context_info": {},
  "tasks_how_to_beat": {},
  "attempt_info": {
    "attempt_id": 409678691,
    "attempt_num": 1,
    "start_attempt": "2026-11-21T08:27:11.251813Z",
    "complete_attempt": "2026-11-21T08:28:21.977349Z",
    "time_duration": 70725,
    "percent_complete": 100.0,
    "person_id": "0345c036-12a8-4757-a00b-9555588736e1",
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
      "challenge_tasks": [
        {
          "score": 1,
          "max_score": 1,
          "criteria_score": null,
          "status": "DONE",
          "result_impact": "INCLUDED",
          "id": 802577977,
          "task_order": 0,
          "question_elements": [
            {
              "text_styles": [],
              "block_styles": [],
              "content": [],
              "text": "Основными компонентами воздуха являются",
              "type": "content/text"
            }
          ],
          "answer": {
            "options": [
              { "id": "a9hdqt", "text": "кислород и азот", "content": [], "text_styles": [], "block_styles": [], "type": "option_type/default" },
              { "id": "q85i22", "text": "озон и кислород",  "content": [], "text_styles": [], "block_styles": [], "type": "option_type/default" },
              { "id": "xp5e7i", "text": "кислород и гелий", "content": [], "text_styles": [], "block_styles": [], "type": "option_type/default" },
              { "id": "xsuoum", "text": "азот и водород",   "content": [], "text_styles": [], "block_styles": [], "type": "option_type/default" }
            ],
            "right_answer": { "id": null },
            "options_order": "random",
            "reference_right_answer": null,
            "type": "answer/single",
            "text_input_rules": null
          },
          "user_answer": {
            "@answer_type": "answer/single",
            "id": "a9hdqt"
          },
          "answer_feedback": {
            "correctness": "CORRECT",
            "type": "JUST_CORRECTNESS"
          }
        }
      ],
      "view_mode": "SINGLE_TASK"
    }
  ],
  "tasks_how_to_verify": {},
  "evaluation_criteria": [],
  "use_case": "HOMEWORK"
}
```

### 9.2. `attempt_info`

| Поле | Тип | Смысл |
|---|---|---|
| `attempt_id` | int | ID попытки |
| `attempt_num` | int | номер попытки (с 1) |
| `start_attempt` | ISO-8601 UTC | старт |
| `complete_attempt` | ISO-8601 UTC | завершение |
| `time_duration` | int (ms) | длительность; HAR #1: `70725` мс ≈ 70.7 с (разница меток 70726 мс) |
| `percent_complete` | float | процент выполнения (`100.0`) |
| `person_id` | UUID | персона |
| `max_score` | int | максимум баллов |
| `got_score` | int | набрано |
| `amount_correct_answer` | int | верных |
| `amount_partially_correct_answer` | int | частично верных |
| `amount_wrong_answer` | int | неверных |
| `amount_waiting_review_answer` | int | ждут ручной проверки |
| `amount_missed_answer` | int | пропущено |

Пример HAR #2 (8 заданий, все верно):

```json
{
  "attempt_id": 409688940,
  "attempt_num": 1,
  "start_attempt": "2026-11-21T08:51:22.462887Z",
  "complete_attempt": "2026-11-21T09:05:41.871115Z",
  "time_duration": 859408,
  "percent_complete": 100.0,
  "person_id": "0345c036-12a8-4757-a00b-9555588736e1",
  "max_score": 8,
  "got_score": 8,
  "amount_correct_answer": 8,
  "amount_partially_correct_answer": 0,
  "amount_wrong_answer": 0,
  "amount_waiting_review_answer": 0,
  "amount_missed_answer": 0
}
```

`time_duration = 859408` мс ≈ 14 мин 19 с; разница `start`/`complete` — `859408.4` мс.
То есть `time_duration` считается как `complete - start` с округлением вниз до мс.

### 9.3. `results[]` — задания с проверкой

| Поле | Смысл |
|---|---|
| `test_group_num` | номер группы |
| `challenge_tasks[]` | задания |
| `score` | получено баллов за задание |
| `max_score` | максимум за задание |
| `criteria_score` | балл по критериям или `null` |
| `status` | `DONE` (проверено автоматически) или `AWAIT_FOR_REVIEW` (ждёт проверки учителем) |
| `result_impact` | `INCLUDED` — задание учтено в результате |
| `id`, `task_order` | ID и порядок задания |
| `question_elements[]` | условие задания (как в `start-attempt`) |
| `answer` | структура ответа; правильный ответ не раскрывается |
| `user_answer` | ответ пользователя (см. ниже) |
| `answer_feedback` | фидбек: `{"correctness":"CORRECT","type":"JUST_CORRECTNESS"}` |
| `view_mode` | режим показа |

`answer.right_answer` в результате:

* `answer/single` → `{"id": null}`;
* `answer/match` → `{"match": {}}`.

То есть даже после завершения правильные ответы не раскрываются.

#### 9.3.1. Проверка на реальных попытках 16–22.11.2026

Через историю `assignments/my` → `context/{id}/result` и
`attempt/{id}/result` проверены **13 ЦДЗ и 17 попыток** (включая повторные
попытки по истории). Это подтвердило несколько важных частных случаев.

**Ручная проверка.** Если в задании есть развёрнутый ответ, оно может прийти
со статусом `AWAIT_FOR_REVIEW`, пустым `answer_feedback` (`{"type":"EMPTY"}`)
и `score: 0` до проверки учителем. При этом итоговая попытка уже может иметь
`got_score: 4` из `max_score: 5`, а в `attempt_info` появляется:

```json
{
  "amount_correct_answer": 4,
  "amount_waiting_review_answer": 1,
  "amount_wrong_answer": 0
}
```

Поэтому `score: 0` у задания в статусе `AWAIT_FOR_REVIEW` нельзя показывать
как «неверно»: это «на проверке», а не окончательный ноль.

**`answer/gap/match/text`.** В реальном задании `answer.text_position[]`
содержит только `position_id`, `text_id` и `position`; сами варианты лежат
общим массивом в `answer.options[]`. Ответ ученика содержит только
`text_position_answer[]`:

```json
{
  "@answer_type": "answer/gap/match/text",
  "text_position_answer": [
    { "position_id": "<position_id>", "text_id": "<text_id>", "id": "<option_id>" }
  ]
}
```

**`answer/groups`.** Заголовки групп — это отдельные элементы
`answer.options[]` с `type: "option_type/group"`. У элементов-картинок
`group_id` отсутствует, поэтому привязку нельзя брать из `option.group_id`:
группы строятся по `option_type/group`, а `user_answer.groups[].group_id`
содержит ID такого заголовка. Один элемент относится к одной группе.

**`answer/table`.** Заголовки и сетка лежат не в `question_elements`, а в
`answer.options[0].content[0]`:

```json
{
  "type": "content/table",
  "table": {
    "cells": { "0": { "0": ["Жизнеспособность"], "1": ["Количество хлорофилла"] } },
    "rows": 2,
    "columns": 2,
    "rowHeaders": false,
    "columnHeaders": true
  }
}
```

Типы ячеек — в `answer.cell_types`, например
`{"1":{"0":"MANUAL_INPUT","1":"MANUAL_INPUT"}}`. Ответ ученика и скрытый
`right_answer` используют объект `cells`:

```json
{"@answer_type":"answer/table","cells":{"1":{"0":["3"],"1":["1"]}}}
```

**Правильный ответ.** Во всех 78 проверенных заданиях `answer_feedback.type`
был только `JUST_CORRECTNESS` (73) или `EMPTY` (5). `right_answer` содержал
заглушки (`id: null`, `ids: []`, `match: {}`, пустые `cells`), а
`reference_right_answer` был `null`. `SHOW_CORRECT_ANSWER` с
`correct_answer` на этих попытках не встретился. Тот же скрытый результат
возвращает read-only
`GET /challenge/task/answers?challenge_id={cid}&challenge_attempt_id={aid}`
(дополнительно приходит `can_assess_task_ids`, но правильные ответы не
раскрываются).

**Картинки.** В `content/atomic` поле `relative_url` бывает как полным
`https://uchebnik.mos.ru/cms/...`, так и путём `/system/atomic_objects/...`.
Относительный путь нужно склеивать с `https://uchebnik.mos.ru`, а не с
`school.mos.ru`; иначе картинка не загрузится.

`user_answer` по типам:

```json
{"@answer_type": "answer/single", "id": "2255d8aa-0400-41e1-b563-b054087413df"}
```

```json
{
  "@answer_type": "answer/match",
  "match": {
    "ed3669ff-f46e-49c3-847d-2afd66803c22": ["b40795d2-5234-4abe-ac20-153c3a5e001f"],
    "a21e4f2d-ff6b-45b0-9815-7280bdd0d9ff": ["1b807e07-0d33-4b58-b155-45541a6ef6d7"],
    "c889b930-fe0a-4bd5-a120-9f9405ef3d3f": ["56e0db88-d6ac-4371-b001-b3f9bfd5f123"],
    "ca4a51ff-4f5d-49e0-b68b-6c9a263aa076": ["4e2c3324-f375-4db2-89aa-d66ae751f480"]
  }
}
```

Для `answer/match` в `answer.options[]` сохраняются оба вида опций
(`option_type/match/source` и `option_type/match/target`) и их `content[]` с картинками.

**`answer_feedback`** (значения уточнены по JS-бандлу, §16.4)

| Поле | Значения | Смысл |
|---|---|---|
| `type` | `EMPTY` | нет фидбека (то, что вернул `task/answer`) |
|  | `JUST_CORRECTNESS` | только правильность, без разбора (`CORRECT`/`PARTIALLY_CORRECT`/`INCORRECT`) |
|  | `SHOW_CORRECT_ANSWER` | показывается правильный ответ: дополнительно `correct_answer`, `detail_correctness`, `hideCorrectAnswer` |
| `correctness` | `CORRECT` / `PARTIALLY_CORRECT` / `INCORRECT` | оценка ответа |

В обоих HAR фидбек одинаков для всех заданий: `{"correctness":"CORRECT","type":"JUST_CORRECTNESS"}`.
Неверные ответы в записях не зафиксированы, но перечисленные выше значения/jsx-логика
присутствуют в бандле.

### 9.4. Прочие поля верхнего уровня

| Поле | Смысл |
|---|---|
| `groups_context_info` | контекст групп, обычно `{}` |
| `tasks_how_to_beat` | «как пройти», обычно `{}` |
| `tasks_how_to_verify` | «как проверять», обычно `{}` |
| `evaluation_criteria` | критерии оценивания, обычно `[]` |
| `use_case` | `HOMEWORK` |

---

## 10. Сводная таблица эндпоинтов

| # | Метод | Путь | Тело | Назначение |
|---|---|---|---|---|
| 1 | POST | `/acl/api/session/v2/refresh` | `null` | обновить `accessTokenEom` + cookie сессии |
| 2 | GET | `/webtests/exam/rest/secure/auth/aclx/user_info` | — | кто я, профили |
| 3 | GET | `/webtests/exam/rest/secure/challenge/{cid}/info` | — | метаданные ЦДЗ, есть ли незавершённая попытка |
| 4 | GET | `/webtests/exam/rest/secure/challenge/{cid}/assignments/my` | — | `assignment_context_id` |
| 5 | GET | `/webtests/exam/rest/secure/challenge/assignment/context/{ctx}/result` | — | история попыток (только завершённые) |
| 6 | POST | `/webtests/exam/rest/secure/challenge/{cid}/start-attempt` | JSON | начать **или** продолжить попытку |
| 7 | POST | `/webtests/exam/rest/secure/challenge/task/answer` | multipart `request` | ответить на задание |
| 8 | POST | `/webtests/exam/rest/secure/challenge/{cid}/complete_attempt` | JSON | завершить попытку |
| 9 | GET | `/webtests/exam/rest/secure/challenge/{cid}/attempt/{aid}/result` | — | детальный результат попытки |
| 10 | GET | `/webtests/exam/rest/secure/challenge/task/answers?challenge_id={cid}&challenge_attempt_id={aid}` | — | ответы и проверка по попытке (найден в JS-бандле, в HAR не вызывался, §16.5) |
| 11 | POST | `/webtests/exam/rest/secure/challenge/task/answer/estimation` | JSON | ручная оценка учителем: `manual_score`, `criteria_score` (§16.5) |
| 12 | GET / POST | `/webtests/exam/rest/secure/attempt/feedback/{aid}` | `data` | опрос ученика: rating/passing_improve/passing_error (§16.5.1) |
| 13 | GET | `/webtests/exam/rest/secure/challenge/{cid}/assignments/my?attemptId={aid}` | — | тот же список с фильтром по попытке (§16.5) |
| 14 | POST | `/webtests/exam/rest/secure/challenge/generate_by/{spec|task|binding}` | JSON | генерация challenge редактором (§16.5) |
| — | GET | `/exam/challenge/{cid}?<params>` | — | HTML-страница прохождения (SPA), источник `context`/`registration` |
| — | GET | `/exam/challenge/{cid}/attempt/{aid}` | — | HTML-страница результата попытки (`url_details` из LRS, см. §15) |
| — | GET | `/exam/config.json` | — | константы путей |
| — | GET | `/exam/abConfig.json` | — | A/B-конфиг медиа (не нужно) |

---

## 11. Хронология из HAR (как это выглядит вживую)

```
11:26:32  GET  /exam/challenge/150779500?...            (страница #1, открытие)
11:26:34  POST /acl/api/session/v2/refresh              -> eom-токен #1
11:26:35  GET  .../auth/aclx/user_info
11:26:35  GET  .../challenge/150779500/info             -> number_of_attempt=0, last=null
11:27:11  POST .../challenge/150779500/start-attempt     -> attempt=409678691  (НАЧАТЬ)
11:27:59  GET  /exam/challenge/150779500?...            (страница #2, возврат/перезагрузка)
11:27:59  POST /acl/api/session/v2/refresh              -> eom-токен #2
11:28:00  GET  .../info                                 -> number_of_attempt=1, last=409678691
11:28:04  GET  .../assignments/my                       -> ctx=1646494213
11:28:04  GET  .../assignment/context/1646494213/result  -> attempts=[] (попытка ещё идёт)
11:28:08  POST .../start-attempt                        -> attempt=409678691  (ПРОДОЛЖИТЬ, тот же)
11:28:15  POST .../challenge/task/answer                -> {"type":"EMPTY"}
11:28:21  POST .../complete_attempt                     -> {}
11:28:22  GET  .../attempt/409678691/result             -> полный результат
11:28:35  GET  /exam/challenge/150779500?...            (страница #3, результат/история)
11:28:35  POST /acl/api/session/v2/refresh              -> eom-токен #3
11:28:36  GET  .../info                                 -> number_of_attempt=1, last=null
11:28:37  GET  .../assignments/my
11:28:38  GET  .../assignment/context/1646494213/result  -> attempts=[409678691], used=1
11:28:40  GET  .../attempt/409678691/result             -> тот же результат
```

Ключевой вывод: **«продолжить попытку» — это повторный `start-attempt`**, а не отдельный эндпоинт;
незавершённая попытка видна через `info.last_attempt_id`, но отсутствует в истории
(`assignment/context/.../result`), пока не завершена.

### 11.1. HAR #2 — реальное прохождение 8 заданий

```
11:51:18  (запись начата; страница уже открыта)
11:51:22  POST .../challenge/152171589/start-attempt   -> attempt=409688940, 8 заданий
11:55:30  POST .../challenge/task/answer (task 802613910) -> EMPTY   (ответ #1)
11:55:51  POST .../challenge/task/answer (task 802613910) -> EMPTY   (переответ #2)
11:55:54  POST .../challenge/task/answer (task 802613910) -> EMPTY   (переответ #3, финальный)
11:57:15  POST .../challenge/task/answer (task 802613911) -> EMPTY
11:57:48  POST .../challenge/task/answer (task 802613912) -> EMPTY
11:58:47  POST .../challenge/task/answer (task 802613913) -> EMPTY
12:01:43  POST .../challenge/task/answer (task 802613914) -> EMPTY
12:02:29  POST .../challenge/task/answer (task 802613915) -> EMPTY
12:03:15  POST .../challenge/task/answer (task 802613916) -> EMPTY
12:05:14  POST .../challenge/task/answer (task 802613917) -> EMPTY   (answer/match)
12:05:41  POST .../challenge/152171589/complete_attempt -> {}
12:05:42  GET  .../attempt/409688940/result            -> 8/8, все CORRECT
```

Что видно по таймингам:

* между стартом и первым ответом — ~4 минуты (чтение/прокрутка заданий);
* между заданиями — 30–190 секунд;
* `refresh`/`info`/`user_info` в этой записи **не вызывались** — сессия была живой,
  страница открыта до начала записи;
* ни одного `start-attempt`-повтора: попытка одна, завершена через 14 мин 19 с.

---

## 12. Пример на Python (эскиз)

```python
import json, requests

BASE = "https://uchebnik.mos.ru"
SECURE = BASE + "/webtests/exam/rest/secure"

CID, CTX, AID, TASK = 150779500, 1646494213, 409678691, 802577977

s = requests.Session()
s.cookies.update({...})            # cookie-jar браузера
TOKEN = "<accessTokenEom>"

s.headers.update({
    "Accept": "application/json;charset=UTF-8",
    "Content-Type": "application/json",
    "User-Id": "1002824909",
    "Profile-Id": "1002991417",
    "Profile": json.dumps({"id": "1002991417", "type": "student"}),
    "Authorization": "Bearer " + TOKEN,
    "X-Mes-Subsystem": "testw",
    "Origin": BASE,
})

# 1) продлить токен
r = s.post(BASE + "/acl/api/session/v2/refresh", data="null")
TOKEN = r.json()["accessTokenEom"]
s.headers["Authorization"] = "Bearer " + TOKEN

# 2) метаданные
info = s.get(f"{SECURE}/challenge/{CID}/info").json()

# 3) история
my = s.get(f"{SECURE}/challenge/{CID}/assignments/my").json()
CTX = my["assignment"]["assignment_context_id"]
hist = s.get(f"{SECURE}/challenge/assignment/context/{CTX}/result").json()

# 4) старт/продолжение
context = "<JWT HS256 из URL страницы>"
registration = "38c4e62a-e7b3-41f4-8955-0958e19e3ced"
actor = {"objectType": "Agent",
         "account": {"name": "77:0345c036-12a8-4757-a00b-9555588736e1"}}
start = s.post(f"{SECURE}/challenge/{CID}/start-attempt", json={
    "actorParam": json.dumps(actor, separators=(",", ":")),
    "registrationParam": registration,
    "activityIdParam": f"{BASE}/exam/challenge/{CID}",
    "resultOverviewUrl": f"{BASE}/exam/challenge/{CID}/attempt/{{attemptId}}",
    "resultManagementUrl": f"{BASE}/exam/challenge/{CID}/context/{{assignmentContextId}}/attempt/{{attemptId}}",
    "context": context,
    "contextCorrupted": False,
}).json()

AID = start["challenge_attempt_id"]
TASK = start["challenge_test_groups"][0]["challenge_tasks"][0]["id"]

# 5) ответ (multipart, часть "request", filename="blob")
# ВАЖНО: id варианта — UUID из start["...challenge_tasks"][i]["answer"]["options"][j]["id"],
# формат id не фиксирован (бывает и "a9hdqt", и UUID).
payload = {"challenge_task_id": TASK, "challenge_attempt_id": AID,
           "answer": {"@answer_type": "answer/single", "id": "a9hdqt"}}
r = s.post(f"{SECURE}/challenge/task/answer",
           files={"request": ("blob", json.dumps(payload, separators=(",", ":")),
                              "application/json")})
# для multipart Content-Type выставится автоматически; "Content-Type: application/json"
# из общих заголовков requests заменит сам
print(r.json())          # -> {"type": "EMPTY"}

# 5b) answer/match: ключ — source, значение — список target
match_payload = {
    "challenge_task_id": 802613917,
    "challenge_attempt_id": AID,
    "answer": {
        "@answer_type": "answer/match",
        "match": {
            "<source_option_id>": ["<target_option_id>"],
        },
    },
}
s.post(f"{SECURE}/challenge/task/answer",
       files={"request": ("blob", json.dumps(match_payload, separators=(",", ":")),
                          "application/json")})

# 6) завершить
s.post(f"{SECURE}/challenge/{CID}/complete_attempt", json={"challenge_id": CID})

# 7) результат
result = s.get(f"{SECURE}/challenge/{CID}/attempt/{AID}/result").json()
print(result["attempt_info"]["got_score"], "/", result["attempt_info"]["max_score"])
for grp in result["results"]:
    for t in grp["challenge_tasks"]:
        print(t["id"], t["status"], t["score"], "/", t["max_score"],
              t["answer_feedback"])

```

---

## 13. Что НЕ видно в HAR (открытые вопросы)

Ниже — список того, что **нельзя** достоверно установить по этой записи. Для каждого пункта
указано, что именно известно, чего не хватает и как это проверить отдельным экспериментом.
Не считайте гипотезы фактами: в документе они помечены явно.

### 13.1. Источник первого `accessTokenEom`

**Известно:** на момент открытия страницы (11:26:32) браузер уже имел `accessTokenEom`:
`POST /acl/api/session/v2/refresh` уходит с `Authorization: Bearer <eom>` (payload:
`sub=1002824909`, `iat=11:25:11`), а `refresh` только **продлевает** его (`iat=11:26:34`).

**Не известно:** каким запросом этот токен выдан впервые. В HAR его нет — запись началась
после логина. Ожидаемый путь: `school.mos.ru` SSO (`/v3/auth/sudir/callback` → cookie
`aupd_token`) и затем какой-то обмен `aupd` → `eom` на стороне `school.mos.ru`/`uchebnik.mos.ru`.

**Версия из `mesh-cdz-api.md` §9:** там сказано, что `POST /acl/api/session/v2/refresh`
«поднимает **или** обновляет сессию uchebnik по cookie», т.е. refresh может быть и
первичным обменом cookie → токен, без предварительного Bearer. Это правдоподобно:
`auth_token`/`aupd_token` — JWT с теми же `usr`/`prf`, а `refresh` ставит
`Session-Id-External` на 7 дней.

**Как проверить:** записать HAR с самого начала (чистый профиль → вход → переход в ЦДЗ)
и найти первый ответ, содержащий `accessTokenEom`. Отдельно дёрнуть `refresh` **без**
`Authorization`, но с cookie `aupd_token`/`auth_token`/`Session-Id-External` (см. 13.7).

### 13.2. Формат ошибок `4xx`

**Известно:** в обоих HAR 100% ответов — `200 OK`. Ни `400`, ни `401`, ни `403`, ни `409`
не встречались.

**Из `mesh-cdz-api.md` §9 (не проверено на uchebnik):** «без этих заголовков
(только cookie) — `401`». То есть минимум `401` существует и связан с отсутствием
заголовков авторизации, но тело ответа там не описано.

**Не известно:**

* структура тела ошибки (`{"error": ...}` / `{"message": ...}` / problem+json / HTML);
* различаются ли `401` (истёк Bearer) и `403` (нет прав) по телу;
* что вернётся при повторном `complete_attempt` или ответе на чужую попытку;
* есть ли `Retry-After` / коды `429`.

**Как проверить:** отправить запрос с заведомо битым `Authorization`, с чужим `challenge_id`,
повторно завершить попытку, ответить с несуществующим `challenge_task_id`. Собрать коды и тела.

### 13.3. Полный набор `answer types`

**Обновление:** полный перечень типов и их строк найден в JS-бандле — см. §16.2.
Здесь ниже остаются только два типа, подтверждённые живым трафиком.

**Известно (два типа, оба сняты с живых ответов):**

`answer/single` — один вариант:

```json
{
  "challenge_task_id": 802613910,
  "challenge_attempt_id": 409688940,
  "answer": { "@answer_type": "answer/single", "id": "<option_id>" }
}
```

`answer/match` — сопоставление source → target:

```json
{
  "challenge_task_id": 802613917,
  "challenge_attempt_id": 409688940,
  "answer": {
    "@answer_type": "answer/match",
    "match": { "<source_option_id>": ["<target_option_id>"] }
  }
}
```

Опции в задании:

* `answer/single` → `options[].type = "option_type/default"`;
* `answer/match` → `options[].type = "option_type/match/source"` и
  `"option_type/match/target"`, `answer.options_order = "none"`.

**Не известно:** схемы для множественного выбора, текстового ввода, числа,
сортировки, файлов, аудио/видео-ответов и т.п. Не известны:

* точные значения `answer.type` / `@answer_type` (`answer/multiple`? `answer/text`?);
* как передаётся несколько выбранных `id` (массив? `ids`?);
* как выглядит `text_input_rules` для текстовых заданий (в обоих HAR всегда `null`);
* какие `answer_feedback.correctness` / `type` бывают кроме `CORRECT` / `JUST_CORRECTNESS`;
* может ли `match` содержать несколько target на один source (схема — массив,
  но в примере везде ровно один).

**Как проверить:** открыть ЦДЗ с другим типом задания и снять HAR ответа; либо перебрать
типы на тестовом задании, которое создаёт учитель.

### 13.4. Как получать fetch-токен `/lrs-dhw/token/fetch/<hash>`

**Известно:** URL присутствует в параметрах страницы; в каждом запуске свой `<hash>`:

```
HAR #1: fetch=https://uchebnik.mos.ru/lrs-dhw/token/fetch/b57c194fa7c2af0a327d1501e5fd0a7d
HAR #2: fetch=https://uchebnik.mos.ru/lrs-dhw/token/fetch/bf82e9264c2a9d010820fdb3cd919e34
```

и в конфиге: `"USE_NEW_LRS_API": true`, `"LRS_EXCLUDE_FETCH_TOKEN_VALIDATION": true`.

**Не известно:** сам вызов **отсутствует в обоих HAR** — вероятно, токен не понадобился
(валидация отключена флагом) или запрос не попал в запись. Не известны:

* метод (ожидается `GET`), заголовки и тело;
* формат ответа (JWT? `{"token": ...}`?);
* TTL и назначение (доступ к LRS/хранилищу результатов);
* что означает `<hash>` в пути (хеш от `registration`/`context`/`person_id` — не установлено;
  в двух запусках он разный, при том что `person_id` один).

**Как проверить:** открыть страницу ЦДЗ с включённой валидацией (или на другом стенде),
поискать в HAR запрос к `/lrs-dhw/token/fetch/...`; либо дёрнуть URL вручную с текущими
cookie/Bearer и посмотреть ответ.

### 13.5. Поведение при истечении лимита времени попытки

**Известно:** в этом ЦДЗ `attempt_time_limit_type: "FIXED"`, `available_time_in_minutes: 0`
(лимита нет), `available_from`/`available_until` — «бесконечные» (2000/3000 год).
Фронт считает таймер по `challenge_attempt_started_at` и `current_date_time` из `start-attempt`.

**Не известно:**

* что вернёт `task/answer` после истечения лимита (ошибка? `200` + отказ?);
* авто-завершается ли попытка на сервере по таймауту (и с каким `complete_attempt`);
* можно ли продолжить просроченную попытку через `start-attempt`;
* как `FIXED` соотносится с `available_time_in_minutes` (минуты на попытку или на всё ЦДЗ);
* поведение `available_from` / `available_until` вне окна (код ошибки, текст).

**Как проверить:** ЦДЗ с `available_time_in_minutes > 0`; начать попытку, дождаться истечения,
затем повторить `start-attempt` / `task/answer` / `complete_attempt`.

### 13.6. Семантика `allowed_tries = 0`

**Известно:** `info.allowed_tries = 0`, `history.max_amount_attempt = 0`, при этом попытка
началась и завершилась, а `can_reanswer = true`, `number_of_attempt` вырос до 1.

**Не известно:** `0` — это «без ограничения», «не задано» или «0 дополнительных попыток»
(и ограничение задаётся другим полем). Также не ясно, как считаются `allowed_tries`
и `max_amount_attempt` при нескольких попытках и что вернёт `start-attempt` при исчерпании.

**Как проверить:** ЦДЗ с `allowed_tries = 1` и с `allowed_tries = 2`: завершить попытку(и)
и вызвать `start-attempt` ещё раз, сравнить ответ/ошибку и значения счётчиков в `info`
и в истории. Отдельно проверить `can_reanswer = false`.

### 13.7. `POST /acl/api/session/v2/refresh` без валидного Bearer

**Известно:** успешный вызов — это `Authorization: Bearer <действующий accessTokenEom>`,
тело `null`, ответ `200` + новые токены + `Set-Cookie` сессии на 7 дней. Все три вызова
в HAR #1 были успешными (в HAR #2 `refresh` не вызывался вовсе).

**Проверено 22.11.2026:** refresh без Bearer, но с `Session-Id-External` +
`Session-Eom-Profile` (и любым из `aupd_token`/`Ltpatoken2`/`user_login`/`obr_id`)
возвращает `200` и новые `accessTokenEom`/`accessTokenAupd`/`Set-Cookie`.
Без двух `Session-*` cookies сервер отвечает
`401 {"error_code":"401","error_description":"Required cookies are missing."}` —
одного `aupd_token` и профильных cookie из `auth_cookies` недостаточно.

**Версия из `mesh-cdz-api.md` §9:** «`POST /acl/api/session/v2/refresh` — поднимает
**или** обновляет сессию uchebnik по cookie». Если это так, то refresh умеет работать
без Bearer, только по cookie (`auth_token`/`aupd_token`/`Session-Id-External`), и именно
он является первичным источником `accessTokenEom` (см. 13.1). Проверкой не подтверждено.

**Не известно:**

* можно ли вызвать `refresh` вообще без `Authorization` (только с cookie
  `Session-Id-External` / `aupd_token`) и что он вернёт;
* принимает ли он `accessTokenAupd` вместо `accessTokenEom`;
* что будет при истёкшем eom (`401`? новый токен? редирект на SSO?);
* обязательны ли заголовки `User-Id` / `Profile-Id` / `Profile` или они лишь дублируют токен;
* можно ли обновить сессию после истечения 7-дневной cookie.

**Ответ из JS-бандла (§16.1):** клиент вызывает `refresh` вообще без тела и без явного
`Authorization`; глобальный провайдер добавляет `Authorization: Bearer` **только если**
в localStorage уже есть `auth_token`. Значит, при первом входе `refresh` уходит без Bearer
и именно он получает первый `accessTokenEom` из cookie-сессии. Гипотеза из 13.1/13.7
подтверждена кодом (но не проверена живым запросом).

**Как проверить:** 4 запроса: (а) без `Authorization`; (б) с `Authorization: Bearer <aupd>`;
(в) с истёкшим eom (подождать 15+ мин или взять старый); (г) без `User-Id`/`Profile-Id`.
Сравнить коды, тела и `Set-Cookie`.

### 13.8. Дополнительно не наблюдалось

* **`contextCorrupted: true`** — ветка восстановления битого `context` не встречалась;
  не известен ответ сервера и что делает фронт (перезапуск? редирект в лаунчер?).
* **`start-attempt` при уже отвеченных заданиях** — «продолжение» в HAR было **до** отправки
  ответа (`user_answer: null`). Формат заполненного `user_answer` / `answer_feedback`
  именно в ответе `start-attempt` не зафиксирован (в `attempt/{id}/result` он есть, §9).
* **Роль учителя** (`role=teacher`, чужие результаты) — не рассматривалась.
  `user_results[]` в истории рассчитан на несколько учеников, но формат для учителя
  (списки, фильтры, `staff_id`) не снят.
* **Пагинация истории** — при большом числе попыток/учеников не известны параметры
  `limit`/`offset` и структура ответа.
* **`assignment: null`** в `assignments/my` (нет назначения) — не наблюдалось.
* **Одновременные попытки / гонки** — поведение при двух параллельных `start-attempt`
  (создастся ли две попытки) не проверялось.

### 13.9. Расхождение с `mesh-cdz-api.md`: `Auth-Token`, `auth_token`, `task/answers`

В `mesh-cdz-api.md` §9 (разбор от 20.11.2026) для `/webtests/exam/rest/secure/*` указаны
другие заголовки:

| Заголовок | Источник по документу | Что реально в HAR #1/#2 |
|---|---|---|
| `Auth-Token` | cookie `auth_token` на `uchebnik.mos.ru` | **отсутствует**, нет и cookie `auth_token` |
| `Profile-Id` | cookie `Profile-Id` | есть, но cookie с таким именем нет |
| `User-Id` | cookie `User-Id` | есть, но cookie с таким именем нет |
| — | — | `Authorization: Bearer <accessTokenEom>` — есть в каждом запросе |
| — | — | `Profile: {"id":"...","type":"student"}` — есть |
| — | — | `X-Mes-Subsystem: testw` — есть |

Документ также утверждает, что «без этих заголовков (только cookie) — `401`»,
и что `auth_token` — JWT с `usr`/`prf[]`. По факту в обоих HAR JWT с `usr`/`prf[]`
приходит в `Authorization: Bearer`, а cookie называются иначе (`aupd_token`,
`Session-Id-External`, `Session-Eom-Profile`).

**Разгадка из JS-бандла (§16.1):** `Authorization: Bearer <auth_token>` добавляется
глобально для всех запросов; заголовки `Auth-Token` + `User-Id` + `Profile-Id` выставляются
**только** для `isEzd`-запросов и режима impersonation (`impersonationProfile` из localStorage).
То есть в обычном прохождении ЦДЗ виден Bearer, а в EZD/админском сценарии — `Auth-Token`.
Оба варианта описаны в одном клиенте — contradictions нет.

Выводы:

* это **разные сценарии одного клиента**: документ снимал EZD/результат или режим
  impersonation, HAR — обычное прохождение `/exam/challenge/{id}`;
* надёжная стратегия для клиента — слать **всё сразу**:
  `Authorization: Bearer <eom>` + `User-Id` + `Profile-Id` + `Profile` +
  `X-Mes-Subsystem: testw` + полный cookie-jar. Лишние заголовки сервер игнорирует;
* пока не проверено, принимает ли сервер `Auth-Token` как альтернативу Bearer.

`GET .../challenge/task/answers?challenge_id={cid}&challenge_attempt_id={aid}`

* упомянут только в `mesh-cdz-api.md` §9 как запрос при открытии **страницы результата**;
* в обоих HAR **отсутствует** (там результат берётся через
  `GET .../attempt/{aid}/result`);
* формат ответа в документе не описан — неизвестно, дублирует ли он `results[]`
  из `attempt/{aid}/result` или содержит больше полей (например, проверку по каждому ответу).

**Как проверить:** открыть страницу `/exam/challenge/{cid}/attempt/{aid}` с включённым
HAR и посмотреть, какие заголовки и эндпоинты используются там реально.

---

## 14. Безопасность и приватность

* `accessTokenEom`, `accessTokenAupd`, `Session-Id-External`, `aupd_token`, `Ltpatoken2`
  и `mosid://`-ссылки — ** credentials**. Не публиковать, не коммитить, не передавать третьим лицам.
* `accessTokenEom` живёт 15 минут, `accessTokenAupd` и `aupd_token` — 24 часа,
  `Session-Id-External` — 7 дней.
* `context` (HS256) подписан сервером и привязан к `person_id` + времени запуска (`dt`/`mi`);
  подделка/переиспользование чужого контекста приведёт к отказу.
* Ответы `start-attempt` и `attempt/{id}/result` **не содержат** правильных ответов
  (`right_answer.id` всегда `null`) — это сделано намеренно.

---

## 15. Связь с ДЗ, запуск и LRS (uchebnik-часть)

Раздел перенесён из `mesh-ranker-app/docs/mesh-cdz-api.md` **только в части, касающейся
`uchebnik.mos.ru`**. Эндпоинты `school.mos.ru` здесь не разбираются — они нужны лишь
для понимания, откуда берутся `context`, `registration` и `challenge_id`.

### 15.1. Как ЦДЗ попадает в uchebnik

Из дневника материал ЦДЗ запускается цепочкой:

```
school.mos.ru/api/ej/partners/v1/homeworks/launch?homework_entry_id=…&material_id=…
   -> 302 с URL в ТЕЛЕ ответа:
      school.mos.ru/api/launcher/v1/launch?activity_url=…&context=<JWT>&registration=<uuid>&role=student
   -> OAuth на school.mos.ru (нужна cookie-сессия, одного Bearer мало)
   -> uchebnik.mos.ru/exam/challenge/{challenge_id}?activityId=…&context=…&registration=…
   -> /webtests/exam/rest/secure/*   (разбор в этом документе)
```

В Android-клиенте первый запрос выполняет Retrofit
(`MeshApiService.homeworkLaunch`) через отдельный OkHttp-клиент без
автоперехода. Заголовки — Bearer, профиль и cookie jar аккаунта (как у
веб-дневника); ответ разбирается как `Location`, строка-URL или Gson-модель
`HomeworkLaunchLink`. Полученный `api/launcher/v1/launch` уже открывается в
браузере или нативном ЦДЗ, а не запрашивается клиентом повторно.

Типы материалов, которые ведут на `/exam/challenge/{challenge_id}`:

| `type` материала | Подпись в UI | Куда ведёт |
|---|---|---|
| `test_task_binding` | «Задание» | `https://uchebnik.mos.ru/exam/challenge/{challenge_id}` |
| `test_spec_binding` | «Тестовая спецификация (Тест)» | `https://uchebnik.mos.ru/exam/challenge/{challenge_id}` |

`challenge_id` из `activity_url` **не равен** `uuid` материала из дневника (`uuid` из homework); это отдельный идентификатор задания внутри uchebnik.

**Практические следствия:**

1. `context` (HS256) и `registration` выдаются **на каждый запуск новыми** — их нельзя
   кэшировать и нельзя взять из соседнего задания.
2. Ссылка запуска **одноразовая**: повторное открытие той же ссылки (в т.ч. F5)
   показывает «Вы повторно перешли по ранее использованной ссылке». Для нового
   открытия нужен новый launch.
3. Ссылки `/exam/challenge/{id}` без `context`/`registration` — голый SPA, результат
   не привяжется к дневнику.

### 15.2. Две страницы uchebnik

| Страница | Назначение | Источник |
|---|---|---|
| `/exam/challenge/{challenge_id}` | прохождение; здесь вызываются `info`, `start-attempt`, `task/answer`, `complete_attempt` | оба HAR |
| `/exam/challenge/{challenge_id}/attempt/{attempt_id}` | результат попытки; `url_details` из LRS | `mesh-cdz-api.md` §6, §9 |

В `start-attempt` эти два маршрута передаются шаблонами:

```
resultOverviewUrl   = .../exam/challenge/{cid}/attempt/{attemptId}
resultManagementUrl = .../exam/challenge/{cid}/context/{assignmentContextId}/attempt/{attemptId}
```

Они совпадают с `url_details` / `url_control` в LRS (см. ниже).

### 15.3. Что об attempt видно из LRS

Результат ЦДЗ читается дневником через `GET school.mos.ru/api/lrs/v2/results`
(этот вызов **не** относится к uchebnik и здесь не разбирается), но полезные поля
ответа описывают именно попытку uchebnik:

| Поле LRS | Смысл | Соответствие в API uchebnik |
|---|---|---|
| `url_details` | ссылка на страницу результата | `/exam/challenge/{cid}/attempt/{aid}` = `resultOverviewUrl` |
| `url_control` | ссылка «управление результатом» | вероятно `resultManagementUrl` (с `assignment_context_id`) |
| `activity_id` | `https://uchebnik.mos.ru/exam/challenge/{cid}` | `activityIdParam` в `start-attempt`, параметр `activityId` |
| `registration` | UUID запуска | `registrationParam` в `start-attempt`, параметр `registration` |
| `actor` | `{"objectType":"Agent","account":{"name":"77:{person_id}"}}` | `actorParam` в `start-attempt` |
| `actual_person_id` | `person_id` | `attempt_info.person_id` |
| `started_at` | старт | `attempt_info.start_attempt` |
| `duration` | длительность, **секунды** (в примере документа `106`) | `attempt_info.time_duration` — но в **мс** (HAR #1: `70725` мс ≈ 70.7 с); это разные попытки |
| `status` | `2` — завершено | факт `complete_attempt` |
| `success`, `final` | признак успеха / финальности | `attempt_info.got_score` и завершённость |
| `score`, `max_score` | на LRS-уровне часто `null` | берите из `attempt_info` uchebnik |
| `recommended_mark`, `recommended_mark_scale` | рекомендованная оценка | — |
| `items[]` | по одному на задание | `results[].challenge_tasks[]` |
| `items[].task_number` | **ID задания uchebnik** | `challenge_tasks[].id` (например, `802577977` совпадает в HAR #1 и в документе) |
| `items[].score`, `max_score`, `mastery_score` | баллы за задание | `results[].challenge_tasks[].score` / `max_score` |
| `items[].has_answer` | есть ответ | `user_answer != null` |
| `items[].status`, `type` | внутренние коды LRS | `status: "DONE"`, `result_impact: "INCLUDED"` — это другие коды |
| `items[].topic_id`, `theme_id`, `education_level_id` | тема/уровень | в API uchebnik нет |
| `items[].expert_comment`, `url_details`, `url_control` | комментарий учителя, доп. ссылки | в API uchebnik нет |

Пример элемента `items[]` из документа:

```json
{
  "task_number": 802577977,
  "status": 0,
  "type": 1,
  "score": 1,
  "max_score": 1,
  "mastery_score": 1,
  "has_answer": true,
  "education_level_id": 2,
  "topic_id": [31944805],
  "theme_id": 0,
  "duration": null,
  "started_at": null,
  "url_details": null,
  "url_control": null,
  "expert_comment": null
}
```

Резюме:** единый сквозной идентификатор задания между API uchebnik и LRS — это
`items[].task_number` == `challenge_tasks[].id`.**

### 15.4. `context` как «контракт запуска»

Ещё раз свести то, что связывает дневник и uchebnik (подробнее — §4.2):

```jsonc
{
  "wt":  "1",                       // work_type = "1" (домашнее задание)
  "wti": 356825924,                 // work_id = homework_entry_id
  "pid": "0345c036-12a8-4757-a00b-9555588736e1",
  "dt":  1795251057944,             // время выдачи, мс
  "mi":  "7c4c1990-ce43-598f-af29-aa99b2d7f105"  // material_id
}
```

При запросе к LRS дневник передаёт тот же `work_type`/`work_id`/`person_id`/`material_id`
в параметре `context` (уже как обычный JSON, не JWT) — поэтому `wti` и `mi` из JWT
можно использовать для сопоставления результата с конкретным ДЗ.

### 15.5. Подводные камни из документа (uchebnik-часть)

* `challenge_id` и `cms_uuid` — разные вещи; `cms_uuid` относится к материалам CMS,
  `challenge_id` — к тестам `/exam`.
* Ссылки из `additional_materials.urls[]` дневника могут вести на uchebnik **без
  `context`** — прохождение по ним не привяжется к ДЗ. Нужен launch из §15.1.
* Открытие `/exam/challenge/{id}` без `context`/`registration` (open-view) с большой
  вероятностью не привязывает попытку к ДЗ — по тому же принципу, что и ссылки
  `additional_materials.urls[]` без контекста (вывод по документу, отдельно не проверялся).
* Статус «выполнено» в карточке ДЗ (`is_done`) — это галочка всего ДЗ, а не статус
  отдельного ЦДЗ; истина — в LRS/в `attempt_info`.
* Веб кеширует ответы (React Query): повторное открытие карточки может не дать
  сетевых запросов — это нормально.

---

## 16. Данные из JS-бандла: типы ответов и скрытые методы

Источник: `https://uchebnik.mos.ru/exam/js/main.module_7c8be42c.js` (тот же хеш, что в HAR,
~11 МБ минифицированного webpack-кода) и чанки `1360`, `257`, `7189`, `4541`. Source maps
нет. Ниже — то, что удалось восстановить из кода. Помечаю:

* **[факт]** — подтверждено живым HAR;
* **[код]** — прочитано в бандле, но в наших HAR не вызывалось;
* **[вывод]** — логическое следствие из кода.

### 16.1. Авторизация: откуда берётся `auth_token` и заголовки

Клиент держит JWT в **localStorage** (не только в cookie!). Ключи: `auth_token`,
`User-Id`, `Profile-Id`, `Profile`, `Session-Id-External` (последний — cookie).

После `POST /acl/api/session/v2/refresh` **[код]** выполняется:

```js
auth_token  = response.accessTokenEom
User-Id     = jwt(auth_token).usr
Profile-Id  = response.profileId
Profile     = JSON.stringify(prf.find(p => p.id == profileId))  // {id, type}
```

Если профиль с таким `id` в `prf[]` не найден — клиент бросает `Error("Profile not found")`.

Глобальный провайдер заголовков **[код]**:

```js
{
  "User-Id":      localStorage["User-Id"]      || "",
  "Profile-Id":   localStorage["Profile-Id"]   || "",
  "Profile":      localStorage["Profile"]      || "",
  "Authorization": localStorage["auth_token"]
                    ? "Bearer " + localStorage["auth_token"] : ""
}
```

Это в точности объясняет заголовки в HAR **[факт]**.

Дополнительно **[код]**:

* `Auth-Token`, `User-Id`, `Profile-Id` в явном виде добавляются только если
  запрос помечен `isEzd` **или** включён impersonation
  (`localStorage["impersonationProfile"]` с совпадающим `id`);
* `Sharing-key` добавляется из URL-параметра `sharing_key`;
* при `401` клиент вызывает `refresh` и повторяет запрос; сам URL refresh исключён
  из этого механизма (`h = ["/acl/api/session/v2/refresh"]`), чтобы не было цикла;
* параллельные вызовы refresh дедуплируются через один сохранённый Promise.

**Первичный токен.** `refresh` вызывается как `{method:"POST", url:"/acl/api/session/v2/refresh"}`
— без тела и без явного `Authorization`. Bearer добавляется только если `auth_token`
уже есть. Отсюда **[вывод]**: при первом открытии ЦДЗ `refresh` уходит **без Bearer**,
и сервер выдаёт первый `accessTokenEom` по cookie-сессии. Это отвечает на вопросы §13.1 и §13.7.

**Старый путь авторизации** (`USE_OLD_AUTH`) **[код]**:

```
GET /aclx/api/sessions/current
  -> заголовки ответа: User-Id, Profile-Id, Profile
  -> тело ответа: auth_token (просто текст, без JSON)
GET /aclx/api/sessions/aupd
GET /aclx/api/user
```

Это ещё один возможный источник первого токена (в зависимости от конфига `USE_OLD_AUTH`).

### 16.2. Полный перечень типов заданий (`answer.type`)

Из enum `Q` в бандле **[код]**. «Логический тип» — внутренний ID в редакторе тестов.

| Enum | Строка `answer.type` | Название в UI | logical_type_id |
|---|---|---|---|
| `SINGLE` | `answer/single` | Одиночный выбор | 26 |
| `MULTIPLE` | `answer/multiple` | Множественный выбор | 27 |
| `NUMBER` | `answer/number` | Ввод числа | 28 |
| `STRING` | `answer/string` | Одиночный ввод | 29 |
| `STRING_MULTIPLE` | `answer/string/multiple` | Множественный ввод | 59 |
| `FREE` | `answer/free` | Открытый ответ (ручная проверка) | 34 |
| `ORDER` | `answer/order` | Упорядочивание элементов | 31 |
| `ORDER_MULTIPLE` | `answer/order/multiple` | — | 44 |
| `GROUPS` | `answer/groups` | Распределение по группам | 32 |
| `MATCH` | `answer/match` | Один к одному | 30 |
| `TABLE` | `answer/table` | Заполнение таблицы V1 | 33 |
| `GRID` | `answer/grid` | Заполнение таблицы V2 | 33 |
| `INLINE_CHOICE` | `answer/inline/choice/single` | Выпадающий список | 42 |
| `GAP_MATCH` | `answer/gap/match/text` | Подстановка элементов | 41 |
| `COLOR_MATCH` | `answer/color/match` | — | 47 |
| `TEXT_IMAGE_MATCH` | `answer/gap/match/text/image` | — | 0 |
| `TEXT_STYLE_MATCH` | `answer/text/line_highlighting/match` | — | 47 |
| `IMAGE_MATCH` | `answer/gap/match/background/image` | Ответ на изображении | 50 |
| `IMAGE_SELECT` | `answer/gap/match/background/image/selection` | Выбор на изображении | 61 |
| `GAP_KEYBOARD` | `answer/gap/text/input` | Ввод слов в тексте | 49 |
| `REBUS_MATCH` | `answer/match/rebus` | — | 51 |
| `TIMELINE` | `answer/match/timeline` | Лента времени | 52 |
| `NUMBER_INTERVAL` | `answer/number/interval` | Число из интервала | 62 |

Группировка в UI **[код]**:

* «Выбор и ввод значений»: SINGLE, MULTIPLE, STRING, STRING_MULTIPLE, NUMBER;
* «Работа с текстом»: INLINE_CHOICE, GAP_MATCH, GAP_KEYBOARD;
* «Установление соответствий»: MATCH, GROUPS;
* «Другое»: TABLE, GRID, ORDER, TIMELINE, FREE, IMAGE_MATCH, IMAGE_SELECT, NUMBER_INTERVAL.

Флаг `USE_NEW_ANSWER_TYPES` из `/exam/config.json` **[факт: `false` в нашем HAR]**
включает только расширенные STRING_MULTIPLE / GAP_KEYBOARD / IMAGE_MATCH; без него
доступны 12 «старых» типов.

### 16.3. Формат `user_answer` по типам

Общий конвертер **[код]**:

```js
answer = {
  challenge_task_id:    ...,
  challenge_attempt_id: ...,
  answer: {
    "@answer_type": answerType,
    ...user_answer
  }
}
```

Для `answer/free` **[код]** вместо одного `request` отправляется FormData с несколькими
частями: `request` (Blob JSON) + по одной части `files` на каждый вложенный файл.
Остальные типы — только `request`.

| Тип | `user_answer` | Статус |
|---|---|---|
| `answer/single` | `{"id":"<option_id>"}` | **[факт]** |
| `answer/match` | `{"match":{"<source_id>":["<target_id>"]}}` | **[факт]** |
| `answer/multiple` | `{"ids":["<id>",…]}` | **[код]** (шаблон `right_answer.ids`) |
| `answer/number` | `{"number":[…]}` | **[код]** (нормализатор: `number || [0]`) |
| `answer/number/interval` | `{"number":"…"}` | **[код]** (`number || "0"`) |
| `answer/string` | `{"string":["…"],"caseSensitive":false}` | **[код]** (строки триммируются) |
| `answer/string/multiple` | `{"answers":[{"id":…,"strings":[…],"num":0,"group":0}],"case_sensitive":false,"with_order":false,"quantity":null}` | **[код]** (+ сортировка по `num`, пустые `strings` отбрасываются, `num` пересчитывается) |
| `answer/free` | `{"…":…}` + `files` в multipart; в задании поле `allow_upload_file_answer` | **[код]** |
| `answer/order` | `{"ids_order":[…]}` | **[код]** |
| `answer/order/multiple` | `{"grouped_options":[{"order_group_id":…,"ids_order":[…]}]}` | **[код]** |
| `answer/groups` | `{"groups":[{"group_id":…,"options_ids":[…]}]}` | **[код]** |
| `answer/table` | `{"cells":{"<row>":{"<col>":[…]}}}` | **[код]** (ячейки с `MANUAL_INPUT` триммируются) |
| `answer/grid` | `{"cells":[{"input_type":"MANUAL_INPUT","answer":"…"}]}` | **[код]** |
| `answer/inline/choice/single` | `{"text_position_answer":[…]}` | **[код]** |
| `answer/gap/match/text` | `{"text_position_answer":[…]}` | **[факт 22.11.2026]**; `text_input_answer` в проверенных ответах не приходил |
| `answer/gap/text/input` | `{"text_input_answer":[{"input_strings":[…],"caseSensitive":false}],"text_position_answer":[…]}` | **[код]** |
| `answer/color/match`, `answer/gap/match/text/image`, `answer/text/line_highlighting/match` | `{"text_position_answer":[…]}` | **[код]** |
| `answer/gap/match/background/image` | `{"position_answer":[…]}` | **[код]** |
| `answer/gap/match/background/image/selection` | `{"position_ids":[…]}` | **[код]** |
| `answer/match/rebus` | `{"match":[…]}` | **[код]** |
| `answer/match/timeline` | `{"match":{"<date_option_id>":["<event_option_id>"]}}` | **[код]** |
| `answer/estimation` | не тип задания, а отдельный вызов оценки (§16.5) | **[код]** |

> Для типов, помеченных **[код]**, формат выведен из шаблонов `right_answer` редактора
> и нормализатора отправки. **Более точные формы ([код-плеер]) получены из чанков плеера
> и собраны в §16.3.2–16.3.3** — это уже не догадка по шаблону, а конкретный вызов
> `setRightAnswer({...})`, который в плеере является установщиком ответа ученика.
> Сквозной проверки «отправил → получил результат» в HAR по-прежнему нет только для
> типов, не встречавшихся в записи.

Структуры `answer` задания тоже зависят от типа **[код]**: `options_order`, `options[]`,
`right_answer`, `reference_right_answer`, а также `text_input_rules`, `substitution_mode`,
`cell_types`, `image_gaps_positions`, `text_position[]`, `gaps_selection_mode`,
`option_positions`, `area_positions`, `points_distribution` и др.

### 16.3.1. Универсальная оболочка запроса и ответа

**Запрос** для любого типа задания — один и тот же **[факт для single/match, код для остальных]**:

```http
POST /webtests/exam/rest/secure/challenge/task/answer HTTP/1.1
Content-Type: multipart/form-data; boundary=<...>

------<boundary>
Content-Disposition: form-data; name="request"; filename="blob"
Content-Type: application/json

{"challenge_task_id":<task id>,"challenge_attempt_id":<attempt id>,"answer":{"@answer_type":"<тип>", ...user_answer}}
------<boundary>--
```

`user_answer` разворачивается в объект `answer` оператором `...`, то есть Имя `@answer_type`
и поля ответа лежат **на одном уровне**.

**Ответ** `task/answer` тоже не зависит от типа задания **[факт: `{"type":"EMPTY"}` для 11 ответов в HAR; код: остальные варианты]**:

```jsonc
{"type":"EMPTY"}                                                     // FACT — принято, проверки нет
{"type":"JUST_CORRECTNESS","correctness":"CORRECT"}                  // код
{"type":"JUST_CORRECTNESS","correctness":"PARTIALLY_CORRECT"}        // код
{"type":"JUST_CORRECTNESS","correctness":"INCORRECT"}                // код
{"type":"SHOW_CORRECT_ANSWER","correctness":"INCORRECT",
 "correct_answer":{...},"detail_correctness":{...},
 "hideCorrectAnswer":false}                                          // код
```

Именно этот объект фронт сохраняет как `answer_feedback` задания и использует при
отрисовке результата. Баллы за задание приходят не здесь, а в
`attempt/{aid}/result` (`score` / `max_score`).

### 16.3.2. Точные формы из чанков плеера

Нашёл место, аналогичное тому, где формируется `single`/`match`, но для всех типов сразу.
В webpack-runtime бандла есть карта имён чанков:

```js
l.u = e => "js/" + e + ".module_" + { 1360:"7cc8d", 9202:"041b1", 2256:"de96a", … }[e] + ".chunk.js"
```

А модуль `50376` сопоставляет тип задания с чанком:

```
SINGLE→1360  MULTIPLE→9202  NUMBER→2256  STRING→4032  STRING_MULTIPLE→6342
FREE→7065  ORDER→1360  GROUPS→257+6691  MATCH→257+7189  TABLE→4766  GRID→6272
INLINE_CHOICE→2040  GAP_MATCH→3980+8425  GAP_KEYBOARD→1378  TIMELINE→4616+5959+3743
IMAGE_MATCH→3496  IMAGE_SELECT→9460  NUMBER_INTERVAL→2618
```

Все чанки скачаны по исходным URL. Ключевая деталь: в **плеере** компонент задания получает

```jsx
<Component task={task} setAnswer={() => {}} setRightAnswer={setUserAnswer} />
```

То есть `setAnswer` — no-op, а `setRightAnswer` — это и есть установщик **ответа ученика**.
Поэтому вызовы вида `setRightAnswer({...})` внутри каждого чанка дают точный формат
`user_answer` — это и есть тот же уровень, на котором раньше были найдены `{id}` и `{match}`.

| Тип | Чанк | Вызов установщика | Статус |
|---|---|---|---|
| `answer/single` | 1360 | `n({id:e})` | **[факт HAR + код]** |
| `answer/multiple` | 9202 | `n({ids:t})` | **[код-плеер]** |
| `answer/number` | 2256 | `i({...right_answer, number:[…]})` | **[код-плеер]** |
| `answer/string` | 4032 | `i({...right_answer, string:[…]})` | **[код-плеер]** |
| `answer/string/multiple` | 6342 | `n({...right_answer, answers:[…]})` | **[код-плеер]** |
| `answer/free` | 7065 | `n({...Z(text), files:right_answer.files||[]})`, где `Z` = `{string:text}` или `{answer_urls:[…]}` | **[код-плеер]** |
| `answer/order` | 1360 | `ids_order` (шаблон `right_answer` в основном бандле; чанк меняет порядок options) | **[код]** |
| `answer/groups` | 6691 | `t({...n, groups:[{group_id, options_ids:[…]}]})` | **[код-плеер]** |
| `answer/match` | 7189 | `l({...right_answer, match:{source:[target]}})` | **[факт HAR + код]** |
| `answer/table` | 4766 | `t({...right_answer, cells:{…}})`, ячейки меняются через `j.AC(o.cells,e,r,n)` | **[код-плеер]** |
| `answer/grid` | 6272 | `o({...right_answer, cells:[…]})` | **[код-плеер]** |
| `answer/inline/choice/single` | 2040 | `n({...right_answer, text_position_answer:[…]})` | **[код-плеер]** |
| `answer/gap/match/text` | 3980 / 8425 | `h({text_position_answer:[…], text_input_answer:[]})` | **[код-плеер]** |
| `answer/gap/text/input` | 1378 | `w({...right_answer, text_input_answer:[…]})` | **[код-плеер]** |
| `answer/gap/match/background/image` | 3496 | `r({position_answer:[…]})` | **[код-плеер]** |
| `answer/gap/match/background/image/selection` | 9460 | `r({position_ids:[…]})` | **[код-плеер]** |
| `answer/match/timeline` | 3743 | `u({...right_answer, match:{date:[event]}})` | **[код-плеер]** |
| `answer/number/interval` | 2618 | `t({...right_answer, number:"…"})` | **[код-плеер]** |
| `answer/order/multiple`, `answer/color/match`, `answer/gap/match/text/image`, `answer/text/line_highlighting/match`, `answer/match/rebus` | stub | во **всех** проверенных бандлах вместо компонента заглушка `Zt()` | **[реализации нет]** |

**Результат поиска реализации для этих пяти типов (обновление):**

1. Скачаны **все 97 чанков** `/exam/js/*.chunk.js` (по карте из webpack-runtime
   `main.module_7c8be42c.js`) — упоминания этих типов есть только в:
   * основном бандле (enum `Q`, таблица `50376`, шаблоны `right_answer` редактора),
   * чанках `7041` и `3746` (редактор/проверка) — и там снова `(0,E.Zt)(type)`.
2. Скачан отдельный SPA-бандл редактора `/composer3/static/js/main.473bef1b.js`
   и все его 80 чанков — этих типов там **нет вообще** (ни строк, ни enum-имён).
3. Функция-заглушка устроена так:

   ```js
   function Zt(type) {
     var t = function(){ return null };
     t.preload = function(){ return new Promise(function(){}) };  // никогда не резолвится
     return t;
   }
   ```

   то есть это не «отложенная загрузка», а осознанная заглушка.

**Вывод:** в текущих сборках uchebnik эти пять типов заданий **не реализованы на клиенте** —
ни в плеере, ни в редакторе, ни в проверке. Сected-код для их формы отправки получить
нельзя. Единственное, что есть — шаблоны `right_answer` из редактора (основной бандл),
по которым можно гипотетически восстановить ожидаемую форму `user_answer`:

```jsonc
"@answer_type":"answer/order/multiple",
   "grouped_options":[{"order_group_id":"<id>","ids_order":["<id>"]}]   // шаблон right_answer
"@answer_type":"answer/color/match",           "text_position_answer":[...]
"@answer_type":"answer/gap/match/text/image",  "text_position_answer":[...]
"@answer_type":"answer/text/line_highlighting/match","text_position_answer":[...]
"@answer_type":"answer/match/rebus",           "match":[...]   // ВНИМАНИЕ: массив, не объект
```

Помечаю как **[шаблон]**, а не **[код-плеер]**: сквозного вызова `setRightAnswer` для
них не существует. `rebus` выделяется тем, что `right_answer.match` — это **массив**
(в отличие от object-карты у `match`/`timeline`).

### 16.3.3. Примеры `user_answer` для всех типов

Содержимое поля `answer` без оболочки `challenge_task_id` / `challenge_attempt_id`:

```jsonc
// FACT — из HAR
"@answer_type":"answer/single",         "id":"a9hdqt"
"@answer_type":"answer/match",          "match":{"<source_id>":["<target_id>"]}

// КОД-ПЛЕЕР — точный вызов setRightAnswer в чанке
"@answer_type":"answer/multiple",       "ids":["<option_id>","<option_id>"]
"@answer_type":"answer/number",         "number":[42]
"@answer_type":"answer/number/interval","number":"5"
"@answer_type":"answer/string",         "string":["Ответ"],"caseSensitive":false
"@answer_type":"answer/string/multiple",
        "answers":[{"id":"<uuid>","strings":["a","b"],"num":0,"group":0}],
        "case_sensitive":false,"with_order":false,"quantity":null
"@answer_type":"answer/free",           "string":"Ответ ученика","files":[]   // + answer_urls, если файлы уже загружены
"@answer_type":"answer/groups",         "groups":[{"group_id":"<id>","options_ids":["<id>"]}]
"@answer_type":"answer/table",          "cells":{"0":{"0":["значение"]}}
"@answer_type":"answer/grid",           "cells":[{"input_type":"MANUAL_INPUT","answer":"..."}]
"@answer_type":"answer/inline/choice/single",
        "text_position_answer":[{"text_id":"<id>","position_id":"<id>","id":"<option_id>"}]
"@answer_type":"answer/gap/match/text",
        "text_position_answer":[{"id":"<option_id>","position_id":"<id>"}],"text_input_answer":[]
"@answer_type":"answer/gap/text/input",
        "text_input_answer":[{"text_id":"<id>","position_id":"<id>","input_strings":["слово"],"caseSensitive":false}],
        "text_position_answer":[]
"@answer_type":"answer/gap/match/background/image",          "position_answer":[{"position_id":"<id>","id":"<option_id>"}]
"@answer_type":"answer/gap/match/background/image/selection","position_ids":["<position_id>"]
"@answer_type":"answer/match/timeline", "match":{"<date_option_id>":["<event_option_id>"]}
"@answer_type":"answer/order",          "ids_order":["<id>","<id>"]            // КОД (шаблон)

// ШАБЛОН — реализации в клиенте нет, форма выведена из right_answer редактора
"@answer_type":"answer/order/multiple",
        "grouped_options":[{"order_group_id":"<id>","ids_order":["<id>"]}]
"@answer_type":"answer/color/match",                 "text_position_answer":[...]
"@answer_type":"answer/gap/match/text/image",        "text_position_answer":[...]
"@answer_type":"answer/text/line_highlighting/match","text_position_answer":[...]
"@answer_type":"answer/match/rebus",                 "match":[...]   // массив!
```

Ответ сервера для всех перечисленных типов один и тот же — формат из §16.3.1.
Разница лишь в том, какие поля внутри `answer` передаёт клиент.

### 16.4. Фидбек и оценки (уточнение §9.3)

Перечисления из бандла **[код]**:

```
answerFeedback.type:            EMPTY | JUST_CORRECTNESS | SHOW_CORRECT_ANSWER
answerFeedback.correctness:     CORRECT | PARTIALLY_CORRECT | INCORRECT
challenge_task.status:          DONE | MISSED | AWAIT_FOR_REVIEW
teacher_assessment:             { isPosted: bool, score: number, comment: string }
```

`SHOW_CORRECT_ANSWER` дополнительно несёт `correct_answer`, `detail_correctness`,
`hideCorrectAnswer`. Визуальный тип фидбека: `correct-answer`, `wrong-answer`,
`partially-correct-answer`, `missed-answer`, `custom`, `incorrect_language`.

Правило ручной оценки **[код]**: если `teacher_assessment.isPosted` и
`0 < score < max_score` → частично верно; `score > 0` → верно; `score <= 0` → неверно.

### 16.5. Дополнительные эндпоинты, найденные в бандле

| Метод | Путь (от `/webtests/exam/rest/secure`) | Параметры | Назначение |
|---|---|---|---|
| GET | `/challenge/task/answers` | query `challenge_id`, `challenge_attempt_id` | ответы/проверка по попытке; подтверждает данные `mesh-cdz-api.md` §9, в HAR не вызывался |
| POST | `/challenge/task/answer/estimation` | JSON `{challenge_task_id, challenge_attempt_id, manual_score, criteria_score}` | выставление ручной оценки учителем |
| GET / POST | `/attempt/feedback/{challenge_attempt_id}` | POST — JSON `{rating, passing_improve, passing_error}` | **не учитель**: опрос ученика на странице результата («Нам важно ваше мнение!», «Оценить прохождение теста»); GET читает те же три поля |
| GET | `/challenge/{cid}/assignments/my?attemptId={aid}` | путь + опциональный query | список назначений с фильтром по попытке **[факт: в HAR без query]** |
| POST | `/challenge/generate_by/spec`, `/generate_by/task`, `/generate_by/binding` | JSON-объект | редактор/автор заданий, не прохождение ученика |
| GET | `/aclx/api/sessions/current`, `/aclx/api/sessions/aupd`, `/aclx/api/user` | — | старый вход/сессия (не API ЦДЗ) |

Все они идут через тот же клиент с заголовками §3.6 / §16.1.

### 16.5.1. Кто и что отправляет (роли)

| Вызов | Роль | Почему |
|---|---|---|
| `POST /challenge/{cid}/start-attempt` | ученик | старт/продолжение своей попытки **[факт HAR]** |
| `POST /challenge/task/answer` | ученик | ответ на задание **[факт HAR]** |
| `POST /challenge/{cid}/complete_attempt` | ученик | завершение своей попытки **[факт HAR]** |
| `POST /challenge/task/answer/estimation` | **учитель** | ручная оценка: `manual_score`, `criteria_score`, `teacher_assessment`, сообщение «Ваша оценка отправлена», отдельный обработчик `403` |
| `POST /attempt/feedback/{aid}` | **ученик** | форма обратной связи о самом тесте, а не об ответе; кнопка «Оценить прохождение теста», поля `rating` / `passing_improve` / `passing_error` |
| `POST /challenge/generate_by/*` | автор/редактор | генерация заданий из спецификации/задания/связки |

Важно: все эти методы лежат в **одном SPA-бандле**. То, что метод есть в коде, ещё не значит,
что он доступен ученику: сервер различает роли. Например, для ручной оценки в коде висит
отдельный обработчик `403` — типичная ситуация, когда ученик видит кнопку, но не может
отправить оценку.

**Почему шанс «сработать у ученика» очень мал (доказательства из кода):**

```js
const teacherRoles = ["TEACHER", "MASTER", "METHODIST", "LECTURER"];
mode = (currentProfile.profile_id !== attempt.profile_id
        && teacherRoles.includes(currentProfile.profileOriginalName))
       ? (attempt.complete_attempt ? "ASSESSMENT" : "TEACHER_OVERVIEW")
       : "SELF_OVERVIEW";            // для ученика
```

Панель ручной оценки рендерится только в режимах `ASSESSMENT` / `TEACHER_OVERVIEW`.
Токен ученика содержит `prf[0].type = "student"` (подтверждено HAR) и заголовок
`Profile: {"id":"1002991417","type":"student"}` — в список учительских ролей он не
попадает, поэтому режим будет `ATTEMPT` / `SELF_OVERVIEW`, а кнопки оценки просто не будет.
Плюс серверный обработчик `403` для этого конкретного вызова.

Остаётся теоретическая возможность, что сервер проверяет только «есть ли право на объект»
и не проверяет роль — но это уже надо проверять живым запросом с несуществующим
`challenge_task_id`, чтобы не менять реальные данные.

Практический вывод для PoC: из «скрытых»-методов ученику реально нужны разве что
`GET /challenge/task/answers` и `GET/POST /attempt/feedback/{aid}`; `estimation` и
`generate_by/*` — учительская/авторская сторона.

### 16.6. Точная сборка тела `start-attempt`

Функция из бандла **[код]** читает параметры страницы и строит тело:

```js
const p = urlParams;                         // actor, activityId, registration, context
body = {
  actorParam:         p.actor        || "",
  registrationParam:  p.registration || "",
  activityIdParam:    p.activityId   || "",
  resultOverviewUrl:  abs("/challenge/"+cid+"/attempt/{attemptId}"),
  resultManagementUrl: abs("/challenge/"+cid+"/context/{assignmentContextId}/attempt/{attemptId}"),
  context:            p.context      || "",
  contextCorrupted:   !(cid && p.actor && p.activityId && p.registration && p.context)
}
```

`contextCorrupted = true`, если не хватает **любого** из пяти значений — это уточнение
к §6.2. `resultOverviewUrl` / `resultManagementUrl` превращаются в абсолютные URL
относительно origin (в HAR — `https://uchebnik.mos.ru/...`).

