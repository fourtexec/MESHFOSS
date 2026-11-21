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
    val order: Int,
    val type: String,
    val question: JSONArray,
    val answer: JSONObject,
    val serverAnswer: JSONObject?,
)

/** Один вариант ответа: текст, картинка и роль в сопоставлении. */
data class CdzOption(
    val id: String,
    val text: String,
    val imageUrl: String,
    val description: String,
    val source: Boolean,
    val groupId: String = "",
)

/** Стабильный порядок заданий: task_order, затем исходный индекс. */
fun parseTasks(attempt: JSONObject): List<CdzTask> {
    val indexed = mutableListOf<Pair<Int, JSONObject>>()
    val groups = attempt.optJSONArray("challenge_test_groups") ?: return emptyList()
    var index = 0
    for (groupIndex in 0 until groups.length()) {
        val group = groups.optJSONObject(groupIndex) ?: continue
        val tasks = group.optJSONArray("challenge_tasks") ?: continue
        for (taskIndex in 0 until tasks.length()) {
            val task = tasks.optJSONObject(taskIndex) ?: continue
            if (task.optLong("id") > 0L) indexed += index++ to task
        }
    }
    return indexed
        .sortedWith(
            compareBy(
                { (_, task) -> task.optInt("task_order", Int.MAX_VALUE) },
                { (sourceIndex, _) -> sourceIndex },
            )
        )
        .map { (_, task) ->
            val answer = task.optJSONObject("answer") ?: JSONObject()
            CdzTask(
                id = task.optLong("id"),
                order = task.optInt("task_order", 0),
                type = answerType(answer),
                question = task.optJSONArray("question_elements") ?: JSONArray(),
                answer = answer,
                serverAnswer = task.optJSONObject("user_answer"),
            )
        }
}

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
        if (content != null) {
            for (j in 0 until content.length()) {
                val item = content.optJSONObject(j) ?: continue
                if (item.optString("atomic_type") == "image") {
                    image = item.optString("relative_url")
                        .ifBlank { item.optString("preview_url") }
                        .trim()
                    description = item.optString("description").trim()
                    if (image.isNotBlank()) break
                }
            }
        }
        result += CdzOption(
            id = id,
            text = option.optString("text").trim(),
            imageUrl = image,
            description = description,
            source = option.optString("type") == "option_type/match/source",
            groupId = option.optString("group_id").trim(),
        )
    }
    return result
}

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
