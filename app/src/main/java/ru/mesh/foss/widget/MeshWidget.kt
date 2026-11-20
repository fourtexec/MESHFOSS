package ru.mesh.foss.widget

import ru.mesh.foss.core.*
import ru.mesh.foss.data.*

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.unit.dp
import androidx.glance.unit.sp
import ru.mesh.foss.data.SessionStore
import ru.mesh.foss.core.Words
import ru.mesh.foss.app.MainActivity

/** Домашний виджет: уроки, ДЗ, пропуски и вход/выход на сегодня. */
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
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.widgetBackground)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "МЭШ",
                    style = TextStyle(
                        color = colors.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    snapshot.childName.ifBlank { Words.Today.text },
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (snapshot.lessonCount == 0 && snapshot.entry == null && snapshot.pendingHomework == 0) {
                Text(
                    Words.WidgetNoData.text,
                    style = TextStyle(color = colors.onSurface, fontSize = 14.sp),
                    maxLines = 3,
                )
            } else {
                val time = listOfNotNull(snapshot.firstLesson, snapshot.lastLesson).distinct().joinToString("–")
                Text(
                    if (time.isBlank()) Words.WidgetLessons(snapshot.lessonCount) else "${Words.WidgetLessons(snapshot.lessonCount)} · $time",
                    style = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    "${Words.WidgetHomework(snapshot.pendingHomework)}   ·   ${Words.Absences.text}: ${snapshot.absences}",
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
                    maxLines = 1,
                )
                if (snapshot.entry != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        "${Words.Entry.text} ${snapshot.entry}   ·   ${Words.Exit.text} ${snapshot.exit ?: Words.StillInside.text}",
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    Words.WidgetUpdated(WidgetUpdater.clock(snapshot.updatedAt)),
                    style = TextStyle(color = colors.outline, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }
}
