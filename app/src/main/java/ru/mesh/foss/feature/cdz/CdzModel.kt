package ru.mesh.foss.feature.cdz

import org.json.JSONArray
import org.json.JSONObject

/**
 * Чистая модель задания ЦДЗ: без Android/Compose.
 *
 * Парсинг отделён от экрана и сетевого клиента, поэтому его легко покрыть
 * тестами и переиспользовать в других режимах (просмотр результата, черновики).
 */
data class CdzTask(
    val id: Long,
    val group: Int,
    val order: Int,
    val type: String,
    val question: JSONArray,
    val answer: JSONObject,
    val serverAnswer: JSONObject?,
)

/**
 * Стилевой диапазон API (`text_styles`).
 *
 * Позиции приходят в UTF-16 координатах исходной строки. Диапазон намеренно
 * не меняет текст: формулы в API лежат обычными символами (`H2O`), а
 * `SUB`/`SUP` лишь говорят, как их показать.
 */
data class CdzTextSpan(
    val start: Int,
    val end: Int,
    val tokens: Set<String>,
)

/** Один вариант ответа: текст, картинка, видео и роль в сопоставлении. */
data class CdzOption(
    val id: String,
    val text: String,
    val imageUrl: String,
    val description: String,
    val source: Boolean,
    val groupId: String = "",
    val textSpans: List<CdzTextSpan> = emptyList(),
    val video: JSONObject? = null,
)

/**
 * Таблица `answer/table`.
 *
 * API хранит заголовки прямо в `answer.options[0].content[0].table.cells`
 * (строка `0` — заголовок столбцов), а типы ячеек — в `answer.cell_types`.
 * Поэтому таблицу нельзя разобрать как обычный список вариантов.
 */
data class CdzTable(
    val rows: Int,
    val columns: Int,
    val columnHeaders: Boolean,
    val rowHeaders: Boolean,
    val cells: Map<Pair<Int, Int>, String>,
    val inputCells: Set<Pair<Int, Int>>,
) {
    val headerRows: Int get() = if (columnHeaders) 1 else 0
    val headerColumns: Int get() = if (rowHeaders) 1 else 0

    fun value(row: Int, column: Int): String = cells[row to column].orEmpty()

    fun isInput(row: Int, column: Int): Boolean = row to column in inputCells
}

/** Пропуск в тексте `answer/inline/choice/single`. */
data class CdzInlineChoice(
    val positionId: String,
    val textId: String,
    val position: Int,
    val options: List<CdzOption>,
)

/** Выбранный вариант для одного пропуска inline-задания. */
data class CdzInlineSelection(
    val textId: String,
    val positionId: String,
    val optionId: String,
)

/** Стабильный порядок заданий: группа, затем task_order внутри группы. */
fun parseTasks(attempt: JSONObject): List<CdzTask> {
    data class IndexedTask(
        val groupOrder: Int,
        val groupIndex: Int,
        val taskIndex: Int,
        val task: JSONObject,
    )

    val indexed = mutableListOf<IndexedTask>()
    val groups = attempt.optJSONArray("challenge_test_groups") ?: return emptyList()
    for (groupIndex in 0 until groups.length()) {
        val group = groups.optJSONObject(groupIndex) ?: continue
        val groupOrder = group.optInt("test_group_num", groupIndex)
        val tasks = group.optJSONArray("challenge_tasks") ?: continue
        for (taskIndex in 0 until tasks.length()) {
            val task = tasks.optJSONObject(taskIndex) ?: continue
            if (task.optLong("id") > 0L) {
                indexed += IndexedTask(groupOrder, groupIndex, taskIndex, task)
            }
        }
    }
    return indexed
        .sortedWith(
            compareBy(
                { it.groupOrder },
                { it.groupIndex },
                // task_order задаётся внутри группы; отсутствие поля не должно
                // выбрасывать задание в конец.
                { it.task.optInt("task_order", it.taskIndex) },
                { it.taskIndex },
            )
        )
        .map { indexedTask ->
            val task = indexedTask.task
            val answer = task.optJSONObject("answer") ?: JSONObject()
            CdzTask(
                id = task.optLong("id"),
                group = indexedTask.groupOrder,
                order = task.optInt("task_order", indexedTask.taskIndex),
                type = answerType(answer),
                question = task.optJSONArray("question_elements") ?: JSONArray(),
                answer = answer,
                serverAnswer = task.optJSONObject("user_answer"),
            )
        }
}

