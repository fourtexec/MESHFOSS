package ru.mesh.foss.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Приёмник виджета «Расписание». */
class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ScheduleWidget()
}
