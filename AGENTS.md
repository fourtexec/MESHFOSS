# AGENTS.md — обязательные правила MESHFOSS

Этот файл — источник истины для агентов и контрибьюторов. Если привычный способ
кажется проще, но нарушает правила ниже — соблюдаем правила.

## 1. Сеть: только Retrofit + Gson

**Любой новый REST/JSON-запрос обязан идти через Retrofit + Gson.**

Канонический образец в проекте:

- `app/src/main/java/ru/mesh/foss/data/MeshClient.kt`
- `app/src/main/java/ru/mesh/foss/data/api/MeshApiService.kt`
- `app/src/main/java/ru/mesh/foss/data/model/AttendanceModels.kt`

Правила:

1. Контракт API — отдельный Retrofit-интерфейс в `data/api` или рядом с фичей.
2. JSON-модели — Kotlin `data class` с `@SerializedName`.
3. Retrofit собирается с `GsonConverterFactory` и общим `GsonBuilder`.
4. Для новых запросов **запрещено** использовать:
   - `HttpURLConnection`;
   - `URL.openConnection()`;
   - ручной `OkHttpClient.newCall(Request.Builder()...)` для обычного JSON/REST;
   - `org.json.JSONObject` как сетевую модель нового API.
5. `OkHttp` напрямую допустим только там, где нужны протокольные возможности,
   которых нет в обычном Retrofit-вызове:
   - отключение redirects и ручной разбор `Location`;
   - OAuth/cookie-обмен;
   - streaming/Range, скачивание файлов, изображений и видео;
   - raw-тело не-JSON формата.
6. Если тело запроса — не JSON-объект (JSON Lines, бинарник, media), это всё равно
   Retrofit-вызов: передаём готовый `RequestBody` с явным `Content-Type`.
   Нельзя уходить из Retrofit в `HttpURLConnection` только из-за необычного тела.
7. Новые сетевые библиотеки не добавляем без отдельного решения.

### WebView: не транспорт для API

WebView нельзя использовать как замену Retrofit для JSON/REST, аналитики или
получения данных. Разрешён только **видимый пользователю** WebView для
интерактивного входа/страницы, которую нельзя получить обычным REST-запросом.

Запрещено:

- скрытый/фоновый WebView без окна и явного действия пользователя;
- `@JavascriptInterface`-мост для вытаскивания токенов/JSON из `fetch`/`XHR`;
- `loadUrl()`/`evaluateJavascript()` как способ выполнить API-запрос;
- новый WebView-экран «на всякий случай» вместо Retrofit-контракта.

Старые мёртвые `MeshLoginActivity` (скрытый WebView с JS-перехватом) и
`CdzActivity` (неиспользуемый WebView для ЦДЗ) удалены. Не возвращать их и не
добавлять аналоги. `SchoolLoginActivity` остаётся явным экспериментальным
окном входа, а не сетевым транспортом.

Если данные нужны приложению — это Retrofit + Gson. Если это сторонняя
HTML-страница для пользователя — это видимый WebView.

### Проверка перед PR

```bash
rg -n "HttpURLConnection|openConnection|Request\\.Builder|org\\.json" app/src/main/java
```

Каждое новое совпадение должно быть объяснено как допустимое исключение из п. 5.
Если объяснения нет — код надо переписать на Retrofit + Gson.

## 2. Аудит существующего кода

В проекте есть исторические исключения. Их **нельзя копировать в новый код**.
При существенном изменении такого файла нужно по возможности переводить его на
Retrofit + Gson, не ломая поведение и тесты.

| Файл | Сейчас | Статус |
|---|---|---|
| `data/MeshClient.kt`, `data/api/MeshApiService.kt` | Retrofit + Gson | канонический путь |
| `data/UpdateChecker.kt` | `HttpURLConnection` + `org.json` | legacy; допустимо только до переписывания |
| `data/MoskvenokApi.kt` | `HttpURLConnection`, cookie, redirects | special; не копировать |
| `data/CdzSessionBootstrap.kt` | `HttpURLConnection`, OAuth/cookie | special; не копировать |
| `data/CdzNativeClient.kt` | `HttpURLConnection`, cookie, redirects | special; не копировать |
| `data/CookieRefresh.kt` | `HttpURLConnection`, OAuth/cookie | special; не копировать |
| `feature/cdz/CdzImage.kt` | `HttpURLConnection` для байтов изображения | special; streaming |
| `core/MeshCore.kt`, `data/SessionStore.kt`, `widget/*` | `org.json` | legacy/хранение/UI-интероп; новые сетевые модели — Gson |