/** Задание в уже завершённой попытке: результат, ответ ученика и фидбек. */
data class CdzResultTask(
    val id: Long,
    val group: Int,
    val order: Int,
    val question: JSONArray,
    val answer: JSONObject,
    val userAnswer: JSONObject?,
    val feedback: JSONObject?,
    val score: Double,
    val maxScore: Double,
    val status: String,
)

/** `attempt/{id}/result` → задания в том же групповом порядке, что и при прохождении. */
fun parseResultTasks(result: JSONObject): List<CdzResultTask> {
    data class IndexedTask(
        val groupOrder: Int,
        val groupIndex: Int,
        val taskIndex: Int,
        val task: JSONObject,
    )

    val indexed = mutableListOf<IndexedTask>()
    val groups = result.optJSONArray("results") ?: return emptyList()
    for (groupIndex in 0 until groups.length()) {
        val group = groups.optJSONObject(groupIndex) ?: continue
        val groupOrder = group.optInt("test_group_num", groupIndex)
        val tasks = group.optJSONArray("challenge_tasks") ?: continue
        for (taskIndex in 0 until tasks.length()) {
            val task = tasks.optJSONObject(taskIndex) ?: continue
            if (task.optLong("id") > 0L) {
                indexed += IndexedTask(groupOrder, groupIndex, taskIndex, task)
            }
        }
    }
    return indexed
        .sortedWith(
            compareBy(
                { it.groupOrder },
                { it.groupIndex },
                { it.task.optInt("task_order", it.taskIndex) },
                { it.taskIndex },
            )
        )
        .map { indexedTask ->
            val task = indexedTask.task
            val answer = task.optJSONObject("answer") ?: JSONObject()
            CdzResultTask(
                id = task.optLong("id"),
                group = indexedTask.groupOrder,
                order = task.optInt("task_order", indexedTask.taskIndex),
                question = task.optJSONArray("question_elements") ?: JSONArray(),
                answer = answer,
                userAnswer = task.optJSONObject("user_answer"),
                feedback = task.optJSONObject("answer_feedback"),
                score = optionalDouble(task, "score"),
                maxScore = optionalDouble(task, "max_score"),
                status = task.optString("status").trim(),
            )
        }
}

private fun optionalDouble(json: JSONObject, key: String): Double =
    if (json.has(key) && !json.isNull(key)) json.optDouble(key, -1.0) else -1.0

/** `answer.type`; в старых ответах встречается просто `type`. */
fun answerType(answer: JSONObject): String =
    answer.optString("answer.type").ifBlank { answer.optString("type") }.trim()

fun parseOptions(answer: JSONObject): List<CdzOption> {
    val options = answer.optJSONArray("options") ?: return emptyList()
    val result = mutableListOf<CdzOption>()
    for (i in 0 until options.length()) {
        val option = options.optJSONObject(i) ?: continue
        val id = option.optString("id").trim()
        if (id.isBlank()) continue
        val content = option.optJSONArray("content")
        var image = ""
        var description = ""
        var video: JSONObject? = null
        if (content != null) {
            for (j in 0 until content.length()) {
                val item = content.optJSONObject(j) ?: continue
                when (item.optString("atomic_type")) {
                    "image" -> {
                        if (image.isBlank()) {
                            image = cdzAbsoluteUrl(
                                item.optString("relative_url")
                                    .ifBlank { item.optString("preview_url") }
                            )
                            description = item.optString("description").trim()
                        }
                    }
                    "video" -> {
                        video = JSONObject(item.toString())
                        if (description.isBlank()) {
                            description = item.optString("description").trim()
                        }
                    }
                }
            }
        }
        val styled = trimmedStyledText(
            raw = option.optString("text"),
            styles = option.optJSONArray("text_styles"),
        )
        result += CdzOption(
            id = id,
            text = styled.text,
            imageUrl = image,
            description = description,
            source = option.optString("type") == "option_type/match/source",
            groupId = option.optString("group_id").trim(),
            textSpans = styled.spans,
            video = video,
        )
    }
    return result
}

