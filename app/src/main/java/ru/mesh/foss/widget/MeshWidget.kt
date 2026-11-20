package ru.mesh.foss.widget

import ru.mesh.foss.R
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.SessionStore
import ru.mesh.foss.app.MainActivity

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.util.Calendar

/**
 * Домашний виджет: что идёт сейчас, что будет следующим и когда это начнётся.
 * Отсчёт времени — нативный Chronometer, поэтому тикает без опроса сети.
 */
class MeshWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshot.fromJson(SessionStore(context).loadWidgetSnapshot())
        provideContent { WidgetContent(snapshot) }
    }
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    GlanceTheme {
        val colors = GlanceTheme.colors
        val nowMillis = System.currentTimeMillis()
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val lessons = snapshot.lessons.sortedBy { WidgetUpdater.minutesOfDay(it.begin) ?: Int.MAX_VALUE }
        val current = lessons.firstOrNull { lesson ->
            val from = WidgetUpdater.minutesOfDay(lesson.begin)
            val to = WidgetUpdater.minutesOfDay(lesson.end)
            from != null && to != null && to > from && nowMinutes in from until to
        }
        val next = lessons.firstOrNull { lesson ->
            val from = WidgetUpdater.minutesOfDay(lesson.begin)
            from != null && from > nowMinutes
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top,
        ) {
            // Шапка: приложение, ученик и текущее время.
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "МЭШ",
                    style = TextStyle(color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    snapshot.childName.ifBlank { Words.Today.text },
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    WidgetUpdater.clock(nowMillis),
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            when {
                current != null -> {
                    Text(
                        Words.WidgetAtLesson.text,
                        style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        current.subject,
                        style = TextStyle(color = colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Text(
                        lessonMeta(current.begin, current.end, current.room),
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                        maxLines = 1,
                    )
                    WidgetUpdater.minutesOfDay(current.end)?.let {
                        Countdown(it, nowMillis, Words.WidgetUntilEnd.text)
                    }
                    if (next != null) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            Words.WidgetNextAt(next.begin, next.subject),
                            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
                next != null -> {
                    Text(
                        Words.WidgetNextLesson.text,
                        style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        next.subject,
                        style = TextStyle(color = colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Text(
                        lessonMeta(next.begin, next.end, next.room),
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                        maxLines = 1,
                    )
                    WidgetUpdater.minutesOfDay(next.begin)?.let {
                        Countdown(it, nowMillis, Words.WidgetUntilStart.text)
                    }
                    if (next.homework.isNotEmpty()) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            Words.WidgetHomework(next.homework.size),
                            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                            maxLines = 1,
                        )
                        next.homework.take(2).forEach { item ->
                            Text(
                                "• $item",
                                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                                maxLines = 1,
                            )
                        }
                    } else {
                        lessons.filter { lesson ->
                            val from = WidgetUpdater.minutesOfDay(lesson.begin)
                            val nextFrom = WidgetUpdater.minutesOfDay(next.begin)
                            from != null && nextFrom != null && from > nextFrom
                        }.take(2).forEach { lesson ->
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                "${lesson.begin}  ${lesson.subject}",
                                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
                lessons.isNotEmpty() -> {
                    Text(
                        Words.WidgetLessonsOver.text,
                        style = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        maxLines = 2,
                    )
                }
                else -> {
                    Text(
                        Words.WidgetNoData.text,
                        style = TextStyle(color = colors.onSurface, fontSize = 13.sp),
                        maxLines = 3,
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "${Words.WidgetHomework(snapshot.pendingHomework)}  ·  ${
                    Words.WidgetUpdated(WidgetUpdater.clock(snapshot.updatedAt))
                }",
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

private fun lessonMeta(begin: String, end: String, room: String): String =
    "$begin–$end" + if (room.isBlank()) "" else "  ·  $room"

/** Живой обратный отсчёт без обновления виджета по сети. */
@Composable
private fun Countdown(targetMinutes: Int, nowMillis: Long, format: String) {
    val context = LocalContext.current
    val target = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, targetMinutes / 60)
        set(Calendar.MINUTE, targetMinutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
    }
    val views = RemoteViews(context.packageName, R.layout.widget_countdown)
    views.setChronometer(
        R.id.countdown,
        SystemClock.elapsedRealtime() + (target.timeInMillis - nowMillis),
        format,
        true,
    )
    // minSdk = 24, setChronometerCountDown доступен с API 24.
    views.setChronometerCountDown(R.id.countdown, true)
    AndroidRemoteViews(views)
}
