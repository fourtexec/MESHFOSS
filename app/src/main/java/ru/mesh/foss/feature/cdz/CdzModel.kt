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

/** Текст условия после вставки `content/math` и ответов в позиции `position`. */
data class CdzMergedText(
    val text: String,
    val spans: List<CdzTextSpan>,
    /** Пропуски, которые нужно нарисовать inline внутри [text]. */
    val placeholders: List<CdzInlinePlaceholder> = emptyList(),
)

/** Пропуск `answer/gap/text/input`: id, текстовый блок и позиция в нём. */
data class CdzGapPosition(
    val id: String,
    val textId: String,
    val position: Int,
)

/**
 * Ответ на один пропуск `answer/gap/text/input`.
 *
 * [index] — позиция в `answer.text_position`, а [values] — один или несколько
 * допустимых вариантов. У правильного ответа сервер часто присылает несколько
 * `input_strings` на один пропуск. [textId] и [position] позволяют вставить
 * ответ обратно в текст задания, как это делает веб-версия.
 */
data class CdzGapAnswer(
    val index: Int,
    val values: List<String>,
    val positionId: String = "",
    val textId: String = "",
    val position: Int = -1,
)

/** Позиция на фоновом изображении в `answer/gap/match/background/image/selection`. */
data class CdzImagePosition(
    val id: String,
    val number: Int,
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
    /** Заголовок группы в `answer/groups` (`option_type/group`), а не элемент. */
    val isGroup: Boolean = false,
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

/**
 * Сетка `answer/grid` (таблица V2).
 *
 * В отличие от `answer/table`, каждая ячейка — самостоятельный объект с
 * координатами и вариантами ответа. Заголовки помечены `is_header`, а поля
 * ввода — `type=cell/input` и `input_type=cell-input/...`.
 */
data class CdzGridCell(
    val row: Int,
    val column: Int,
    val header: Boolean,
    val input: Boolean,
    val inputType: String,
    val text: String,
    val textSpans: List<CdzTextSpan>,
    val imageUrl: String,
    val imageDescription: String,
    val options: List<CdzOption>,
) {
    val manualInput: Boolean get() = input && options.isEmpty()
    val multiple: Boolean get() =
        inputType.contains("multi", ignoreCase = true) ||
            inputType.contains("multiple", ignoreCase = true)
}

data class CdzGrid(
    val rows: List<Int>,
    val columns: List<Int>,
    val cells: Map<Pair<Int, Int>, CdzGridCell>,
) {
    fun cell(row: Int, column: Int): CdzGridCell? = cells[row to column]
}

/** Значение одной ячейки `answer/grid`. */
data class CdzGridValue(
    val optionIds: List<String> = emptyList(),
    val manual: String = "",
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

/**
 * Общее условие группы (`groups_context_info`).
 *
 * В олимпиадных попытках сервер выносит общий текст/формулы перед заданиями.
 * Без этого блока задачи выглядят оторванными от условия.
 */
data class CdzContextInfo(
    val caption: String,
    val elements: JSONArray,
)

fun parseContextInfo(result: JSONObject): List<CdzContextInfo> {
    val groups = result.optJSONObject("groups_context_info") ?: return emptyList()
    return groups.keys().asSequence()
        .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
        .mapNotNull { key ->
            val group = groups.optJSONObject(key) ?: return@mapNotNull null
            val content = group.optJSONObject("content") ?: return@mapNotNull null
            val elements = JSONArray()
            val text = content.optString("text")
            val items = content.optJSONArray("content") ?: JSONArray()
            if (text.isNotBlank()) {
                // Формулы/картинки уже лежат внутри `content`; текстовый блок
                // сам вставит их по `position`, поэтому не дублируем их сверху.
                elements.put(
                    JSONObject()
                        .put("type", "content/text")
                        .put("text_id", "context_$key")
                        .put("text", text)
                        .put(
                            "text_styles",
                            content.optJSONArray("text_styles") ?: JSONArray(),
                        )
                        .put("content", items)
                )
            } else {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    elements.put(item)
                }
            }
            if (elements.length() == 0) return@mapNotNull null
            CdzContextInfo(
                caption = group.optString("caption").trim(),
                elements = elements,
            )
        }
        .toList()
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

/**
 * Задание считается тем же, если совпадают id, тип, условие и структура ответа.
 *
 * В результате сервер скрывает правильный ответ (`right_answer`,
 * `correct_answer`), поэтому эти поля не сравниваем: иначе полностью
 * одинаковое задание никогда бы не совпало. Порядок вариантов в
 * `answer.options` может отличаться — сервер вправе перемешать их между
 * попытками; остальные массивы сравниваются с учётом порядка.
 */
fun sameCdzTask(current: CdzTask, previous: CdzResultTask): Boolean {
    if (current.id != previous.id) return false
    if (current.type != answerType(previous.answer)) return false
    if (!jsonEquivalent(current.question, previous.question)) return false
    return sameCdzAnswer(current.answer, previous.answer)
}

/** Автоматически проверенный ответ без ожидания учителя. */
fun isCorrectCdzResult(task: CdzResultTask): Boolean =
    task.status != "AWAIT_FOR_REVIEW" &&
        task.feedback?.optString("correctness")?.trim()
            ?.equals("CORRECT", ignoreCase = true) == true

/**
 * Локальные ответы, которые можно подставить из прошлых попыток.
 *
 * [previous] должен идти от новой попытки к старой: при нескольких верных
 * ответах на одно задание побеждает самый свежий. Возвращаются только
 * черновики — вызывающий код не должен добавлять их в `saved`, пока ученик
 * сам не нажмёт «Сохранить».
 */
fun prefillAnswers(
    tasks: List<CdzTask>,
    previous: List<CdzResultTask>,
    draft: Map<Long, JSONObject>,
    saved: Set<Long>,
): Map<Long, JSONObject> {
    val correct = previous.filter { isCorrectCdzResult(it) && hasVisibleAnswer(it.userAnswer) }
    val result = LinkedHashMap<Long, JSONObject>()
    tasks.forEach { task ->
        if (task.serverAnswer != null || task.id in saved) return@forEach
        if (hasAnswer(draft[task.id] ?: JSONObject())) return@forEach
        val source = correct.firstOrNull { sameCdzTask(task, it) } ?: return@forEach
        val answer = source.userAnswer ?: return@forEach
        result[task.id] = JSONObject(answer.toString())
    }
    return result
}

private val cdzHiddenAnswerKeys = setOf(
    "right_answer",
    "correct_answer",
    "detail_correctness",
    "hideCorrectAnswer",
    "answer_feedback",
)

private fun sameCdzAnswer(first: JSONObject, second: JSONObject): Boolean {
    val firstKeys = first.keys().asSequence()
        .filterNot { it in cdzHiddenAnswerKeys }
        .toSet()
    val secondKeys = second.keys().asSequence()
        .filterNot { it in cdzHiddenAnswerKeys }
        .toSet()
    if (firstKeys != secondKeys) return false
    return firstKeys.all { key ->
        if (key == "options") {
            jsonEquivalentOptions(first.optJSONArray(key), second.optJSONArray(key))
        } else {
            jsonEquivalent(first.opt(key), second.opt(key))
        }
    }
}

/** Варианты ответа сравниваются как множество: их порядок сервер может менять. */
private fun jsonEquivalentOptions(first: JSONArray?, second: JSONArray?): Boolean {
    if (first == null || second == null) return first == null && second == null
    if (first.length() != second.length()) return false
    val remaining = (0 until second.length()).map { second.opt(it) }.toMutableList()
    for (index in 0 until first.length()) {
        val item = first.opt(index)
        val match = remaining.indexOfFirst { jsonEquivalent(item, it) }
        if (match < 0) return false
        remaining.removeAt(match)
    }
    return true
}

private fun jsonEquivalent(first: Any?, second: Any?): Boolean {
    val firstNull = first == null || first === JSONObject.NULL
    val secondNull = second == null || second === JSONObject.NULL
    if (firstNull || secondNull) return firstNull && secondNull
    if (first is JSONObject && second is JSONObject) {
        val firstKeys = first.keys().asSequence()
            .filterNot { it in cdzHiddenAnswerKeys }
            .toSet()
        val secondKeys = second.keys().asSequence()
            .filterNot { it in cdzHiddenAnswerKeys }
            .toSet()
        if (firstKeys != secondKeys) return false
        return firstKeys.all { key -> jsonEquivalent(first.opt(key), second.opt(key)) }
    }
    if (first is JSONArray && second is JSONArray) {
        if (first.length() != second.length()) return false
        return (0 until first.length()).all { index ->
            jsonEquivalent(first.opt(index), second.opt(index))
        }
    }
    if (first is Number && second is Number) return first.toDouble() == second.toDouble()
    return first == second
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
        var video: JSONObject? = null
        val contentText = mutableListOf<String>()
        if (content != null) {
            for (j in 0 until content.length()) {
                val item = content.optJSONObject(j) ?: continue
                val type = item.optString("type").trim()
                val atomic = item.optString("atomic_type").trim()
                val media = cdzAtomicMediaType(item)
                when {
                    media == "image" -> {
                        if (image.isBlank()) {
                            image = cdzAbsoluteUrl(
                                item.optString("relative_url")
                                    .ifBlank { item.optString("preview_url") }
                            )
                            description = item.optString("description").trim()
                        }
                    }
                    media == "video" -> {
                        video = JSONObject(item.toString())
                        if (description.isBlank()) {
                            description = item.optString("description").trim()
                        }
                    }
                    type.startsWith("content/math") || atomic == "math" -> {
                        parseMathContent(
                            item.optString("content").ifBlank { item.optString("text") }
                        ).takeIf { it.isNotBlank() }?.let(contentText::add)
                    }
                    type.startsWith("content/text") -> {
                        item.optString("text").trim()
                            .takeIf { it.isNotBlank() }?.let(contentText::add)
                    }
                    else -> {
                        item.optString("text").trim()
                            .takeIf { it.isNotBlank() }?.let(contentText::add)
                    }
                }
            }
        }
        val baseText = option.optString("text")
        val styled = if (baseText.isNotBlank()) {
            trimMergedText(
                mergeQuestionText(
                    text = baseText,
                    content = content,
                    styles = option.optJSONArray("text_styles"),
                )
            )
        } else {
            trimmedStyledText(
                raw = contentText.joinToString(" "),
                styles = option.optJSONArray("text_styles"),
            )
        }
        result += CdzOption(
            id = id,
            text = styled.text,
            imageUrl = image,
            description = description,
            source = option.optString("type") == "option_type/match/source",
            groupId = option.optString("group_id").trim(),
            textSpans = styled.spans,
            video = video,
            isGroup = option.optString("type") == "option_type/group",
        )
    }
    return result
}

/**
 * `content/math` в API — простая LaTeX-подобная запись (`{Na_2S}`, `x^{2}`).
 * Показываем её человеческим текстом через общий парсер: простые односимвольные
 * индексы становятся Unicode, сложные (`p_{мат}`) — baseline-span'ами на экране.
 * Это нужно и в условии, и в вариантах ответа.
 */
fun parseMathContent(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return parseMathText(raw)
        .replace(Regex("""\s+([.,;:!?])"""), "$1")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

/**
 * Склеивает обычный текст условия с `content/math` по полю `position`.
 *
 * В олимпиадных `question_elements` формулы не лежат внутри `text`: сервер
 * присылает чистый текст и отдельные math-блоки с координатой вставки. Если
 * просто отрисовать `text`, а потом все `content`, формулы уезжают в конец
 * задания. Здесь они возвращаются на свои места; пробелы добавляются только
 * там, где формула примыкает к слову.
 */
fun mergeQuestionText(
    text: String,
    content: JSONArray?,
    styles: JSONArray? = null,
    gaps: List<CdzGapAnswer> = emptyList(),
): CdzMergedText {
    val inserts = mutableListOf<MergedInsertion>()
    val contentPositions = mutableListOf<Int>()
    if (content != null) {
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            val rawPosition = part.optInt("position", -1)
            if (rawPosition >= 0) {
                contentPositions += rawPosition.coerceIn(0, text.length)
            }
            val type = part.optString("type").trim()
            val atomic = part.optString("atomic_type").trim()
            if (!type.startsWith("content/math") && atomic != "math") continue
            val math = parseMath(
                part.optString("content").ifBlank { part.optString("text") }
            )
            if (math.text.isBlank() && math.inlines.isEmpty()) continue
            val position = if (rawPosition < 0) text.length else rawPosition.coerceIn(0, text.length)
            inserts += MergedInsertion(position, order = index, math = math)
        }
    }
    val gapPositions = gaps.mapNotNull { gap ->
        gap.position.takeIf { it >= 0 }?.coerceIn(0, text.length)
    }
    gaps.forEachIndexed { index, gap ->
        if (gap.position < 0) return@forEachIndexed
        val value = gap.values.joinToString(" / ").trim().ifBlank { "—" }
        inserts += MergedInsertion(
            position = gap.position.coerceIn(0, text.length),
            order = index,
            gap = gap.copy(values = listOf(value)),
        )
    }

    // Draft.js-индексы `text_block_index` считают только неатомарные
    // текстовые блоки. Формулы и пропуски вставляются как atomic-блоки,
    // поэтому для стилей нужно восстановить именно эту последовательность.
    val blockPositions = contentPositions + gapPositions
    val sourceSpans = parseTextSpansForContent(text, styles, blockPositions)
    if (inserts.isEmpty()) {
        return normalizeMergedText(CdzMergedText(text, sourceSpans))
    }

    val out = StringBuilder(text.length + inserts.sumOf { (it.math?.text?.length ?: 0) + 2 })
    val inserted = mutableListOf<Pair<Int, Int>>()
    val placeholders = mutableListOf<CdzInlinePlaceholder>()
    val mathSpans = mutableListOf<CdzTextSpan>()
    var cursor = 0
    // Пропуски идут перед формулой в той же точке: ответ должен остаться
    // после знака вопроса/слова, а формула — на своей исходной позиции.
    inserts.sortedWith(compareBy<MergedInsertion>({ it.position }, { it.gap == null }, { it.order }))
        .forEach { insertion ->
            val at = insertion.position.coerceIn(cursor, text.length)
            if (at > cursor) out.append(text, cursor, at)
            val gap = insertion.gap
            if (gap != null) {
                val id = "gap_${gap.positionId.ifBlank { "answer_${placeholders.size}" }}"
                placeholders += CdzInlinePlaceholder(
                    position = out.length,
                    id = id,
                    alternateText = gap.values.firstOrNull().orEmpty().ifBlank { "—" },
                )
            } else {
                val math = insertion.math ?: return@forEach
                val before = out.length
                if (out.isNotEmpty() && needsSpaceBefore(out.last())) out.append(' ')
                val mathStart = out.length
                out.append(math.text)
                math.spans.forEach { span ->
                    mathSpans += span.copy(
                        start = mathStart + span.start,
                        end = mathStart + span.end,
                    )
                }
                math.inlines.forEachIndexed { inlineIndex, inline ->
                    placeholders += CdzInlinePlaceholder(
                        position = mathStart + inline.position,
                        id = "math_${insertion.order}_$inlineIndex",
                        alternateText = inline.alternateText,
                        math = inline.node,
                    )
                }
                val next = text.getOrNull(at)
                if (next != null && needsSpaceAfter(next)) out.append(' ')
                inserted += at to (out.length - before)
            }
            cursor = at
        }
    if (cursor < text.length) out.append(text, cursor, text.length)

    val merged = out.toString()
    val mappedSpans = sourceSpans.mapNotNull { span ->
        val start = mapMergedPosition(span.start, inserted, includeAtPosition = true)
        val end = mapMergedPosition(span.end, inserted, includeAtPosition = false)
        if (end > start) span.copy(start = start, end = end) else null
    } + mathSpans
    return normalizeMergedText(CdzMergedText(merged, mappedSpans, placeholders))
}

private data class MergedInsertion(
    val position: Int,
    val order: Int,
    val math: CdzMathRender? = null,
    val gap: CdzGapAnswer? = null,
)

/**
 * `text_styles` с учётом Draft.js-блоков.
 *
 * `content/math` и пропуски в вебе вставляются atomic-блоками. После этого
 * `text_block_index` считается по списку только текстовых блоков, а не по
 * исходной строке. Здесь восстанавливаем этот список и переводим локальную
 * позицию стиля в координаты исходного текста.
 */
private fun parseTextSpansForContent(
    text: String,
    styles: JSONArray?,
    positions: List<Int>,
): List<CdzTextSpan> {
    if (text.isEmpty() || styles == null) return emptyList()
    val blocks = draftTextBlocks(text, positions)
    val result = mutableListOf<CdzTextSpan>()
    for (index in 0 until styles.length()) {
        val style = styles.optJSONObject(index) ?: continue
        val rawStart = style.optInt("position", -1)
        if (rawStart < 0) continue
        val blockIndex = style.optInt("text_block_index", -1)
        val local = if (blockIndex >= 0) {
            val block = blocks.getOrNull(blockIndex) ?: continue
            val start = block.start + rawStart
            val end = start + style.optInt("offset", 0)
            val blockEnd = block.start + block.text.length
            if (start in block.start..blockEnd && end >= start) {
                start to end.coerceAtMost(blockEnd)
            } else {
                null
            }
        } else {
            rawStart to rawStart + style.optInt("offset", 0)
        } ?: continue

        var start = local.first.coerceIn(0, text.length)
        var end = local.second.coerceAtLeast(start).coerceIn(0, text.length)
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start >= end) continue
        val tokens = styleTokens(style)
        if (tokens.isNotEmpty()) result += CdzTextSpan(start, end, tokens)
    }
    return result
}

