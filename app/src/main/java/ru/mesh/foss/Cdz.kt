package ru.mesh.foss

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * ЦДЗ — цифровые домашние задания МЭШ (тесты Библиотеки МЭШ, «Облако Знаний» и т. п.).
 *
 * Протокол описан в docs/mesh-cdz-api.md:
 *  - §5 `POST /api/family/materials/v1/additional_materials` — расширенные карточки;
 *  - §6 `GET /api/lrs/v2/results` — выполнено / баллы / ссылка на результат;
 *  - §7 `GET /api/ej/partners/v1/homeworks/launch` — одноразовая ссылка запуска.
 *
 * Файл не зависит от Android: только org.json и java.* — покрыт JVM-тестами.
 */
object MeshCdz {

    /** `work_type` в LRS и в JWT запуска: 1 = домашнее задание. */
    const val WORK_TYPE_HOMEWORK = "1"

    /** Назначение материала в запросе additional_materials. */
    const val PURPOSE_HOME = "for_home"

    const val TYPE_FIZIKON = "fizikon_module"
    const val TYPE_TEST_TASK = "test_task_binding"
    const val TYPE_TEST_SPEC = "test_spec_binding"

    private val KNOWN_TYPES = setOf(TYPE_FIZIKON, TYPE_TEST_TASK, TYPE_TEST_SPEC)
    private val ATTACHMENT_TYPES = setOf("attachment", "attachments", "file", "files")

    /**
     * Является ли материал ЦДЗ — то есть запускается через launch с контекстом ДЗ
     * и имеет статус выполнения в LRS. Файлы-вложения ЦДЗ не считаются.
     */
    fun isCdz(material: JSONObject): Boolean {
        val type = material.optString("type").lowercase(Locale.US)
        if (type in ATTACHMENT_TYPES) return false
        if (material.optString("uuid").isBlank()) return false
        return type in KNOWN_TYPES ||
            material.optString("selected_mode").equals("execute", ignoreCase = true) ||
            material.optInt("action_id") == 1
    }

    /**
     * Подпись типа как в веб-дневнике («Самостоятельная работа», «Задание»,
     * «Тематический тест»). Для неизвестных типов — `type_name` из ответа МЭШ.
     */
    fun typeLabel(type: String?, fallback: String?): String = when (type?.lowercase(Locale.US)) {
        TYPE_FIZIKON -> Words.CdzSelfWork.text
        TYPE_TEST_TASK -> Words.CdzTask.text
        TYPE_TEST_SPEC -> Words.CdzThematicTest.text
        else -> fallback?.trim().orEmpty()
    }

    /** Открытие материала «Облака Знаний» сразу запускает таймер работы (§8). */
    fun startsTimer(type: String?): Boolean = type.equals(TYPE_FIZIKON, ignoreCase = true)

    // ------------------------------------------------- §5 additional_materials --

    /**
     * Тело `POST additional_materials`. [entryStudentId] — `homework_entry_student_id`
     * (не `homework_entry_id`!), [materials] — нормализованные материалы ДЗ.
     */
    fun additionalMaterialsBody(entryStudentId: String, materials: List<JSONObject>): JSONObject {
        val list = JSONArray()
        val entry: Any = entryStudentId.toLongOrNull() ?: entryStudentId
        materials.forEach { material ->
            val uuid = material.optString("uuid").ifBlank { material.optString("materialId") }
            if (uuid.isBlank()) return@forEach
            list.put(
                JSONObject()
                    .put("uuid", uuid)
                    .put("purpose", PURPOSE_HOME)
                    .put(
                        "selected_mode",
                        material.optString("selectedMode")
                            .ifBlank { material.optString("selected_mode") }
                            .ifBlank { "execute" }
                    )
                    .put("homework_entry_student_id", entry)
            )
        }
        return JSONObject().put("materials", list)
    }

    /**
     * Ответ additional_materials → карточки по `uuid`.
     * Порядок в ответе может не совпадать с порядком в ДЗ, поэтому — словарь.
     */
    fun normalizeAdditionalMaterials(json: JSONObject?): Map<String, JSONObject> {
        val result = linkedMapOf<String, JSONObject>()
        val groups = json?.optJSONArray("additional_materials") ?: return result
        for (i in 0 until groups.length()) {
            val purpose = groups.optJSONObject(i) ?: continue
            val materialGroups = purpose.optJSONArray("material_groups") ?: continue
            for (j in 0 until materialGroups.length()) {
                val group = materialGroups.optJSONObject(j) ?: continue
                val materials = group.optJSONArray("materials") ?: continue
                for (k in 0 until materials.length()) {
                    val material = materials.optJSONObject(k) ?: continue
                    val uuid = material.optString("uuid")
                    if (uuid.isBlank()) continue
                    result[uuid] = normalizeExtendedMaterial(material)
                }
            }
        }
        return result
    }