/**
 * Таблица из `answer/table`.
 *
 * `options[0].content[0].table` содержит сетку и заголовки, а `cell_types`
 * говорит, какие ячейки нужно заполнять. Если `cell_types` нет, вводными
 * считаются все ячейки ниже/правее заголовков.
 */
fun parseTable(answer: JSONObject): CdzTable? {
    val table = findTable(answer) ?: return null
    val rows = table.optInt("rows", 0)
    val columns = table.optInt("columns", 0)
    if (rows <= 0 || columns <= 0) return null
    val rawCells = table.optJSONObject("cells") ?: return null

    val cells = linkedMapOf<Pair<Int, Int>, String>()
    for (row in 0 until rows) {
        val rowJson = rawCells.optJSONObject(row.toString()) ?: continue
        for (column in 0 until columns) {
            cells[row to column] = cellText(rowJson.opt(column.toString()))
        }
    }

    val columnHeaders = table.optBoolean("columnHeaders", false)
    val rowHeaders = table.optBoolean("rowHeaders", false)
    val headerRows = if (columnHeaders) 1 else 0
    val headerColumns = if (rowHeaders) 1 else 0
    val cellTypes = answer.optJSONObject("cell_types")
    val input = linkedSetOf<Pair<Int, Int>>()
    for (row in headerRows until rows) {
        for (column in headerColumns until columns) {
            val type = cellTypes
                ?.optJSONObject(row.toString())
                ?.optString(column.toString())
                .orEmpty()
                .trim()
                .uppercase()
            if (cellTypes == null || type.isBlank() ||
                "INPUT" in type || "MANUAL" in type
            ) {
                input += row to column
            }
        }
    }

    return CdzTable(
        rows = rows,
        columns = columns,
        columnHeaders = columnHeaders,
        rowHeaders = rowHeaders,
        cells = cells,
        inputCells = input,
    )
}

/** Значения `cells` из `user_answer` или `right_answer` таблицы. */
fun parseTableValues(answer: JSONObject?): Map<Pair<Int, Int>, String> {
    val cells = answer?.optJSONObject("cells") ?: return emptyMap()
    val result = linkedMapOf<Pair<Int, Int>, String>()
    val rowKeys = cells.keys()
    while (rowKeys.hasNext()) {
        val rowKey = rowKeys.next()
        val row = rowKey.toIntOrNull() ?: continue
        val rowJson = cells.optJSONObject(rowKey) ?: continue
        val columnKeys = rowJson.keys()
        while (columnKeys.hasNext()) {
            val columnKey = columnKeys.next()
            val column = columnKey.toIntOrNull() ?: continue
            val text = cellText(rowJson.opt(columnKey)).trim()
            if (text.isNotBlank()) result[row to column] = text
        }
    }
    return result
}

