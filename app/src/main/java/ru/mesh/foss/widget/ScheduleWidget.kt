package ru.mesh.foss.widget

import ru.mesh.foss.app.MainActivity
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.AccountRegistry
import ru.mesh.foss.data.SessionStore

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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

/** Виджет «Расписание на сегодня»: список уроков с текущим выделением. */
class ScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshot.fromJson(SessionStore(context, AccountRegistry(context).activeScope()).loadWidgetSnapshot())
        provideContent { ScheduleContent(snapshot) }
    }
}

@Composable
private fun ScheduleContent(snapshot: WidgetSnapshot) {
    GlanceTheme {
        val colors = GlanceTheme.colors
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "МЭШ",
                    style = TextStyle(color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    Words.Schedule.text,
                    style = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    snapshot.childName,
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (snapshot.lessons.isEmpty()) {
                Text(
                    Words.WidgetNoData.text,
                    style = TextStyle(color = colors.onSurface, fontSize = 13.sp),
                    maxLines = 3,
                )
            } else {
                snapshot.lessons.take(6).forEach { lesson ->
                    val from = WidgetUpdater.minutesOfDay(lesson.begin)
                    val to = WidgetUpdater.minutesOfDay(lesson.end)
                    val active = from != null && to != null && to > from && nowMinutes in from until to
                    val line = buildString {
                        append(lesson.begin.ifBlank { "–" })
                        append("  ")
                        append(lesson.subject)
                        if (lesson.room.isNotBlank()) append("  ·  ").append(lesson.room)
                    }
                    Text(
                        line,
                        style = TextStyle(
                            color = if (active) colors.primary else colors.onSurface,
                            fontSize = if (active) 14.sp else 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                }
                if (snapshot.lessons.size > 6) {
                    Text(
                        Words.WidgetMoreLessons(snapshot.lessons.size - 6),
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                Words.WidgetUpdated(WidgetUpdater.clock(snapshot.updatedAt)),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}
