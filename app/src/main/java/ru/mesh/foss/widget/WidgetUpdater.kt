package ru.mesh.foss.widget

import ru.mesh.foss.core.*
import ru.mesh.foss.data.*

import android.content.Context
import androidx.glance.appwidget.updateAll
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.data.SessionStore

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
        val person = personId?.trim().orEmpty()
        if (student.isBlank() || person.isBlank()) return@withContext
        val today = MeshCore.today()
        val schedule = runCatching { client.getSchedule(student, today) }.getOrNull()
        val homeworks = runCatching { client.getHomeworks(student, today, today) }.getOrNull()
        val attendance = runCatching { client.getAttendance(student, today, today) }.getOrNull()
        val visits = runCatching { client.getVisitDurations(person, today, today) }.getOrNull()

        val lessons = schedule.toLessonList()
        val first = visits?.payload?.firstOrNull()?.visits?.firstOrNull()
        val snapshot = WidgetSnapshot(
            childName = store.studentName.orEmpty(),
            date = today,
            lessonCount = lessons.size,
            firstLesson = lessons.mapNotNull { it.optString("begin").takeIf(String::isNotBlank) }.minOrNull(),
            lastLesson = lessons.mapNotNull { it.optString("end").takeIf(String::isNotBlank) }.maxOrNull(),
            pendingHomework = homeworks.orEmpty().count { !it.optBoolean("isDone") },
            absences = attendance?.attendance?.sumOf { it.lessons.size } ?: 0,
            entry = first?.enteredAt?.takeIf { it != "-" },
            exit = first?.leftAt?.takeIf { it != "-" },
            updatedAt = System.currentTimeMillis(),
        )
        store.saveWidgetSnapshot(snapshot.toJson().toString())
        runCatching { MeshWidget().updateAll(context) }
    }

    private fun JSONArray?.toLessonList(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    fun clock(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}