    private fun normalizeExtendedMaterial(material: JSONObject): JSONObject {
        var viewUrl = ""
        var launchUrl = ""
        val urls = material.optJSONArray("urls") ?: JSONArray()
        for (i in 0 until urls.length()) {
            val entry = urls.optJSONObject(i) ?: continue
            val url = entry.optString("url").trim()
            // МЭШ кладёт строку "null" вместо отсутствующей ссылки.
            if (url.isBlank() || url == "null") continue
            when (entry.optString("type")) {
                "view" -> viewUrl = url
                "launch" -> launchUrl = url
            }
        }
        val partner = material.optJSONObject("partner") ?: material.optJSONObject("publishing_house")
        val type = material.optString("type")
        return JSONObject()
            .put("uuid", material.optString("uuid"))
            .put("type", type)
            .put("typeName", typeLabel(type, material.optString("type_name")))
            .put("title", MeshCore.asString(material.opt("title")))
            .put("description", MeshCore.asString(material.opt("description")).trim())
            .put("actionName", material.optString("action_name"))
            .put("partnerName", partner?.optString("name").orEmpty())
            .put("partnerIcon", partner?.optString("icon_url").orEmpty().let { if (it == "null") "" else it })
            // Ссылки без контекста: для справки, но НЕ для прохождения (§5, §12.5).
            .put("viewUrl", viewUrl)
            .put("launchUrl", launchUrl)
    }

    // ------------------------------------------------------------------ §6 LRS --

    /**
     * Параметр `context` запроса LRS. `work_id` — `homework_entry_id`
     * (не `homework_entry_student_id`), `work_type` — строка "1".
     */
    fun lrsContext(materialId: String, personId: String, homeworkEntryId: String): String {
        val workId: Any = homeworkEntryId.toLongOrNull() ?: homeworkEntryId
        // Порядок ключей как у веб-дневника.
        return "{" +
            "\"material_id\":${JSONObject.quote(materialId)}," +
            "\"person_id\":${JSONObject.quote(personId)}," +
            "\"work_type\":\"$WORK_TYPE_HOMEWORK\"," +
            "\"work_id\":${if (workId is Long) workId.toString() else JSONObject.quote(workId.toString())}" +
            "}"
    }

    /**
     * Страница результатов LRS → статус материала.
     *
     * Пустой `content` — «не выполнено». Из нескольких попыток берём завершённую
     * (`final`), а среди них — самую позднюю.
     */
    fun normalizeLrsResults(json: JSONObject?): JSONObject {
        val content = json?.optJSONArray("content") ?: JSONArray()
        val attempts = (0 until content.length()).mapNotNull { content.optJSONObject(it) }
        if (attempts.isEmpty()) {
            return JSONObject()
                .put("done", false)
                .put("started", false)
                .put("attempts", 0)
        }
        val best = attempts
            .sortedWith(
                compareByDescending<JSONObject> { isFinished(it) }
                    .thenByDescending { it.optString("started_at") }
            )
            .first()

        var score: Double? = null
        var maxScore: Double? = null
        val items = best.optJSONArray("items")
        if (items != null && items.length() > 0) {
            var sum = 0.0
            var max = 0.0
            var any = false
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                MeshCore.asNullableDouble(item.opt("score"))?.let { sum += it; any = true }
                MeshCore.asNullableDouble(item.opt("max_score"))?.let { max += it; any = true }
            }
            if (any) {
                score = sum
                maxScore = max
            }
        }
        if (score == null) {
            score = MeshCore.asNullableDouble(best.opt("score"))
            maxScore = MeshCore.asNullableDouble(best.opt("max_score"))
        }

