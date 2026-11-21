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
                            image = item.optString("relative_url")
                                .ifBlank { item.optString("preview_url") }
                                .trim()
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
    val result = mutableListOf<CdzInlineChoice>()
    for (index in 0 until positions.length()) {
        val position = positions.optJSONObject(index) ?: continue
        val positionId = position.optString("position_id").trim()
        val textId = position.optString("text_id").trim()
        val charPosition = position.optInt("position", -1)
        val options = parseOptions(position)
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
    type == "answer/inline/choice/single"

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
