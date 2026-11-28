# Олимпиады — API раздела `/contests`

Документ описывает публичный read-only раздел «Олимпиады» в MESHFOSS.
Источник — локальные HAR-файлы и текущий веб-бандл `school.mos.ru/contests`;
живые токены, cookie, ФИО и номера участников в документ не попадают.

## Транспорт

- Retrofit + Gson, отдельный контракт `OlympiadApiService`.
- База: `https://school.mos.ru/api/contests/exec/v1/`.
- Заголовки:
  - `Authorization: Bearer <aupd_token>`;
  - `X-Mes-Subsystem: constuweb`;
  - `Accept: application/json`.
- Семейные заголовки `Profile-Id`, `Profile-Type`, `X-Mes-RoleId` и
  `Auth-Token` в этот API не отправляются.
- `404` от `/participant` — это не «результатов пока нет», а «ученик не
  участвовал в олимпиаде». Приложение всё равно проверяет `/result`, но
  вкладку «Результаты» показывает только при участии и непустой модели
  результата. Если участия нет, остаётся только «О программе».
- `204` от `/recommendation` — нормальное отсутствие рекомендации, не ошибка.
- `404` от `/result` и `/recommendation` обрабатывается как отсутствие
  подресурса, но не подменяет статус участия.

## Эндпоинты

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `contests/overview` | Список «Мои» / «Все», поиск, страницы |
| `GET` | `subjects` | Справочник предметов |
| `GET` | `parallels` | Справочник параллелей |
| `GET` | `contests/stages` | Справочник этапов |
| `GET` | `contests/levels` | Справочник уровней |
| `GET` | `years` | Доступные годы проведения |
| `GET` | `contests/{id}` | Карточка олимпиады |
| `GET` | `contests/{id}/participant` | Статус участия |
| `GET` | `contests/{id}/recommendation` | Рекомендация |
| `GET` | `contests/{id}/result` | Результат |
| `GET` | `contests/{id}/result/{resultId}/url-details/link` | Ссылка на страницу попытки |

## Нативный результат попытки

`detailsUrl` не открывается в браузере. Приложение:

1. делает авторизованный `GET` по `detailsUrl`;
2. получает `{ "link": "https://…" }`; это может быть как прямая страница
   `uchebnik.mos.ru/contests/challenge/{challenge}/attempt/{attempt}`, так и
   launcher-ссылка `school.mos.ru/api/launcher/v1/launch` с URL-encoded
   `activity_url`;
3. декодирует ссылку и извлекает `challenge` и `attempt`;
4. читает результат через отдельный Retrofit-контракт
   `OlympiadAttemptApiService`:

```http
GET https://uchebnik.mos.ru/webtests/contests/rest/secure/challenge/task/answers
    ?challenge_id={challenge}
    &challenge_attempt_id={attempt}
```

Заголовки и cookie-сессию даёт тот же `uchebnik`-контур, что и у ЦДЗ
(`X-Mes-Subsystem: testw`, `Authorization: Bearer <accessTokenEom>`,
`User-Id`, `Profile-Id`, `Profile`, cookie). Экран показывает сводку попытки,
задания, ответ ученика, правильный ответ и баллы. Запросы `start-attempt`,
отправка ответов и `complete_attempt` не вызываются.

### Условие и формулы

`question_elements[]` может быть `content/text` с отдельным массивом
`content[]`. Элементы `content/math` содержат LaTeX-подобный `content` и
`position` — смещение в символах внутри родительского `text`, куда формулу
нужно вставить. Это не отдельный блок после текста: если отрисовать `text`,
а затем все `content/math`, формулы уедут в конец задания. Чистый парсер
`mergeQuestionText` вставляет их в `position`, добавляет пробелы на границе
слова, убирает пробелы перед `.,;:!?` (кроме многоточия) и сдвигает
`text_styles`.

`parseMath`/`CdzMathInline` разбирают вложенные `\frac`, `\sqrt`, `\text`,
`\mathbf`, `\mathit`, `\overline`, `\underline`, степени и индексы. Дроби и
корни рисуются как Compose-контент, а не как `√(...)`. Простые односимвольные
степени (`x^3`) остаются Unicode-символами для компактности, но произвольные
индексы, включая кириллицу (`p_{мат}`, `v_{М0}`), получают
`BaselineShift.Subscript`/`Superscript` и не теряют размер.