/** Тело ответа `answer/table` в формате сервера. */
fun tableAnswer(type: String, values: Map<Pair<Int, Int>, String>): JSONObject {
    val cells = JSONObject()
    values.toSortedMap(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        .forEach { (key, text) ->
        if (text.isNotBlank()) {
            val rowKey = key.first.toString()
            val row = cells.optJSONObject(rowKey) ?: JSONObject().also {
                cells.put(rowKey, it)
            }
            row.put(key.second.toString(), JSONArray().put(text))
        }
    }
    if (cells.length() == 0) return JSONObject()
    return JSONObject()
        .put("@answer_type", type)
        .put("cells", cells)
}

private fun findTable(answer: JSONObject): JSONObject? {
    answer.optJSONObject("table")?.let { return it }
    val options = answer.optJSONArray("options") ?: return null
    for (optionIndex in 0 until options.length()) {
        val option = options.optJSONObject(optionIndex) ?: continue
        val content = option.optJSONArray("content") ?: continue
        for (partIndex in 0 until content.length()) {
            val part = content.optJSONObject(partIndex) ?: continue
            part.optJSONObject("table")?.let { return it }
        }
    }
    return null
}

private fun cellText(value: Any?): String = when (value) {
    null, JSONObject.NULL -> ""
    is JSONArray -> (0 until value.length())
        .mapNotNull { index -> value.optString(index).takeIf { it.isNotBlank() } }
        .joinToString("\n")
    is String -> value
    else -> value.toString()
}

/**
 * Относительные ссылки ЦДЗ приходят и как `/system/...`, и как полный URL.
 * База всегда uchebnik.mos.ru, поэтому school-хелпер здесь не подходит.
 */
fun cdzAbsoluteUrl(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return ""
    if (value.startsWith("//")) return "https:$value"
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    return "https://uchebnik.mos.ru/${value.trimStart('/')}"
}

/** Разбирает `text_styles` и обрезает диапазоны по границам текста. */
fun parseTextSpans(text: String, styles: JSONArray?): List<CdzTextSpan> {
    if (text.isEmpty() || styles == null) return emptyList()
    val result = mutableListOf<CdzTextSpan>()
    for (index in 0 until styles.length()) {
        val style = styles.optJSONObject(index) ?: continue
        val rawStart = style.optInt("position", -1)
        if (rawStart < 0) continue
        var start = rawStart.coerceAtMost(text.length)
        var end = (rawStart + style.optInt("offset", 0)).coerceAtLeast(start).coerceAtMost(text.length)
        // API иногда включает в SUB завершающий пробел: "O2 " с offset=2.
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start >= end) continue

        val tokens = linkedSetOf<String>()
        val content = style.optJSONArray("content")
        if (content != null) {
            for (tokenIndex in 0 until content.length()) {
                content.optString(tokenIndex).trim()
                    .takeIf { it.isNotBlank() }
                    ?.uppercase()
                    ?.let(tokens::add)
            }
        } else {
            style.optString("content").trim()
                .takeIf { it.isNotBlank() }
                ?.uppercase()
                ?.let(tokens::add)
        }
        if (tokens.isNotEmpty()) result += CdzTextSpan(start, end, tokens)
    }
    return result
}

private data class StyledText(val text: String, val spans: List<CdzTextSpan>)

/**
 * Обрезает внешние пробелы и сдвигает стилевые диапазоны на ту же величину.
 * Для обычного текста это сохраняет визуальную позицию формулы.
 */
private fun trimmedStyledText(raw: String, styles: JSONArray?): StyledText {
    val first = raw.indexOfFirst { !it.isWhitespace() }
    if (first < 0) return StyledText("", emptyList())
    val last = raw.indexOfLast { !it.isWhitespace() }
    val text = raw.substring(first, last + 1)
    val spans = parseTextSpans(raw, styles).mapNotNull { span ->
        val start = (span.start - first).coerceIn(0, text.length)
        val end = (span.end - first).coerceIn(0, text.length)
        if (end > start) span.copy(start = start, end = end) else null
    }
    return StyledText(text, spans)
}

