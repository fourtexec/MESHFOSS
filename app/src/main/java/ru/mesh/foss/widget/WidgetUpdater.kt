package ru.mesh.foss.widget

import ru.mesh.foss.core.*
import ru.mesh.foss.data.*

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Загружает снимок для виджета и просит Glance перерисовать все экземпляры. */
object WidgetUpdater {

    suspend fun refresh(
        context: Context,
        client: MeshClient,
        store: SessionStore,
        studentId: String?,
        personId: String?,
    ) = withContext(Dispatchers.IO) {
        val student = studentId?.trim().orEmpty()
        if (student.isBlank()) return@withContext
        val today = MeshCore.today()
        val schedule = runCatching { client.getSchedule(student, today) }.getOrNull()
        val homeworks = runCatching { client.getHomeworks(student, today, today) }.getOrNull().orEmpty()

        val pending = homeworks.filter { !it.optBoolean("isDone") }
        val homeworkBySubject = pending
            .groupBy { normalizeSubject(it.optString("subjectName")) }
            .mapValues { (_, items) ->
                items.mapNotNull { it.optString("description").takeIf(String::isNotBlank) }.take(2)
            }

        val lessons = schedule.toLessonList().mapNotNull { lesson ->
            val begin = lesson.string("begin") ?: return@mapNotNull null
            val end = lesson.string("end") ?: return@mapNotNull null
            val subject = lesson.string("subject")?.takeIf { it != "—" } ?: return@mapNotNull null
            WidgetLesson(
                begin = begin,
                end = end,
                subject = subject,
                room = lesson.string("room").orEmpty(),
                homework = homeworkBySubject[normalizeSubject(subject)].orEmpty(),
            )
        }.sortedBy { minutesOfDay(it.begin) ?: Int.MAX_VALUE }

        val snapshot = WidgetSnapshot(
            childName = store.studentName.orEmpty(),
            date = today,
            lessons = lessons,
            pendingHomework = pending.size,
            updatedAt = System.currentTimeMillis(),
        )
        store.saveWidgetSnapshot(snapshot.toJson().toString())
        runCatching { MeshWidget().updateAll(context) }
    }

    private fun JSONObject.string(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONArray?.toLessonList(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun normalizeSubject(value: String): String =
        value.trim().lowercase(Locale.getDefault())

    /** Минуты от полуночи для сравнения с текущим временем. */
    fun minutesOfDay(value: String): Int? {
        val parts = value.trim().split(":")
        if (parts.size < 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null
        return hours * 60 + minutes
    }

    fun clock(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}
