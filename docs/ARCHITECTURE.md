# Архитектура MESHFOSS 2.5

Документ описывает фактическое устройство приложения после перехода на
package-by-feature. Правило простое: **новая функциональность живёт в своём
feature-пакете**, а общие вещи поднимаются в `core`, `data` или `ui` только
тогда, когда их действительно используют несколько экранов.

## Сеть: обязательный стек

Новые REST/JSON-запросы делаются только через Retrofit + Gson: контракт в
`data/api` (или рядом с фичей), DTO с `@SerializedName`, `GsonConverterFactory`.
`HttpURLConnection`, `URL.openConnection` и ручной `OkHttp Request.Builder` для
обычного JSON в новом коде запрещены. Прямой OkHttp допустим только для
redirect/cookie/OAuth, streaming/Range и скачивания файлов; `HttpURLConnection`
в существующих CDZ/Москвёнок/cookie-файлах — историческое исключение, его нельзя
копировать. Полные правила и аудит — [`AGENTS.md`](../AGENTS.md).

## Слои и пакеты

```text
ru.mesh.foss
├── app          MainActivity, Application, WorkManager-воркер
├── core         Words, UiLanguage, MeshCore (чистые правила и разбор МЭШ)
│   └── analytics  события, санитайзер, JSON Lines, время
├── data         Retrofit-клиент, сессия, cookie, обновления, Москвёнок
│   ├── api      явный контракт REST API (MeshApiService)
│   ├── model    DTO для Gson (посещаемость, проходы)
│   └── analytics Retrofit-транспорт, UUID установки, очередь, состояние
├── ui           тема, Liquid Glass / Haze, общие карточки и контролы
├── feature
│   ├── login      QR-вход, явный экспериментальный WebView-вход
│   ├── ranking    рейтинг класса
│   ├── marks      оценки, итоговые, детали оценки
│   ├── homeworks  домашние задания
│   ├── schedule   расписание и переходы в ДЗ/питание
│   ├── food       меню, баланс, заказы
│   ├── moskvenok  карта «Москвёнок»
│   ├── attendance посещаемость и проходы
│   ├── profile    профиль ученика и родителя
│   └── settings   темы, язык, обновления
└── widget       Glance-виджет и его обновление
```

### Направление зависимостей

```text
feature/*  ──►  ui, data, core
app        ──►  feature/*, ui, data, core, lock, widget
widget     ──►  data, core
data       ──►  core, data/api, data/model
ui         ──►  core
core       ──►  (ничего из перечисленного)
```

`feature`-пакеты не импортируют друг друга напрямую. Если экрану нужен общий
элемент (например, `HomeworkTarget`), он лежит в `ui` или `core`.

## Поток данных

1. `MainActivity` создаёт `SessionStore` и `MeshClient`.
2. `AppRoot` загружает профиль через `MeshClient.fetchProfile()`, выбирает
   ученика и сохраняет `studentId`, `personId`, имя ребёнка.
3. Экраны получают уже готовые `client`, `studentId`, `personId`; каждый экран
   сам управляет своим состоянием загрузки.
4. `MeshClient` — единственная точка HTTP:
   - Retrofit + Gson для типизированных ответов;
   - `JsonElement` + `MeshCore.normalize*` для исторических разделов;
   - один повтор запроса после `401`: свежий `aupd_token` из cookie jar →
     полный OAuth по cookie → `/v2/token/refresh`;
   - ошибки приводятся к `MeshCore.MeshError` с кодом (`unauthorized`,
     `network`, `http-*`).
5. `TokenRefreshWorker` раз в 12 часов продлевает сессию через тот же
   `MeshClient` и `SessionStore`.

## Журнал

Единый журнал приложения разделён на три части:

- `core/MeshLog` — ядро без Android: очередь строк, метка времени, санк-вывод
  через `sink`; при выключенном журнале вызовы почти бесплатны. Отдельный
  `errorSink` отдаёт ошибки в аналитику даже при выключенном локальном журнале;
