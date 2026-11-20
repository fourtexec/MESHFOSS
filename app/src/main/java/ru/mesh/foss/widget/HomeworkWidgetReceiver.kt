package ru.mesh.foss.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Приёмник виджета «Домашние задания». */
class HomeworkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = HomeworkWidget()
}