/** Пропуски `answer/inline/choice/single`. */
fun parseInlineChoices(answer: JSONObject): List<CdzInlineChoice> {
    val positions = answer.optJSONArray("text_position") ?: return emptyList()
    // `answer/gap/match/text` хранит общий список вариантов в answer.options,
    // а `answer/inline/choice/single` — у каждого пропуска свой список.
    val sharedOptions = if (isGapMatchText(answerType(answer))) {
        parseOptions(answer)
    } else {
        emptyList()
    }
    val result = mutableListOf<CdzInlineChoice>()
    for (index in 0 until positions.length()) {
        val position = positions.optJSONObject(index) ?: continue
        val positionId = position.optString("position_id").trim()
        val textId = position.optString("text_id").trim()
        val charPosition = position.optInt("position", -1)
        val options = parseOptions(position).ifEmpty { sharedOptions }
        if (positionId.isBlank() || textId.isBlank() || charPosition < 0 || options.isEmpty()) continue
        result += CdzInlineChoice(
            positionId = positionId,
            textId = textId,
            position = charPosition,
            options = options,
        )
    }
    return result
}

/** id вариантов inline-задания: position_id → id. */
fun inlineSelections(answer: JSONObject): Map<String, String> {
    val positions = answer.optJSONArray("text_position_answer") ?: return emptyMap()
    val result = linkedMapOf<String, String>()
    for (index in 0 until positions.length()) {
        val item = positions.optJSONObject(index) ?: continue
        val positionId = item.optString("position_id").trim()
        val optionId = item.optString("id").trim()
        if (positionId.isNotBlank() && optionId.isNotBlank()) result[positionId] = optionId
    }
    return result
}

/** Тело ответа `answer/inline/choice/single` в формате сервера. */
fun inlineAnswer(type: String, selections: List<CdzInlineSelection>): JSONObject {
    if (selections.isEmpty()) return JSONObject()
    val positions = JSONArray()
    selections.forEach { selection ->
        positions.put(
            JSONObject()
                .put("text_id", selection.textId)
                .put("position_id", selection.positionId)
                .put("id", selection.optionId)
        )
    }
    return JSONObject()
        .put("@answer_type", type)
        .put("text_position_answer", positions)
}

/** true для типов, где ответ выбирается прямо в тексте задания. */
fun isInlineChoice(type: String): Boolean =
    type == "answer/inline/choice/single" || isGapMatchText(type)

/** `answer/gap/match/text` использует общий список вариантов и пропуски в тексте. */
fun isGapMatchText(type: String): Boolean =
    type == "answer/gap/match/text"

/** Пары из `answer.match` (source → target). */
fun parseMatch(value: JSONObject?): LinkedHashMap<String, String> {
    val result = LinkedHashMap<String, String>()
    if (value == null) return result
    val match = value.optJSONObject("match") ?: return result
    val keys = match.keys()
    while (keys.hasNext()) {
        val source = keys.next()
        val targets = match.optJSONArray(source) ?: continue
        val target = if (targets.length() > 0) targets.optString(0) else ""
        if (source.isNotBlank() && target.isNotBlank()) result[source] = target
    }
    return result
}

/** Тело ответа `answer/match` в формате сервера. */
fun matchAnswer(pairs: Map<String, String>): JSONObject {
    val match = JSONObject()
    pairs.forEach { (source, target) ->
        match.put(source, JSONArray().put(target))
    }
    return JSONObject()
        .put("@answer_type", "answer/match")
        .put("match", match)
}

/** id, выбранные в single/multiple. */
fun selectedOptionIds(answer: JSONObject): Set<String> {
    val ids = answer.optJSONArray("ids")
    if (ids != null) {
        return (0 until ids.length()).map { ids.optString(it) }.filter { it.isNotBlank() }.toSet()
    }
    val id = answer.optString("id")
    return if (id.isBlank()) emptySet() else setOf(id)
}

/** Значение текстового ответа (string/number/free). */
fun textAnswer(answer: JSONObject): String {
    answer.optJSONArray("string")?.let { array ->
        if (array.length() > 0) return array.optString(0)
    }
    answer.optJSONArray("number")?.let { array ->
        if (array.length() > 0) return array.optString(0)
    }
    val number = answer.optString("number")
    if (number.isNotBlank()) return number
    return answer.optString("string")
}