- `core/Sensitive` — общая редакция секретов: `Authorization`/`Auth-Token`,
  cookie, sessionid, OAuth `code`/`state`, `Profile-Id`, ПИИ и JWT. Дополнительно
  маскируются `student_id`/`person_id`/`material_id`/`work_id`/`markId`,
  `context`/`activity_url`, UUID, длинные числовые id, email и телефоны. Regex
  компилируются поштучно и обёрнуты в `runCatching`, поэтому некорректный
  шаблон не роняет приложение;
- `data/MeshLogStore` — Android-сторона: переключатель в Настройках, запись в
  `filesDir/mesh.log` (усечение) и logcat (`adb logcat -s MeshLog`).

`MeshClient` пишет все HTTP-запросы под тегом `HTTP`, продление токена — под
`AUTH`; экран «Москвёнок» — под тегом `Moskvenok`.

## Аналитика

`data/analytics` — отдельный слой, не влияющий на работу приложения:

- `AnalyticsApiService` + `RetrofitAnalyticsTransport` — Retrofit-контракт и
  JSON Lines через `RequestBody` (`application/stream+json`);
- `AnalyticsEvent`/`AnalyticsState` — Gson-модели;
- `AnalyticsIdentity` — UUID в `noBackupFilesDir`: переживает обновление, не
  переживает переустановку и не уходит в backup;
- `AnalyticsQueue` — дисковая очередь (200 событий / 256 КБ), пачки ≤ 60 КБ,
  не более 5 запросов в минуту;
- `AnalyticsReporter` — install/update/heartbeat, ошибки, дедупликация, backoff;
- `AnalyticsSanitizer` — дополнительная редакция ПИИ и id перед отправкой.

`app_install`, `app_update` и `error` ставятся в очередь при каждом событии и
не подавляются суточным окном. `app_active` — отдельный heartbeat «client
alive»; он проверяется в `MainActivity.onCreate` раз в 24 часа и добавляется
дополнительно к install/update. Ошибки приходят из `MeshLog.error` и uncaught
handler. Ключ endpoint'а — GitHub secret `ANALYTICS_API_KEY`, в код и логи не
попадает. Подробности — [`ANALYTICS.md`](ANALYTICS.md).

## Посещаемость

`feature/attendance` разделён на три части:

- `AttendanceRepository` — контракт загрузки;
- `MeshAttendanceRepository` — два запроса МЭШ параллельно по смыслу, но
  последовательно в коде: пропуски и проходы;
- `AttendanceViewModel` — неделя, загрузка, ошибки, обновление.

Используются только подтверждённые endpoint'ы:

- `GET /api/family/web/v1/attendance?student_id=…&from=…&to=…`
- `GET /api/pass/entrances/v1/visit_durations?personId=…&from=…&to=…`

Шлюз `pass/entrances` отвечает `401`, если пришли **оба** заголовка
`Authorization` и `Auth-Token`, и принимает любой из них по отдельности.
Поэтому `visit_durations` запрашивается с `includeAuthToken = false` (только
Bearer); для `family/web` допустимы оба.

## Виджет

`widget/WidgetUpdater` загружает компактный снимок (расписание на день +
невыполненное ДЗ, привязанное к урокам по предмету) и кладёт его в
`SessionStore` (обычный приватный кэш). Виджеты **не ходят в сеть во время
отрисовки**: Glance-композиция читает готовый снимок и сразу рисует UI. После
обновления снимка вызывается `updateAll` для всех трёх виджетов:

- `MeshWidget` — текущий/следующий урок и обратный отсчёт;
- `ScheduleWidget` — расписание на день с выделением текущего урока;
- `HomeworkWidget` — список невыполненных заданий.

У каждого своего `GlanceAppWidgetReceiver` и `res/xml/*_widget_info.xml`.

Главное виджета — «что сейчас и что дальше»: текущий урок с обратным отсчётом
до конца, следующий урок с отсчётом до начала и его ДЗ. Отсчёт делает нативный
`Chronometer` (`AndroidRemoteViews`), поэтому он тикает сам и не требует
опроса сети. Состояние «уроков больше нет» и пустое состояние тоже явные.

