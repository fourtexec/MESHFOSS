# Аналитика MESHFOSS

Аналитика нужна для двух вещей: понимать, какие версии приложения реально
используются, и видеть **отфильтрованные** ошибки без токенов и персональных
данных.

## Транспорт

Аналитика использует те же правила, что и остальная сеть:

- Retrofit-контракт: `data/analytics/AnalyticsApiService.kt`;
- транспорт: `data/analytics/AnalyticsTransport.kt`;
- Gson-модели событий и состояния: `AnalyticsEvent`, `AnalyticsState`;
- JSON Lines передаётся готовым `RequestBody` с
  `Content-Type: application/stream+json`.

WebView в аналитике не используется вообще: ни скрытого, ни видимого. Все
запросы идут через Retrofit.

Endpoint:

```text
POST https://analytics.4mes.dpdns.org/insert/jsonline
```

Query:

```text
_time_field=timestamp
_msg_field=event
_stream_fields=app,version,device,level,event_type,uuid
```

Заголовок `X-Api-Key` берётся из `BuildConfig.ANALYTICS_API_KEY`. Значение
приходит из GitHub Actions secret `ANALYTICS_API_KEY`; в git и логи оно не
попадает. Если ключа нет, отправка полностью отключается.

## События

| `event_type` | Когда | `level` |
|---|---|---|
| `app_install` | первый запуск с новым UUID | `info` |
| `app_update` | версия отличается от последней отправленной | `info` |
| `app_active` | heartbeat «client alive» при запуске UI, если с прошлого heartbeat прошло ≥ 24 ч | `info` |
| `error` | ошибка, ушедшая через `MeshLog.error` или uncaught handler | `error` |

`app_install`, `app_update` и `error` не ограничены суточным окном: каждое
событие ставится в очередь и отправляется при первой возможности. `app_active`
— отдельный heartbeat, он добавляется **в дополнение** к install/update, а не
вместо них. Проверка heartbeat выполняется в `MainActivity.onCreate`, а не в
фоновом worker'е, чтобы фоновая работа не выглядела как активность
пользователя.

## Поля строки

Обязательные:

- `event` — текст;
- `timestamp` — ISO 8601 UTC с миллисекундами;
- `app` — `BuildConfig.APPLICATION_ID`;
- `version` — `BuildConfig.VERSION_NAME`;
- `device` — только производитель и модель (`Google Pixel 7`), без serial,
  Android ID и рекламного id;
- `level` — `info` / `error`;
- `event_type` — см. таблицу;
- `uuid` — случайный id установки.

Дополнительные:

- `tag` — модуль/тег (`HTTP`, `AUTH`, `CDZ`, `CRASH`, …);
- `error_type` — класс исключения;
- `stack` — укороченный стек (до 3,5 КБ), прошедший фильтрацию;
- `os` — версия Android и API level.

## UUID установки

`data/analytics/AnalyticsIdentity` хранит UUID в
`context.noBackupFilesDir/analytics_id`.

- переживает обновление приложения;
- исчезает при переустановке;
- не попадает в облачный backup и перенос на другое устройство;
- при повреждённом файле генерируется новый UUID.

## Очередь и ретраи

`data/analytics/AnalyticsQueue` хранит неотправленные события в
`noBackupFilesDir/analytics_queue.jsonl`.

- при отсутствии сети события остаются на диске;
- очередь ограничена 200 событиями и 256 КБ;
- перед отправкой режется на пачки ≤ 60 КБ;
- не более 5 HTTP-запросов в минуту;
- `429` — пауза по `Retry-After` (или 60 с);
- `401/403/415` — короткая пауза 60 с, затем очередь повторяется;
- прочие `4xx` — пауза 5 минут;
- `413` — пачка режется пополам;
- сеть/5xx — повтор позже;
- суточного блока на все события больше нет. Старый `blocked_until` из
  состояния 2.5.x сбрасывается при обновлении до 2.6.

## Что фильтруется

Перед отправкой строка проходит `Sensitive.scrub` и `AnalyticsSanitizer`.
Дополнительно к токенам/cookie удаляются:

- UUID и длинные числовые id;
- `student_id`, `person_id`, `profile_id`, `material_id`, `work_id`, `markId`,
  `entry`, `challenge`, `attemptId`;
- `context` и `activity_url` (внутри URL-encoded JSON/URL с id);
- email, телефоны;
- ключи `name`, `student_name`, `parent_name`, `class`, `school`, `login`,
  `birthdate`, `address`, `snils`, `inn`, `passport` и т.п.;
- длинные токены и JWT.

Стек ошибки тоже проходит фильтрацию. Тело ответа analytics endpoint'а не
читается и не пишется в журнал.

## Проверка

JVM-тесты:

- `AnalyticsSanitizerTest` — ПИИ/id не уходят наружу;
- `AnalyticsEventTest` — Gson-поля и JSON Lines;
- `AnalyticsIdentityTest` — UUID переживает чтение и пересоздаётся при порче;
- `AnalyticsStorageTest` — очередь и состояние;
- `AnalyticsPayloadTest` — лимит 60 КБ;
- `AnalyticsReporterTest` — install/update/heartbeat вместе, ошибки, 429,
  короткий backoff после `401`.
