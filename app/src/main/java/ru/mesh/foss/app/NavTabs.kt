package ru.mesh.foss.app

import ru.mesh.foss.R
import ru.mesh.foss.core.Words

import android.content.Context

/** Раскладка нижней панели (не больше [MAX_BOTTOM]) и меню «Ещё». */
data class NavLayout(val bottom: List<String>, val more: List<String>)

/**
 * Конфигурация вкладок. Пользователь может менять порядок и переносить
 * разделы между панелью и «Ещё»; выбор сохраняется в приватных настройках.
 */
object NavTabs {
    const val MAX_BOTTOM = 4

    private const val PREF = "mesh_settings"
    private const val KEY_BOTTOM = "nav_bottom"
    private const val KEY_MORE = "nav_more"

    val ALL = listOf(
        "ranking", "marks", "homeworks", "schedule",
        "attendance", "moskvenok", "olympiads", "food", "settings"
    )
    val DEFAULT_BOTTOM = listOf("ranking", "marks", "homeworks", "schedule")
    val DEFAULT_MORE = listOf("attendance", "moskvenok", "olympiads", "food", "settings")

    fun label(key: String): String = when (key) {
        "ranking" -> Words.Ranking.text
        "marks" -> Words.GradesTab.text
        "homeworks" -> Words.Tasks.text
        "schedule" -> Words.ScheduleTab.text
        "attendance" -> Words.Attendance.text
        "moskvenok" -> Words.Moskvenok.text
        "olympiads" -> Words.Olympiads.text
        "food" -> Words.MealsAndBalance.text
        "settings" -> Words.Settings.text
        else -> Words.More.text
    }

    fun icon(key: String): Int = when (key) {
        "ranking" -> R.drawable.ic_nav_ranking
        "marks" -> R.drawable.ic_nav_marks
        "homeworks" -> R.drawable.ic_nav_homeworks
        "schedule" -> R.drawable.ic_nav_schedule
        "attendance" -> R.drawable.ic_nav_attendance
        "moskvenok" -> R.drawable.ic_nav_moskvenok
        "olympiads" -> R.drawable.ic_nav_olympiads
        "food" -> R.drawable.ic_nav_food
        "settings" -> R.drawable.ic_settings
        else -> R.drawable.ic_nav_more
    }

    fun load(context: Context): NavLayout {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val bottom = prefs.getString(KEY_BOTTOM, null)?.split(',')?.filter { it.isNotBlank() }
            ?: DEFAULT_BOTTOM
        val more = prefs.getString(KEY_MORE, null)?.split(',')?.filter { it.isNotBlank() }
            ?: DEFAULT_MORE
        return sanitize(bottom, more)
    }

    fun save(context: Context, layout: NavLayout) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_BOTTOM, layout.bottom.joinToString(","))
            .putString(KEY_MORE, layout.more.joinToString(","))
            .apply()
    }

    /** Убирает неизвестные и продублированные ключи, ограничивает панель четырьмя. */
    fun sanitize(bottom: List<String>, more: List<String>): NavLayout {
        val b = bottom.filter { it in ALL }.distinct().take(MAX_BOTTOM)
        val m = LinkedHashMap<String, Unit>()
        more.filter { it in ALL && it !in b }.forEach { m[it] = Unit }
        ALL.forEach { if (it !in b && it !in m) m[it] = Unit }
        return NavLayout(b, m.keys.toList())
    }
}