/** Порядок вариантов answer/order. */
fun orderIds(answer: JSONObject, options: List<CdzOption>): MutableList<String> {
    val saved = answer.optJSONArray("ids_order")
    val result = mutableListOf<String>()
    if (saved != null) {
        for (i in 0 until saved.length()) {
            val id = saved.optString(i)
            if (id.isNotBlank() && options.any { it.id == id }) result += id
        }
    }
    options.forEach { option -> if (option.id !in result) result += option.id }
    return result
}


/** Есть ли в ответе содержательные данные (а не пустая оболочка типа). */
fun hasAnswer(answer: JSONObject): Boolean {
    if (answer.length() <= 1) return false
    answer.optJSONObject("match")?.let { return it.length() > 0 }
    answer.optJSONArray("ids")?.let { return it.length() > 0 }
    answer.optJSONArray("ids_order")?.let { return it.length() > 0 }
    answer.optJSONArray("text_position_answer")?.let { return it.length() > 0 }
    answer.optJSONArray("groups")?.let { groups ->
        for (i in 0 until groups.length()) {
            val ids = groups.optJSONObject(i)?.optJSONArray("options_ids")
            if (ids != null && ids.length() > 0) return true
        }
        return false
    }
    answer.optJSONArray("cells")?.let { cells ->
        for (i in 0 until cells.length()) {
            if (cells.optJSONObject(i)?.optString("answer").orEmpty().isNotBlank()) return true
        }
        return false
    }
    answer.optJSONObject("cells")?.let {
        return parseTableValues(answer).isNotEmpty()
    }
    answer.optJSONArray("string")?.let { values ->
        for (i in 0 until values.length()) {
            if (values.optString(i).isNotBlank()) return true
        }
        return false
    }
    answer.optJSONArray("number")?.let { values ->
        for (i in 0 until values.length()) {
            if (values.optString(i).isNotBlank()) return true
        }
        return false
    }
    return answer.optString("id").isNotBlank() ||
        answer.optString("string").isNotBlank() ||
        answer.optString("number").isNotBlank()
}

/**
 * Есть ли в payload правильный ответ, который можно показать.
 *
 * Сервер в `JUST_CORRECTNESS` возвращает заглушки `{"id":null}`,
 * `{"ids":[]}`, `{"match":{}}` и `{"cells":{}}`; их нельзя выдавать за ответ.
 */
fun hasVisibleAnswer(answer: JSONObject?): Boolean {
    if (answer == null || answer.length() == 0) return false
    if (answer.has("id") && !answer.isNull("id") && answer.optString("id").isNotBlank()) return true
    answer.optJSONArray("ids")?.let { return it.length() > 0 }
    answer.optJSONObject("match")?.let { return it.length() > 0 }
    answer.optJSONObject("cells")?.let { return parseTableValues(answer).isNotEmpty() }
    answer.optJSONArray("cells")?.let { return hasAnswer(answer) }
    answer.optJSONArray("groups")?.let { return hasAnswer(answer) }
    answer.optJSONArray("ids_order")?.let { return it.length() > 0 }
    answer.optJSONArray("text_position_answer")?.let { return it.length() > 0 }
    answer.optJSONArray("answers")?.let { answers ->
        for (index in 0 until answers.length()) {
            val item = answers.optJSONObject(index) ?: continue
            item.optJSONArray("strings")?.let { strings ->
                for (stringIndex in 0 until strings.length()) {
                    if (strings.optString(stringIndex).isNotBlank()) return true
                }
            }
        }
    }
    answer.optJSONArray("string")?.let { array ->
        for (index in 0 until array.length()) {
            if (array.optString(index).isNotBlank()) return true
        }
    }
    answer.optJSONArray("number")?.let { array ->
        for (index in 0 until array.length()) {
            if (array.optString(index).isNotBlank()) return true
        }
    }
    if (answer.optString("string").isNotBlank()) return true
    if (answer.optString("number").isNotBlank()) return true
    return false
}