private data class DraftTextBlock(
    val start: Int,
    val text: String,
    val atomic: Boolean = false,
)

/**
 * Повторяет преобразование web-бандла: текст режется вставками на позициях,
 * формулы/пропуски становятся atomic-блоками, а `\n` создаёт новый блок.
 */
private fun draftTextBlocks(text: String, positions: List<Int>): List<DraftTextBlock> {
    val blocks = mutableListOf(DraftTextBlock(0, ""))
    val byPosition = positions
        .filter { it in 0..text.length }
        .groupingBy { it }
        .eachCount()
    for (position in 0..text.length) {
        val count = byPosition[position] ?: 0
        repeat(count) { item ->
            if (item > 0) blocks += DraftTextBlock(position, "")
            blocks += DraftTextBlock(position, "", atomic = true)
        }
        val char = text.getOrNull(position)
        val current = blocks.removeLastOrNull() ?: DraftTextBlock(position, "")
        when {
            char == '\n' -> {
                blocks += current
                blocks += DraftTextBlock(position + 1, "")
                if (current.atomic) blocks += DraftTextBlock(position + 1, "")
            }
            current.atomic -> {
                blocks += current
                if (char != null) blocks += DraftTextBlock(position, char.toString())
            }
            char != null -> blocks += current.copy(text = current.text + char)
            else -> {
                blocks += current
                if (current.atomic) blocks += DraftTextBlock(position, "")
            }
        }
    }
    return blocks.filterNot { it.atomic }
}

