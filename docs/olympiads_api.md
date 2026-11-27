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
- 404 для `/participant`, `/recommendation` и `/result` — нормальный ответ
  «данных ещё нет», а не ошибка экрана.

## Эндпоинты

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `contests/overview` | Список «Мои» / «Все», поиск, страницы |
| `GET` | `subjects` | Справочник предметов |
| `GET` | `parallels` | Справочник параллелей |
| `GET` | `contests/stages` | Справочник этапов |
| `GET` | `contests/levels` | Справочник уровней |
| `GET` | `contests/{id}` | Карточка олимпиады |
| `GET` | `contests/{id}/participant` | Статус участия |
| `GET` | `contests/{id}/recommendation` | Рекомендация |
| `GET` | `contests/{id}/result` | Результат |

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
Год берётся текущий. Ответ содержит `page.content[]` и `hiddenTotal`
(общее число найденных олимпиад).

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
    "detailsUrl": "…"
  }
}
```

## Ограничения

- Раздел только читает данные. Регистрация, запуск тура, отправка ответов и
  завершение попыток не вызываются.
- Сырой JSON, UUID и строки `"null"` в UI не показываются.
- В тестах и демо-режиме используются синтетические id
  `demo-olympiad-1`, `demo-olympiad-2`, `demo-olympiad-3`.
- Описание очищается от HTML и rich-text служебных полей перед показом.
