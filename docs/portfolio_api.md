# Портфолио — API раздела `/portfolio/app`

Документ описывает read-only вкладку «Портфолио» в MESHFOSS. Источник —
HAR-файл `school.mos.ru/portfolio/student/study`; живые ФИО, школа, класс,
телефоны, email, СНИЛС, UUID и токены в документ не попадают.

## Транспорт

- Retrofit + Gson, отдельный контракт `PortfolioApiService`.
- База: `https://school.mos.ru/portfolio/app/`.
- Заголовки:
  - `Authorization: Bearer <aupd_token>`;
  - `X-Mes-Subsystem: studentportfolioweb`;
  - `Accept: application/json`;
  - `Referer: https://school.mos.ru/portfolio/student/study`.
- Семейные заголовки `Profile-Id`, `Profile-Type`, `X-Mes-RoleId` и
  `Auth-Token` в этот API не отправляются.
- Все ответы обёрнуты в `{ "data": …, "result": "OK" }`; списки и вложенные
  объекты в DTO nullable, потому что Gson не вызывает конструктор data-класса.
- Раздел только читает данные. Запросы на изменение портфолио не выполняются.

## Эндпоинты

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `portfolio/users/context/` | Профиль и `meshId` ученика |
| `GET` | `persons/{id}/widgets/list` | Виджеты достижений |
| `GET` | `persons/{id}/events/list?size=9999` | Олимпиады, конкурсы, события |
| `GET` | `persons/{id}/rewards/list?size=9999` | Награды |
| `GET` | `persons/{id}/academic-performance/final-mark` | Итоговые оценки по годам |
| `GET` | `persons/{id}/v2/academic-performance/average-mark` | Средний балл и рейтинг предметов |
| `GET` | `persons/{id}/independent-diagnostic-grouped?count=2` | Диагностики по годам |
| `GET` | `persons/{id}/employments/list?size=9999` | Занятия и кружки |
| `GET` | `persons/{id}/cultural/list/?size=9999` | Культурные посещения |

`{id}` — `data.personMeshInfo.meshId` из контекста. Если контекст не вернул
`meshId`, используется `personId` текущего профиля.

## Контекст

```json
{
  "data": {
    "meshId": "00000000-0000-4000-8000-000000000000",
    "personMeshInfo": {
      "classLevel": "7",
      "schoolName": "Школа № …",
      "lastname": "…",
      "firstname": "…",
      "patronymic": "…",
      "class": "7 «Б»",
      "meshId": "00000000-0000-4000-8000-000000000000"
    }
  },
  "result": "OK"
}
```

Имя, класс и школа показываются только в интерфейсе текущего пользователя.
Они не логируются, не отправляются в аналитику и не попадают в тесты/демо:
там используются синтетические «Демидов Пётр Алексеевич», «7 «Б»»,
«Школа № 1234».

## Успеваемость

`final-mark` возвращает массив учебных годов:

```json
[
  {
    "yearTitle": "2025/26",
    "educationLevel": "Основное общее образование",
    "averageAllSubjects": 4.72,
    "subjects": [
      { "name": "Алгебра", "yearValue": "5", "yearFivePointValue": 5 }
    ]
  }
]
```

`average-mark` возвращает объект:

```json
{
  "averageMarkFive": 4.78,
  "subjectsRank": [
    { "subjectName": "Алгебра", "averageMarkFiveSubject": 4.9 }
  ]
}
```

Средний балл и предметы показываются в карточке «Успеваемость»; итоговые
оценки раскрываются по учебным годам.

## События и награды

Событие содержит `id`, `name`, `result`, `maxScore`, `startDate`, `endDate`,
`stageEvent`, `category`, `source`, `format`, `subjects`,
`participationStatus` и `levelEvent`. `result` приходит строкой, поэтому
числовое значение форматируется как `48 / 50`, а текстовое («Призёр»)
показывается как есть.

Награда содержит `name`, `date`, `category`, `statusReward`, `rewardType`,
`levelReward`, `subjectCode` и `entityId`. `entityId` связывает награду с
событием; в UI бейдж награды показывается рядом с соответствующим событием.

## Диагностики

`independent-diagnostic-grouped` возвращает годы с `diagnostics.diagnostic[]`,
`totalDiagnostic` и `bestResult`. Для каждой диагностики показываются предмет,
дата, процент, уровень и заметка. Если API вернул `url`, доступна кнопка
«Открыть результат»; ссылка открывается через `UriHandler`, не через скрытый
WebView.

## Ограничения

- Новые DTO — Gson `data class` с `@SerializedName`.
- Сетевые ошибки одного блока не скрывают остальные: экран показывает
  загруженные данные и предупреждение «Не удалось загрузить часть данных».
- Сырой JSON, UUID, email, телефон, СНИЛС и адрес в UI не показываются.
- Демо-режим и JVM-тесты используют только синтетические данные.
