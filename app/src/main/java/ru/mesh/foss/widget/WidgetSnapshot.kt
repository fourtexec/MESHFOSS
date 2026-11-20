package ru.mesh.foss.widget

import org.json.JSONObject

/**
 * Компактные данные домашнего виджета.
 *
 * Виджет не делает сетевых запросов во время отрисовки: приложение обновляет
 * снимок в кэше и только после этого просит Glance перерисоваться. Это важно,
 * потому что Glance-композиция ограничена по времени и не должна ждать сеть.
 */
data class WidgetSnapshot(
    val childName: String = "",
    val date: String = "",
    val lessonCount: Int = 0,
    val firstLesson: String? = null,
    val lastLesson: String? = null,
    val pendingHomework: Int = 0,
    val absences: Int = 0,
    val entry: String? = null,
    val exit: String? = null,
    val updatedAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("childName", childName)
        .put("date", date)
        .put("lessonCount", lessonCount)
        .put("firstLesson", firstLesson ?: JSONObject.NULL)
        .put("lastLesson", lastLesson ?: JSONObject.NULL)
        .put("pendingHomework", pendingHomework)
        .put("absences", absences)
        .put("entry", entry ?: JSONObject.NULL)
        .put("exit", exit ?: JSONObject.NULL)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject?): WidgetSnapshot {
            if (json == null) return WidgetSnapshot()
            return WidgetSnapshot(
                childName = json.optString("childName"),
                date = json.optString("date"),
                lessonCount = json.optInt("lessonCount"),
                firstLesson = json.stringOrNull("firstLesson"),
                lastLesson = json.stringOrNull("lastLesson"),
                pendingHomework = json.optInt("pendingHomework"),
                absences = json.optInt("absences"),
                entry = json.stringOrNull("entry"),
                exit = json.stringOrNull("exit"),
                updatedAt = json.optLong("updatedAt"),
            )
        }

        private fun JSONObject.stringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}