private fun styleTokens(style: JSONObject): Set<String> {
    val tokens = linkedSetOf<String>()
    val content = style.optJSONArray("content")
    if (content != null) {
        for (index in 0 until content.length()) {
            content.optString(index).trim()
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
    return tokens
}

private fun needsSpaceBefore(char: Char): Boolean =
    char.isLetterOrDigit() || char in ",.;:!?)]}»”"

private fun needsSpaceAfter(char: Char): Boolean =
    char.isLetterOrDigit() || char in "(«“\""

private fun mapMergedPosition(
    position: Int,
    inserted: List<Pair<Int, Int>>,
    includeAtPosition: Boolean,
): Int {
    var shift = 0
    inserted.forEach { (at, length) ->
        if (at < position || (includeAtPosition && at == position)) shift += length
    }
    return position + shift
}

/**
 * Тип атома: `image` или `video`.
 *
 * `content/atomic` без `atomic_type` встречается у фото и у видео; тип нельзя
 * угадывать только по `type`, иначе картинка попадает в VideoView и висит.
 */
fun cdzAtomicMediaType(block: JSONObject): String {
    val type = block.optString("type").trim().lowercase()
    val atomic = block.optString("atomic_type").trim().lowercase()
    val contentType = block.optString("content_type").trim().lowercase()
    val url = block.optString("file_url")
        .ifBlank { block.optString("relative_url") }
        .ifBlank { block.optString("preview_url") }
        .ifBlank { block.optString("url") }
    if (contentType.startsWith("image")) return "image"
    if (contentType.startsWith("video")) return "video"
    if (atomic == "image" || type == "content/image" || looksLikeImage(url)) return "image"
    if (atomic == "video" || type == "content/video" || looksLikeVideo(url)) return "video"
    // У фото/таблиц atomic_type иногда отсутствует, но URL уже есть.
    if (type == "content/atomic" && atomic.isBlank() && url.isNotBlank()) return "image"
    return ""
}

private fun looksLikeImage(value: String): Boolean {
    val lower = value.substringBefore('?').lowercase()
    return lower.endsWith(".png") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".gif") ||
        lower.endsWith(".bmp") ||
        lower.endsWith(".svg")
}

private fun looksLikeVideo(value: String): Boolean {
    val lower = value.substringBefore('?').lowercase()
    return lower.endsWith(".mp4") ||
        lower.endsWith(".m3u8") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".mov") ||
        lower.contains("/video/")
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
    val declaredRows = table.optInt("rows", 0)
    val declaredColumns = table.optInt("columns", 0)
    if (declaredRows <= 0 || declaredColumns <= 0) return null
    val rawCells = table.optJSONObject("cells") ?: return null

    // Иногда `rows`/`columns` меньше фактической сетки. Берём максимум,
    // иначе нижняя строка с полями ввода просто исчезает.
    var rows = declaredRows
    var columns = declaredColumns
    val rowKeys = rawCells.keys()
    while (rowKeys.hasNext()) {
        val rowKey = rowKeys.next()
        val row = rowKey.toIntOrNull() ?: continue
        rows = maxOf(rows, row + 1)
        val rowJson = rawCells.optJSONObject(rowKey) ?: continue
        val columnKeys = rowJson.keys()
        while (columnKeys.hasNext()) {
            val column = columnKeys.next().toIntOrNull() ?: continue
            columns = maxOf(columns, column + 1)
        }
    }

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

/**
 * Разбирает `answer/grid`. Координаты строк/столбцов приходят с единицы и
 * могут быть разреженными, поэтому сетка хранит реальные ключи API, а не
 * предполагаемый диапазон `0..rows`.
 */
fun parseGrid(answer: JSONObject): CdzGrid? {
    val rawCells = answer.optJSONArray("cells") ?: return null
    val cells = linkedMapOf<Pair<Int, Int>, CdzGridCell>()
    val rowKeys = linkedSetOf<Int>()
    val columnKeys = linkedSetOf<Int>()

    for (index in 0 until rawCells.length()) {
        val raw = rawCells.optJSONObject(index) ?: continue
        val row = raw.optInt("row", -1)
        val column = raw.optInt("column", -1)
        if (row < 0 || column < 0) continue

        val type = raw.optString("type").trim()
        val inputType = raw.optString("input_type").trim()
        val optionArray = raw.optJSONArray("options")
        val options = if (optionArray == null) {
            emptyList()
        } else {
            parseOptions(JSONObject().put("options", optionArray))
        }
        val header = raw.optBoolean("is_header", false) ||
            (type == "cell/attachment" && inputType.isBlank() && options.isEmpty())
        val input = !header && (
            type == "cell/input" ||
                inputType.isNotBlank() ||
                options.isNotEmpty()
            )
        val styled = gridCellContent(raw)

        cells[row to column] = CdzGridCell(
            row = row,
            column = column,
            header = header,
            input = input,
            inputType = inputType,
            text = styled.text,
            textSpans = styled.spans,
            imageUrl = styled.imageUrl,
            imageDescription = styled.imageDescription,
            options = options,
        )
        rowKeys += row
        columnKeys += column
    }

    if (cells.isEmpty()) return null
    return CdzGrid(
        rows = rowKeys.sorted(),
        columns = columnKeys.sorted(),
        cells = cells,
    )
}

/** Заголовок/подпись ячейки сетки: текст, формулы и картинка. */
private fun gridCellContent(cell: JSONObject): StyledTextWithImage {
    val texts = mutableListOf<String>()
    val spans = mutableListOf<CdzTextSpan>()
    var imageUrl = ""
    var imageDescription = ""

    fun addText(raw: String, styles: JSONArray?) {
        val styled = trimmedStyledText(raw, styles)
        if (styled.text.isBlank()) return
        val offset = if (texts.isEmpty()) 0 else texts.sumOf { it.length + 1 }
        texts += styled.text
        spans += styled.spans.map { span ->
            span.copy(start = span.start + offset, end = span.end + offset)
        }
    }

    val content = cell.optJSONArray("content")
    if (content != null) {
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            val type = part.optString("type").trim()
            val atomic = part.optString("atomic_type").trim()
            if (type.startsWith("content/math") || atomic == "math") {
                addText(
                    parseMathContent(
                        part.optString("content").ifBlank { part.optString("text") }
                    ),
                    null,
                )
                continue
            }
            if (cdzAtomicMediaType(part) == "image") {
                if (imageUrl.isBlank()) {
                    imageUrl = cdzAbsoluteUrl(
                        part.optString("relative_url")
                            .ifBlank { part.optString("preview_url") }
                    )
                    imageDescription = part.optString("description").trim()
                }
                continue
            }
            addText(part.optString("text"), part.optJSONArray("text_styles"))
        }
    }

    if (texts.isEmpty()) {
        addText(cell.optString("text"), cell.optJSONArray("text_styles"))
    }

    return StyledTextWithImage(
        text = texts.joinToString("\n"),
        spans = spans,
        imageUrl = imageUrl,
        imageDescription = imageDescription,
    )
}