        val done = isFinished(best)
        fun url(key: String): String = best.optString(key).trim().let { if (it == "null") "" else it }
        return JSONObject()
            .put("done", done)
            .put("started", true)
            .put("success", if (best.has("success") && !best.isNull("success")) best.optBoolean("success") else JSONObject.NULL)
            .put("status", best.optInt("status"))
            .put("startedAt", best.optString("started_at").let { if (it == "null") "" else it })
            .put("duration", MeshCore.asNullableInt(best.opt("duration")) ?: JSONObject.NULL)
            .put("score", score ?: JSONObject.NULL)
            .put("maxScore", maxScore ?: JSONObject.NULL)
            .put("mark", MeshCore.asNullableInt(best.opt("recommended_mark")) ?: JSONObject.NULL)
            .put("markScale", MeshCore.asNullableInt(best.opt("recommended_mark_scale")) ?: JSONObject.NULL)
            .put("resultUrl", url("url_details"))
            .put("improveUrl", url("url_improve"))
            .put("registration", best.optString("registration"))
            .put("attempts", attempts.size)
    }

    /** status 2 — завершено; `final: true` тоже означает итоговую попытку. */
    private fun isFinished(attempt: JSONObject): Boolean =
        attempt.optBoolean("final") || attempt.optInt("status") == 2

    // ---------------------------------------------------------------- подписи --

    /** «Выполнено 20 ноя в 18:39 за 1 м 46 с» / «Начато, не завершено» / «Не выполнено». */
    fun statusText(result: JSONObject?, timeZone: TimeZone = TimeZone.getDefault()): String {
        if (result == null) return ""
        if (!result.optBoolean("started")) return Words.CdzNotCompleted.text
        if (!result.optBoolean("done")) return Words.CdzInProgress.text
        val started = result.optString("startedAt")
        val parsed = parseInstant(started)
        val base = if (parsed != null) {
            Words.CdzCompletedAt(dayMonth(parsed, timeZone), clock(parsed, timeZone))
        } else {
            Words.CdzCompleted.text
        }
        val duration = MeshCore.asNullableInt(result.opt("duration"))
        return if (duration != null && duration > 0) "$base ${Words.CdzInDuration(formatDuration(duration))}" else base
    }

    /** «1/1 баллов» или пустая строка, если баллов нет. */
    fun scoreText(result: JSONObject?): String {
        if (result == null || !result.optBoolean("done")) return ""
        val score = MeshCore.asNullableDouble(result.opt("score")) ?: return ""
        val max = MeshCore.asNullableDouble(result.opt("maxScore"))
        val left = MeshCore.asString(MeshCore.round(score, 1))
        return if (max != null && max > 0) {
            Words.CdzScore(left, MeshCore.asString(MeshCore.round(max, 1)))
        } else {
            Words.CdzScoreOnly(left)
        }
    }

    /** 106 → «1 м 46 с», 3720 → «1 ч 2 м», 40 → «40 с». */
    fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return Words.DurationSeconds(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> listOfNotNull(Words.DurationHours(h), if (m > 0) Words.DurationMinutes(m) else null).joinToString(" ")
            m > 0 -> listOfNotNull(Words.DurationMinutes(m), if (s > 0) Words.DurationSeconds(s) else null).joinToString(" ")
            else -> Words.DurationSeconds(s)
        }
    }

    /** ISO-8601 с долями секунды и `Z`/смещением → миллисекунды UTC. */
    fun parseInstant(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        val match = Regex("^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}(?::\\d{2})?)(?:\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?$")
            .find(text) ?: return null
        val (date, time, zone) = match.destructured
        val fullTime = if (time.length == 5) "$time:00" else time
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        format.timeZone = when {
            zone.isBlank() || zone == "Z" -> TimeZone.getTimeZone("UTC")
            else -> TimeZone.getTimeZone("GMT" + zone.let { if (it.contains(':')) it else it.substring(0, 3) + ":" + it.substring(3) })
        }
        return runCatching { format.parse("${date}T$fullTime")?.time }.getOrNull()
    }

    private val RU_MONTHS_SHORT = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

    private fun dayMonth(millis: Long, timeZone: TimeZone): String {
        val calendar = java.util.Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        if (UiLanguage.code == "ru") {
            return "${calendar.get(java.util.Calendar.DAY_OF_MONTH)} ${RU_MONTHS_SHORT[calendar.get(java.util.Calendar.MONTH)]}"
        }
        return SimpleDateFormat("d MMM", UiLanguage.locale).apply { this.timeZone = timeZone }.format(millis)
    }

    private fun clock(millis: Long, timeZone: TimeZone): String =
        SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = timeZone }.format(millis)
}