`text_styles.text_block_index` считается по неатомарным блокам Draft.js:
`content/math` и пропуски вставляются как atomic-блоки. Поэтому стили
применяются к исходным координатам с учётом вставок; `SUPER`, `SUB`,
`SUBSCRIPT`, `SUPERSCRIPT` и родственные алиасы нормализуются.

`groups_context_info` — общее условие группы. Объект с ключом группы содержит
`caption` и `content` (`text`, `content[]`, `text_styles`). Приложение
показывает его отдельной карточкой перед заданиями; формулы и изображения
внутри `content[]` вставляются в текст по `position`, а не дублируются.

### Ответы с пропусками

`answer/gap/text/input` хранит ответ не в `string`/`number`, а в
`user_answer.text_input_answer[].input_strings[]`. Правильный ответ в
`answer_feedback.correct_answer` имеет ту же форму. Пустые `input_strings`
означают пропуск, а не ошибку разбора.

Массив `text_input_answer[]` приходит в произвольном порядке, а не в порядке
пропусков. `gapAnswers` сопоставляет `position_id` с `answer.text_position[]`
и сортирует строки по номеру пропуска; иначе «Ваш ответ» и «Правильный ответ»
выглядят перепутанными. Если у одного пропуска несколько `input_strings`,
это допустимые варианты правильного ответа: они показываются через ` / `.

`answer.text_position[].position` — смещение в исходном `text`, а не в тексте
после вставки формул. `mergeQuestionText` принимает список gap-ответов и
вставляет их в те же координаты, что и `content/math`, сохраняя формулы на
местах. В результате ответ ученика отображается inline внутри условия, как в
веб-версии, а не отдельным списком. Номера пропусков остаются только в блоке
правильного ответа.

### Порядок вариантов и сопоставлений

`answer/options[]` и `answer/match` не гарантируют порядок, совпадающий с
отображением. Для `answer/multiple` выбранные id сортируются по порядку
`answer.options[]`, а пары `answer/match` — по позиции source-варианта. Так
«Ваш ответ» и «Правильный ответ» показывают одни и те же элементы в одной
последовательности. В `answer/number` и `answer/string` правильный ответ тоже
может содержать несколько допустимых значений: они показываются через ` / `.

### Задания с выбором позиций на изображении

`answer/gap/match/background/image/selection` хранит фон в
`answer.background_image` (`relative_url`/`preview_url`, `description`), а
позиции — в `answer.image_gaps_positions[].position_id`. Ответ и правильный
ответ лежат в `position_ids[]`. Номера позиций берутся из порядка
`image_gaps_positions[]`, а не из порядка `position_ids[]`.

## `POST contests/overview`

Тело запроса:

```json
{
  "page": { "number": 0, "size": 20 },
  "searchString": "",
  "sort": { "fieldName": "startAt", "order": "DESC" },
  "subjectIds": [],
  "stageCodes": [],
  "parallelIds": [],
  "years": [2026],
  "includeCurrent": true,
  "includeFuture": true,
  "includeCompleted": true,
  "showParticipantContests": true
}
```

`showParticipantContests: true` — «Мои олимпиады», `false` — «Все олимпиады».
По умолчанию выбран текущий год, как в веб-форме; пустой `years` означает
«все годы». Ответ содержит `page.content[]` и `hiddenTotal` (общее число
найденных олимпиад).

Фильтры повторяют веб-форму: предметы (`subjectIds`), этапы (`stageCodes`),
параллели (`parallelIds`), годы (`years`) и статусы (`includeCurrent` —
идущие, `includeFuture` — предстоящие, `includeCompleted` — завершённые).
Выбранные значения отправляются в `POST contests/overview`; активные фильтры
показываются чипами над списком и снимаются нажатием на чип. «Сбросить»
возвращает текущий год и все статусы.

Элемент списка: `id` (строка или `{ "value": "…" }`), `contestId`,
`contestName`, `startAt`, `finishAt`, `statusCode`, `subjectIds`,
`parallelIds`, `stageCode`, `teamContest`, `recommendationExists`.

## Карточка и результаты

`GET contests/{id}` отдаёт `contestName`, `statusCode`, `commonInfo`
(`stageCode`, `subjectIds`, `levelCode`), `participationInfo`
(`parallelIds`, `teamContest`, `startAt`, `finishAt`), `additionalInfo`
(`contestDescription` — JSON/HTML rich-text, `testContests` — ссылки) и
`prevContestStages` (`subjectIds`, `parallelIds`, `stageCode`, `startedAt`,
`rewards`, `minRank`, `minMark`).