private data class StyledTextWithImage(
    val text: String,
    val spans: List<CdzTextSpan>,
    val imageUrl: String,
    val imageDescription: String,
)

/** Значения ячеек из `user_answer` или `right_answer` сетки. */
fun parseGridValues(answer: JSONObject?): Map<Pair<Int, Int>, CdzGridValue> {
    val cells = answer?.optJSONArray("cells") ?: return emptyMap()
    val result = linkedMapOf<Pair<Int, Int>, CdzGridValue>()
    for (index in 0 until cells.length()) {
        val cell = cells.optJSONObject(index) ?: continue
        val row = cell.optInt("row", index + 1)
        val column = cell.optInt("column", 1)
        if (row < 0 || column < 0) continue

        val optionIds = linkedSetOf<String>()
        cell.optString("option_id").trim()
            .takeIf { it.isNotBlank() }
            ?.let(optionIds::add)
        cell.optJSONArray("option_ids")?.let { ids ->
            for (idIndex in 0 until ids.length()) {
                ids.optString(idIndex).trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(optionIds::add)
            }
        }
        cell.optJSONArray("ids")?.let { ids ->
            for (idIndex in 0 until ids.length()) {
                ids.optString(idIndex).trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(optionIds::add)
            }
        }
        val manual = cellText(cell.opt("answer")).trim()
        if (optionIds.isEmpty() && manual.isBlank()) continue
        result[row to column] = CdzGridValue(optionIds.toList(), manual)
    }
    return result
}

