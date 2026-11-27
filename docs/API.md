# API-контракт МЭШ в MESHFOSS

Все запросы идут на `https://school.mos.ru/`. Клиент — `ru.mesh.foss.data.MeshClient`,
Retrofit-контракт — `ru.mesh.foss.data.api.MeshApiService`.

## Заголовки

`MeshCore.baseHeaders()` добавляет клиентские заголовки МЭШ, `MeshCore.authHeaders()`
— авторизацию:

- `Authorization: Bearer <token>` (если токен есть);
- `Profile-Id` / `X-Mes-Profile-Id` (если известен профиль);
- `X-Mes-Subsystem: familyweb`.

Методы питания дополнительно получают cookie `aupd_token=<token>` и **не**
получают заголовок `Authorization`, как на сайте МЭШ.

## Ошибки и токен

- `401` → обновление сессии: сначала берётся более свежий `aupd_token` из
  cookie jar (его может обновить нативная ЦДЗ-сессия), затем полный OAuth по
  cookie (`CookieRefresh`), и только потом `GET /v2/token/refresh?roleId=1&subsystem=2`.
  При успехе исходный запрос повторяется с новым токеном.
- Если обновить токен не удалось — `MeshError(code = "unauthorized")`.
- `403` → `forbidden`, `404` → `not-found`, остальные коды → `http-<code>`.
- Сетевые ошибки → `network`, таймаут → `timeout`.
- Невалидный JSON → `invalid-json`.

## Основные методы

| Метод | Endpoint | Параметры |
|---|---|---|
| профиль | `GET /api/family/web/v1/profile` | — |
| профиль по personId | `GET /api/gamification/v1/profiles` | `personId` |
| предметы рейтинга | `GET /api/ej/rating/v1/rank/subjects` | `date`, `personId` |
| рейтинг класса | `GET /api/ej/rating/v1/rank/class` | `personId`, `date`, `subjectId` |
| оценки | `GET /api/family/web/v1/marks` | `student_id`, `from`, `to` |
| деталь оценки | `GET /api/family/web/v1/marks/{markId}` | `person_id`, `markId` |
| итоговые оценки | `GET /api/family/web/v1/final_marks/v2` | `person_id`, `student_id`, `academic_year_id`, `is_year_marks_only` |
| ДЗ | `GET /api/family/web/v1/homeworks` | `student_id`, `from`, `to` |
| ДЗ выполнено | `POST /api/family/web/v1/homeworks/{entryStudentId}/done` | `type` |
| снять отметку ДЗ | `DELETE /api/family/web/v1/homeworks/{entryStudentId}/done` | `type` |
| расписание | `GET /api/family/web/v1/schedule` | `student_id`, `date` |
| питание: состояние | `GET /api/food/meals/v3/clients/allowed-food-state` | `clientId` |
| питание: баланс | `GET /api/food/meals/v3/clients/balance` | `clientIds` |
| питание: меню | `GET /api/food/meals/v3/menu/complexes` | `clientId`, `from`, `to`, `withPreorder` |
| питание: заказы | `GET /api/food/meals/v3/orders` | `clientId`, `from`, `to`, `limit` |

`clientId` в API питания — JSON-объект, сериализованный в query:
`{"personId":"<guid>"}`.

### Файлы оценки

Список `/marks` отдаёт у оценки только флаг `has_files`. В детальном ответе
`/marks/{markId}` появляются массивы:

- `result_files` — файлы, прикреплённые учителем к оценке;
- `student_work_result_files` — файлы работы ученика.

Элемент файла содержит `id`, `ordinal`, `type`, `size` и `file_url`. DTO —
Gson-модели `MarkDetail` / `MarkFile`; URL используется только для потоковой
загрузки в общую папку «Загрузки» через `DownloadManager`, без открытия
браузера. Изображения дополнительно показываются встроенным просмотром.

### Время и история оценки

В детальном ответе есть `created_at` (время выставления) и `updated_at`
(время последнего изменения). Если `updated_at` отличается от `created_at`,
интерфейс показывает обе отметки времени.

Массив `history` содержит изменения оценки: `date`, `change_date`, а также
снимки `before` / `after` с полями `value`, `weight`, `is_exam`,
`control_form_name`, `comment_exists` и другими. Модели — Gson-классы
`MarkHistoryEntry` / `MarkSnapshot`. История показывается хронологически:
старые значения серым, актуальное — последним.

## Посещаемость

### Пропуски по урокам

```http
GET /api/family/web/v1/attendance?student_id=<studentId>&from=<YYYY-MM-DD>&to=<YYYY-MM-DD>
```

DTO — `AttendanceResponse`:

```json
{
  "attendance": [
    {
      "date": "2026-11-16",
      "summary": "Болеет",
      "lessons": [
        {
          "subject_id": 1,
          "subject_name": "Алгебра",
          "lesson_education_type": "LESSON",
          "reason_id": 1,
          "description": "Болеет",
          "health_status": null
        }
      ]
    }
  ],
  "days_count": 1,
  "year_description": "2026/2027"
}
```

### Фактические проходы

```http
GET /api/pass/entrances/v1/visit_durations?personId=<personId>&from=<YYYY-MM-DD>&to=<YYYY-MM-DD>
```

DTO — `VisitDurationsResponse`:

```json
{
  "payload": [
    {
      "date": "2026-11-16",
      "visits": [
        {
          "in": "08:42",
          "out": "15:10",
          "duration": "6 ч 28 мин",
          "organizationShortName": "ГБОУ Школа №…",
          "isIncomplete": false
        }
      ]
    }
  ]
}
```

Оба DTO лежат в `ru.mesh.foss.data.model.AttendanceModels`. Поля, которых нет в
ответе, остаются `null`/пустыми списками; экран не падает.

## Олимпиады

Публичный read-only раздел `/api/contests/exec/v1/` вынесен в отдельный
Retrofit-контракт `OlympiadApiService`; подробности — в
[`docs/olympiads_api.md`](olympiads_api.md).

## Москвёнок и QR-вход

QR-вход и «Москвёнок» используют отдельные сценарии (`QrLogin`, `MoskvenokApi`)
и не входят в `MeshApiService`: там нужны cookie, редиректы и HTML-разбор.
Они также не являются частью публичного REST-контракта МЭШ.