## Нижняя навигация

Внизу остаются четыре частых раздела:

1. Рейтинг
2. Оценки
3. ДЗ
4. Расписание

По умолчанию внизу ровно эти четыре раздела; пятый элемент — **«Ещё»**
(посещаемость, «Москвёнок», питание, настройки). Состав редактируется кнопкой
«Редактировать» в шторке «Ещё»: перетаскивание меняет порядок, кнопки ± переносят
раздел между панелью и «Ещё». Панель ограничена четырьмя вкладками; при меньшем
числе вкладки распределяются поровну (`weight(1f)` во всех трёх таб-барах).
Раскладка хранится в `mesh_settings` (`nav_bottom` / `nav_more`). В liquid-
дизайне шторка использует тот же `dialogGlass()`, что и диалоги; при drag
масштаб иконок не меняется — двигается только строка.

## Инварианты интерфейса

- Liquid Glass: базовый слой `io.github.kyant0:backdrop` (`bgBackdrop` для карточек)
  и комбинированный `chromeBackdrop` (`rememberCombinedBackdrop(bgBackdrop, contentBackdrop)`)
  для плавающего таб-бара, скользящей `Capsule`-линзы, шапки и FAB; `Design.LIQUID`
  включён по умолчанию (`Design.migrate` / `Design.DEFAULT`).
- Иконки не увеличиваются при нажатии/перетаскивании; двигается только стекло.
- Линза следует за пальцем, замедляется у центров вкладок и выбирает ближайшую
  левую вкладку при движении справа налево.
- Видимый вход — только QR-код. Дополнительно за красной экспериментальной
  кнопкой доступны явное окно WebView `school.mos.ru` и ручной токен.
- WebView — только видимый интерактивный вход/сторонняя HTML-страница. Нельзя
  использовать WebView, `loadUrl()` или `@JavascriptInterface` как транспорт для
  JSON/API/аналитики: для данных есть Retrofit + Gson. Скрытых/фоновых WebView
  нет; старые `MeshLoginActivity` и `CdzActivity` удалены. Правило
  зафиксировано в [`AGENTS.md`](../AGENTS.md).
- Поддерживается только МЭШ / `school.mos.ru`; «Моя школа» не является целью.

## Аккаунты

`data/AccountRegistry` хранит список аккаунтов и активный id в общих настройках.
Каждый аккаунт использует свой `SessionStore(context, scope)` — отдельные файлы
`mesh_session_<scope>` и `mesh_cache_<scope>`, поэтому токены, куки и все кэши
(имена, рейтинг, оценки, снимок виджета) не смешиваются. Легаси-сессия остаётся
в файлах без префикса и зарегистрирована как аккаунт по умолчанию.

Экспериментальный вход по cookie JSON (`data/CookieJson`) приводит массив
браузерного экспорта (`name`/`value`) к плоскому jar, обновляет токен через
`CookieRefresh` и создаёт новый аккаунт. Виджеты и фоновое обновление читают
активный scope.

## Нативное ЦДЗ

`data/CdzNativeClient` реализует протокол `docs/cdz_api.md`: refresh сессии
uchebnik, `start-attempt`, `task/answer` (multipart) и `complete_attempt`.
`app/CdzNativeActivity` показывает сетку заданий (синий — сохранено на сервере,
красный — отвечено, но не сохранено, серый — не начато), сохраняет черновики
локально **без автосохранения** и спрашивает подтверждение перед завершением,
если не все задания решены/сохранены. При старте новой попытки верные ответы
из прошлых попыток подставляются только в пустые черновики и только для
полностью совпадающих заданий (порядок вариантов ответа может отличаться);
каждый такой ответ всё равно нужно сохранить вручную. Экран использует
M3E-переходы, свайп между заданиями и адаптивные inline-поля. В настройках
переключатель «Нативное решение ЦДЗ» включён по умолчанию; при желании ЦДЗ
можно открывать в веб-окне.