/** Тело ответа `answer/grid` в формате сервера. */
fun gridAnswer(
    type: String,
    values: Map<Pair<Int, Int>, CdzGridValue>,
    grid: CdzGrid? = null,
): JSONObject {
    val cells = JSONArray()
    values.toSortedMap(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        .forEach { (key, value) ->
            if (value.optionIds.isEmpty() && value.manual.isBlank()) return@forEach
            val cell = grid?.cell(key.first, key.second)
            val multi = cell?.multiple == true
            val inputType = cell?.inputType?.takeIf { it.isNotBlank() }
                ?: when {
                    multi -> "cell-input/multi-select"
                    value.optionIds.isNotEmpty() -> "cell-input/single-select"
                    else -> "cell-input/manual-input"
                }
            val out = JSONObject()
                .put("input_type", inputType)
                .put("row", key.first)
                .put("column", key.second)

            if (value.optionIds.isNotEmpty()) {
                if (multi) {
                    out.put("option_ids", JSONArray().apply {
                        value.optionIds.forEach { id -> put(id) }
                    })
                } else {
                    out.put("option_id", value.optionIds.first())
                }
            }
            if (value.manual.isNotBlank()) out.put("answer", value.manual)

            cells.put(out)
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
        .mapNotNull { index -> cellText(value.opt(index)).takeIf { it.isNotBlank() } }
        .joinToString("\n")
    is JSONObject -> {
        val type = value.optString("type").trim()
        val atomic = value.optString("atomic_type").trim()
        if (type.startsWith("content/math") || atomic == "math") {
            parseMathContent(value.optString("content").ifBlank { value.optString("text") })
        } else {
            cellText(value.opt("text"))
        }
    }
    is String -> value
    else -> value.toString()
}.trim()

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

/**
 * Границы текстовых блоков inline-задания.
 *
 * `text_styles.position` в ЦДЗ считается от начала своего `text_block_index`,
 * а не от всей склеенной строки. Пропуски (`text_position`) находятся ровно
 * между этими блоками, поэтому их позиции и дают границы.
 */
fun inlineTextBlockStarts(textLength: Int, positions: Iterable<Int>): List<Int> {
    val starts = mutableListOf(0)
    positions.asSequence()
        .filter { it in 0..textLength }
        .sorted()
        .forEach { position ->
            if (starts.last() != position) starts += position
        }
    if (starts.last() != textLength) starts += textLength
    return starts
}

/**
 * Разбирает `text_styles` и обрезает диапазоны по границам текста.
 *
 * [blockStarts] нужен для inline-заданий: в них API нумерует текстовые блоки,
 * и `position` каждого стиля относится к своему блоку. Без этой карты
 * индексы SUB/SUP попадали на случайные буквы и визуально «уплывали» вниз.
 */
fun parseTextSpans(
    text: String,
    styles: JSONArray?,
    blockStarts: List<Int>? = null,
): List<CdzTextSpan> {
    if (text.isEmpty() || styles == null) return emptyList()
    val blocks = blockStarts
        ?.takeIf { it.size >= 2 && it.first() == 0 && it.last() == text.length }
    val result = mutableListOf<CdzTextSpan>()
    for (index in 0 until styles.length()) {
        val style = styles.optJSONObject(index) ?: continue
        val rawStart = style.optInt("position", -1)
        if (rawStart < 0) continue

        val blockIndex = style.optInt("text_block_index", -1)
        val local = if (blockIndex >= 0 && blocks != null && blockIndex < blocks.lastIndex) {
            val blockStart = blocks[blockIndex]
            val blockEnd = blocks[blockIndex + 1]
            val start = blockStart + rawStart
            val end = blockStart + rawStart + style.optInt("offset", 0)
            if (start in blockStart..blockEnd && end >= start) start to end.coerceAtMost(blockEnd) else null
        } else {
            // Старый формат: координаты уже от начала всей строки.
            if (style.has("text_block_index") && blockIndex > 0) null
            else rawStart to rawStart + style.optInt("offset", 0)
        } ?: continue

        var start = local.first.coerceIn(0, text.length)
        var end = local.second.coerceAtLeast(start).coerceIn(0, text.length)
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

/**
 * Обрезает внешние пробелы и сдвигает стилевые диапазоны на ту же величину.
 * Для обычного текста это сохраняет визуальную позицию формулы.
 */
private fun trimmedStyledText(raw: String, styles: JSONArray?): CdzMergedText {
    val first = raw.indexOfFirst { !it.isWhitespace() }
    if (first < 0) return CdzMergedText("", emptyList())
    val last = raw.indexOfLast { !it.isWhitespace() }
    val text = raw.substring(first, last + 1)
    val spans = parseTextSpans(raw, styles).mapNotNull { span ->
        val start = (span.start - first).coerceIn(0, text.length)
        val end = (span.end - first).coerceIn(0, text.length)
        if (end > start) span.copy(start = start, end = end) else null
    }
    return normalizeMergedText(CdzMergedText(text, spans))
}

/**
 * Убирает пробелы перед знаками препинания и повторные пробелы.
 *
 * В олимпиадных `text` они встречаются регулярно (`«слово , ещё»`), а формулы
 * и стили привязаны к старым координатам. Поэтому параллельно со сборкой
 * строки строится карта старых индексов в новые, чтобы не сломать `text_styles`.
 * Многоточие `...` не склеиваем: пробел перед ним может быть осмысленным.
 */
internal fun normalizeMergedText(merged: CdzMergedText): CdzMergedText {
    val source = merged.text
    if (source.isEmpty()) return merged
    val out = StringBuilder(source.length)
    val map = IntArray(source.length + 1)
    for (index in source.indices) {
        map[index] = out.length
        val char = source[index]
        if (char == ' ' || char == '\t') {
            val next = source.getOrNull(index + 1)
            val beforePunctuation = next != null && next in ",;:!?"
            val beforeSingleDot = next == '.' && source.getOrNull(index + 2) != '.'
            if (beforePunctuation || beforeSingleDot) continue
            if (out.isEmpty() || out.last() == ' ' || out.last() == '\t') continue
            out.append(' ')
        } else {
            out.append(char)
        }
    }
    map[source.length] = out.length
    if (out.length == source.length && out.toString() == source) return merged
    val text = out.toString()
    val spans = merged.spans.mapNotNull { span ->
        val start = map[span.start.coerceIn(0, source.length)]
        val end = map[span.end.coerceIn(0, source.length)]
        if (end > start) span.copy(start = start, end = end) else null
    }
    val placeholders = merged.placeholders.map { placeholder ->
        placeholder.copy(position = map[placeholder.position.coerceIn(0, source.length)])
    }
    return CdzMergedText(text, spans, placeholders)
}

/** Обрезает уже склеенный текст с формулами, не теряя сдвинутые стили. */
private fun trimMergedText(merged: CdzMergedText): CdzMergedText {
    val first = merged.text.indexOfFirst { !it.isWhitespace() }
    if (first < 0) return CdzMergedText("", emptyList())
    val last = merged.text.indexOfLast { !it.isWhitespace() }
    val text = merged.text.substring(first, last + 1)
    val spans = merged.spans.mapNotNull { span ->
        val start = (span.start - first).coerceIn(0, text.length)
        val end = (span.end - first).coerceIn(0, text.length)
        if (end > start) span.copy(start = start, end = end) else null
    }
    val placeholders = merged.placeholders.map { placeholder ->
        placeholder.copy(position = (placeholder.position - first).coerceIn(0, text.length))
    }
    return CdzMergedText(text, spans, placeholders)
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

/**
 * Все значения `answer/number` / `answer/string`.
 *
 * У правильного ответа сервер часто перечисляет несколько допустимых
 * вариантов (`["10", "10.0"]`), а не одно значение. Для ввода ответа нужен
 * первый, а для показа — все.
 */
fun textAnswers(answer: JSONObject?): List<String> {
    val result = linkedSetOf<String>()
    answer?.optJSONArray("string")?.let { array ->
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf { it.isNotBlank() }?.let(result::add)
        }
    }
    answer?.optJSONArray("number")?.let { array ->
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf { it.isNotBlank() }?.let(result::add)
        }
    }
    if (result.isEmpty()) {
        answer?.optString("number").orEmpty().trim()
            .takeIf { it.isNotBlank() }?.let(result::add)
        answer?.optString("string").orEmpty().trim()
            .takeIf { it.isNotBlank() }?.let(result::add)
    }
    return result.toList()
}

/**
 * Ответы `answer/gap/text/input` в порядке пропусков из `answer.text_position`.
 *
 * Сервер кладёт их в `text_input_answer[].input_strings`, а не в `string`
 * или `number`, поэтому общий [textAnswer] их не видит. Массив
 * `text_input_answer` приходит в произвольном порядке, поэтому без сортировки
 * «Ваш ответ» и «Правильный ответ» выглядят перепутанными.
 */
fun gapPositions(answer: JSONObject?): List<CdzGapPosition> =
    gapPositions(answer?.optJSONArray("text_position"))

fun gapPositions(positions: JSONArray?): List<CdzGapPosition> {
    if (positions == null) return emptyList()
    return (0 until positions.length()).mapNotNull { index ->
        val position = positions.optJSONObject(index) ?: return@mapNotNull null
        val id = position.optString("position_id").trim()
        if (id.isBlank()) null else CdzGapPosition(
            id = id,
            textId = position.optString("text_id").trim(),
            position = position.optInt("position", -1),
        )
    }
}

fun gapAnswers(answer: JSONObject?, positions: JSONArray?): List<CdzGapAnswer> {
    val items = answer?.optJSONArray("text_input_answer") ?: return emptyList()
    val ordered = gapPositions(positions)
    val order = ordered.withIndex().associate { (index, position) -> position.id to index }

    val grouped = linkedMapOf<String, LinkedHashSet<String>>()
    val extra = linkedMapOf<String, LinkedHashSet<String>>()
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        val positionId = item.optString("position_id").trim()
        val strings = item.optJSONArray("input_strings") ?: continue
        val key = positionId.ifBlank { "item-$index" }
        val target = if (order.isEmpty() || order.containsKey(positionId)) grouped else extra
        for (stringIndex in 0 until strings.length()) {
            val value = strings.optString(stringIndex).trim()
            if (value.isNotBlank()) target.getOrPut(key) { linkedSetOf() }.add(value)
        }
    }

    if (order.isEmpty()) {
        return grouped.values.mapIndexed { index, values ->
            CdzGapAnswer(index = index, values = values.toList())
        }
    }

    val result = mutableListOf<CdzGapAnswer>()
    order.forEach { (positionId, index) ->
        val position = ordered[index]
        result += CdzGapAnswer(
            index = index,
            values = grouped[positionId]?.toList().orEmpty(),
            positionId = positionId,
            textId = position.textId,
            position = position.position,
        )
    }
    var nextIndex = order.size
    extra.values.forEach { values ->
        result += CdzGapAnswer(index = nextIndex++, values = values.toList())
    }
    return result
}

/**
 * Плоский список строк для проверок «есть ли ответ» и старых тестов.
 * Для отображения используйте [gapAnswers]: он сохраняет порядок пропусков.
 */
fun textInputAnswers(answer: JSONObject?): List<String> =
    gapAnswers(answer, null)
        .map { it.values.joinToString(" / ") }
        .filter { it.isNotBlank() }

/** Позиции на фоне image-selection задания; нумеруем в порядке API. */
fun imageGapPositions(answer: JSONObject): List<CdzImagePosition> {
    val positions = answer.optJSONArray("image_gaps_positions") ?: return emptyList()
    return (0 until positions.length()).mapNotNull { index ->
        val position = positions.optJSONObject(index) ?: return@mapNotNull null
        val id = position.optString("position_id").trim()
        if (id.isBlank()) null else CdzImagePosition(id = id, number = index + 1)
    }
}

/** Номера выбранных позиций в порядке фона, а не в порядке ответа сервера. */
fun imageGapSelection(
    answer: JSONObject?,
    positions: List<CdzImagePosition>,
): List<Int> {
    val ids = answer?.optJSONArray("position_ids") ?: return emptyList()
    val byId = positions.associate { it.id to it.number }
    return (0 until ids.length())
        .mapNotNull { index -> byId[ids.optString(index).trim()] }
        .distinct()
        .sorted()
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
    answer.optJSONArray("position_ids")?.let { return it.length() > 0 }
    if (textInputAnswers(answer).isNotEmpty()) return true
    answer.optJSONArray("groups")?.let { groups ->
        for (i in 0 until groups.length()) {
            val ids = groups.optJSONObject(i)?.optJSONArray("options_ids")
            if (ids != null && ids.length() > 0) return true
        }
        return false
    }
    answer.optJSONArray("cells")?.let { cells ->
        for (i in 0 until cells.length()) {
            val cell = cells.optJSONObject(i) ?: continue
            if (cell.optString("answer").isNotBlank()) return true
            if (cell.optString("option_id").isNotBlank()) return true
            cell.optJSONArray("option_ids")?.let { ids ->
                if (ids.length() > 0) return true
            }
            cell.optJSONArray("ids")?.let { ids ->
                if (ids.length() > 0) return true
            }
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
    answer.optJSONArray("position_ids")?.let { return it.length() > 0 }
    if (textInputAnswers(answer).isNotEmpty()) return true
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
