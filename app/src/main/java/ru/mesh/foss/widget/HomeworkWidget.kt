package ru.mesh.foss.widget

import ru.mesh.foss.app.MainActivity
import ru.mesh.foss.core.Words
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

/** Виджет «Домашние задания»: только невыполненные задания на сегодня. */
class HomeworkWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshot.fromJson(SessionStore(context).loadWidgetSnapshot())
        provideContent { HomeworkContent(snapshot) }
    }
}

@Composable
private fun HomeworkContent(snapshot: WidgetSnapshot) {
    GlanceTheme {
        val colors = GlanceTheme.colors
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
                    Words.WidgetHomework(snapshot.pendingHomework),
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
            if (snapshot.homeworks.isEmpty()) {
                Text(
                    Words.WidgetNoHomework.text,
                    style = TextStyle(color = colors.onSurface, fontSize = 13.sp),
                    maxLines = 3,
                )
            } else {
                snapshot.homeworks.take(5).forEach { item ->
                    val line = if (item.subject.isBlank()) item.description
                    else "${item.subject}: ${item.description}"
                    Text(
                        line,
                        style = TextStyle(color = colors.onSurface, fontSize = 12.sp),
                        maxLines = 2,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                }
                if (snapshot.homeworks.size > 5) {
                    Text(
                        Words.WidgetMoreHomework(snapshot.homeworks.size - 5),
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