`org.json` допустим для уже существующего разбора, хранения и интеропа с UI.
Новый сетевой ответ, новый DTO и новая аналитика — только Gson.

## 3. Аналитика

Аналитика сделана по общим правилам сети:

- транспорт: `data/analytics/AnalyticsTransport.kt` — Retrofit;
- контракт: `data/analytics/AnalyticsApiService.kt`;
- события и состояние: Gson (`AnalyticsEvent`, `AnalyticsState`);
- endpoint: `https://analytics.4mes.dpdns.org/`;
- `Content-Type: application/stream+json`, JSON Lines;
- ключ: `BuildConfig.ANALYTICS_API_KEY`, только из GitHub secret
  `ANALYTICS_API_KEY`. Ключ нельзя коммитить, логировать или печатать в CI.

Правила аналитики:

1. UUID установки — случайный, хранится в `noBackupFilesDir`:
   переживает обновление, не переживает переустановку, не уходит в backup.
2. События: `app_install`, `app_update`, `app_active`, `error`.
3. `app_active` отправляется при запуске UI, если прошло больше 24 часов.
4. Ошибки отправляются после `Sensitive.scrub` + `AnalyticsSanitizer`.
5. В аналитику не попадают токены, cookie, ФИО, email, телефоны, UUID материалов,
   `student_id`, `person_id`, `markId`, `context`, `activity_url`, длинные
   числовые id и прочие минимально приватные данные.
6. Лимиты: не более 5 запросов в минуту, тело — до 64 КБ (клиент режет на 60 КБ).
7. Ответы endpoint'а не логируем и не читаем в журнал.
8. Новые поля аналитики добавлять только после проверки, что они не ПИИ.

## 4. Безопасность и приватность

- Не коммитить и не цитировать `log.txt`, `refresh_mos.py`, cookie/token-файлы
  и другие локальные артефакты с секретами.
- Не писать в журнал/аналитику токены, cookie, имена, телефоны, email, адреса,
  номера учеников, материалы, попытки.
- Все новые строки журнала должны проходить через `Sensitive.scrub`.
- Секреты — только через GitHub Actions secrets.

## 5. Архитектура и UI

- Сохранять модульность: сеть — `data`, чистая логика — `core`, экраны — `feature`.
- Сохранять Material 3 Expressive: motion, shape/color morphing, gestures,
  Liquid Glass, shapes, lens, shader.
- Не масштабировать иконки при нажатии/перетаскивании.
- QR-вход — единственный видимый способ входа.
- CDZ: только read-only `GET`/`POST` для чтения. Никогда не вызывать
  `start-attempt`, отправку ответа или `complete_attempt` из новых изменений.
- Статус CDZ брать из серверных attempts/results; не показывать сырой JSON,
  UUID и ответы `"null"`.
- Мультиаккаунт: у каждого аккаунта отдельные токены, cookie и кэши.

## 6. Сборка, тесты, релизы

- Локально Android SDK/NDK/Gradle не запускать. Сборка и тесты — только GitHub
  Actions.
- JVM-тесты обязательны для чистой логики и фильтров.
- Стабильный релиз: только `main` + тег `v*` (например, `v2.5.2`), не pre-release.
- Non-main ветки — только `Android pre-release`.
- При push stable-тега ночная сборка не должна публиковаться параллельно.
- Перед push: рабочее дерево чистое, секретов нет, CI-конфиг обновлён.

## 7. Definition of Done

- Нет новых `HttpURLConnection`/`URL.openConnection`/ручных JSON-запросов.
- Новые DTO — Gson + `@SerializedName`.
- Тесты добавлены/обновлены.
- CI зелёный.
- Секреты и ПИИ не попали в git, логи, аналитику или релизные заметки.
- Документация обновлена, если изменился контракт или поведение.