`GET contests/{id}/result`:

```json
{
  "hasResult": true,
  "resultModel": {
    "startedAt": "…",
    "finishedAt": "…",
    "score": {
      "mark": 18,
      "markScale": 25,
      "passedCount": 7,
      "totalCount": 10,
      "partialPassedCount": 2,
      "wrongCount": 1
    },
    "reward": { "code": "WINNER", "name": "Победитель" },
    "participationStatus": {
      "code": "IN_COMPETITION",
      "name": "В основном зачёте",
      "justification": "…"
    },
    "portfolioInfo": "…",
    "detailsUrl": "/api/contests/exec/v1/contests/{id}/result/{resultId}/url-details/link"
  }
}
```

`detailsUrl` — не страница для браузера. Приложение делает по нему
авторизованный `GET`, получает `{ "link": "https://…" }`, декодирует
launcher-ссылку при необходимости, разбирает ID попытки и открывает
результат нативно. Прямое открытие `detailsUrl` показывает JSON/ошибку API,
а браузерная страница `link` не нужна.

`/participant` также отдаёт `resultsPublicationInfoCode` и
`checkResultsLink`. Код используется только для текста в пустой вкладке
результатов; `checkResultsLink` показывается кнопкой «Проверить баллы»,
когда результаты ещё не загружены в МЭШ.

## Программистские олимпиады (`vrlabs/informatics`)

Результат может вести не на `uchebnik`, а на
`school.mos.ru/vrlabs/informatics/student/olympiad/competition/{competitionId}/participation/results/{assignmentId}`.
Ссылка может быть завёрнута в launcher-ссылки; `ProgrammingOlympiadLink`
декодирует её и достаёт `competitionId`/`assignmentId`.

Чтение результата:

- `GET vrlabs/informatics/api/olympiad/result-page/{competitionId}` — название,
  описание, инструкция, лимит времени;
- `GET vrlabs/informatics/api/pass-test/{assignmentId}/state` — статус,
  баллы, дата завершения, `autoCheckResults`, список заданий;
- `GET vrlabs/informatics/api/pass-test/{assignmentId}/task/{taskId}` —
  описание задания, форматы ввода/вывода, примеры, `taskAssignment`.

Контракт — Retrofit + Gson (`ProgrammingApiService`,
`ProgrammingModels`). OAuth/cookie-обмен и launcher-редиректы используют
OkHttp как протокольное исключение; это только авторизация и `GET`, без
`start-attempt`, отправки ответов и `complete_attempt`.

OAuth: `GET /v1/oauth/authorize` с cookie-сессией даёт callback с `code` и
`state`, затем `POST /vrlabs/informatics/api/authentication/token` с
`launchContext` возвращает Bearer-токен. Токен и cookie не логируются и не
попадают в документ/аналитику.

Описание задания — HTML или LaTeX (`descriptionFormat`). `CdzHtml` разбирает
разрешённые теги (`p`, `div`, `span`, `br`, `li`, `ul`, `ol`, `img`,
`center`, `legend`, `note`, `input-specification`, `output-specification`,
`scoring`), формулы `$$$…$$$`, `\(...\)`, `\[...\]`, editor
`data-w-e-type="formula"` и `math-tex`. Одиночный `$...$` остаётся текстом:
в задачах он слишком часто оказывается валютой. Скрытые `invisibleText` с
`font-size: 0`, белым цветом или `display:none` пропускаются вместе с
вложенными формулами, чтобы не показывать prompt-injection текст.

UI показывает сводку, общее описание/инструкцию, задания с вердиктом и
баллами, примеры, ответ ученика (`answersB`) или исходный код
(`taskAssignment.content`), комментарий учителя. Правильный ответ
показывается, если сервер его отдал; иначе остаются вердикт и баллы.

## Ограничения

- Раздел только читает данные. Регистрация, запуск тура, отправка ответов и
  завершение попыток не вызываются.
- Сырой JSON, UUID и строки `"null"` в UI не показываются.
- В тестах и демо-режиме используются синтетические id
  `demo-olympiad-1`, `demo-olympiad-2`, `demo-olympiad-3`.
- Описание очищается от HTML и rich-text служебных полей перед показом.
