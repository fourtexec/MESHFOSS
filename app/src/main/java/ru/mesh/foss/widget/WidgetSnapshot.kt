package ru.mesh.foss.widget

import org.json.JSONArray
import org.json.JSONObject

/** Один урок в снимке виджета. */
data class WidgetLesson(
    val begin: String = "",
    val end: String = "",
    val subject: String = "",
    val room: String = "",
    val homework: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("begin", begin)
        .put("end", end)
        .put("subject", subject)
        .put("room", room)
        .put("homework", JSONArray().putAll(homework))

    companion object {
        fun fromJson(json: JSONObject?): WidgetLesson? {
            if (json == null) return null
            val subject = json.optString("subject").takeIf { it.isNotBlank() } ?: return null
            val homework = json.optJSONArray("homework")
            val texts = (0 until (homework?.length() ?: 0)).mapNotNull { homework?.optString(it)?.takeIf(String::isNotBlank) }
            return WidgetLesson(
                begin = json.optString("begin"),
                end = json.optString("end"),
                subject = subject,
                room = json.optString("room"),
                homework = texts,
            )
        }
    }
}

data class WidgetHomework(
    val subject: String = "",
    val description: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("subject", subject)
        .put("description", description)

    companion object {
        fun fromJson(json: JSONObject?): WidgetHomework? {
            if (json == null) return null
            return WidgetHomework(
                subject = json.optString("subject"),
                description = json.optString("description"),
            )
        }
    }
}

/**
 * Компактные данные домашнего виджета: только то, что реально нужно на
 * главном экране — расписание на день и невыполненное ДЗ.
 *
 * Виджет не делает сетевых запросов во время отрисовки: приложение обновляет
 * снимок в кэше и только после этого просит Glance перерисоваться. Это важно,
 * потому что Glance-композиция ограничена по времени и не должна ждать сеть.
 */
data class WidgetSnapshot(
    val childName: String = "",
    val date: String = "",
    val lessons: List<WidgetLesson> = emptyList(),
    val homeworks: List<WidgetHomework> = emptyList(),
    val pendingHomework: Int = 0,
    val updatedAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("childName", childName)
        .put("date", date)
        .put("lessons", JSONArray().putAll(lessons.map { it.toJson() }))
        .put("homeworks", JSONArray().putAll(homeworks.map { it.toJson() }))
        .put("pendingHomework", pendingHomework)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject?): WidgetSnapshot {
            if (json == null) return WidgetSnapshot()
            val array = json.optJSONArray("lessons")
            var lessons = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
                .mapNotNull { WidgetLesson.fromJson(it) }
            if (lessons.isEmpty()) {
                // Совместимость со снимками beta.1: там был только первый/последний урок.
                val first = json.stringOrNull("firstLesson")
                val last = json.stringOrNull("lastLesson")
                if (first != null || last != null) {
                    lessons = listOf(
                        WidgetLesson(
                            begin = first.orEmpty(),
                            end = last.orEmpty(),
                            subject = WidgetLegacySubject,
                        )
                    )
                }
            }
            var homeworks = (0 until (json.optJSONArray("homeworks")?.length() ?: 0))
                .mapNotNull { json.optJSONArray("homeworks")?.optJSONObject(it) }
                .mapNotNull { WidgetHomework.fromJson(it) }
            if (homeworks.isEmpty()) {
                // Старый снимок: ДЗ было только внутри урока.
                homeworks = lessons.flatMap { lesson ->
                    lesson.homework.map { text -> WidgetHomework(lesson.subject, text) }
                }
            }
            return WidgetSnapshot(
                childName = json.optString("childName"),
                date = json.optString("date"),
                lessons = lessons,
                homeworks = homeworks,
                pendingHomework = json.optInt("pendingHomework"),
                updatedAt = json.optLong("updatedAt"),
            )
        }

        private const val WidgetLegacySubject = "—"

        private fun JSONObject.stringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}

private fun JSONArray.putAll(values: Iterable<Any>): JSONArray = apply {
    values.forEach { put(it) }
}
