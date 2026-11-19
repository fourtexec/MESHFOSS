package ru.mesh.foss

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color

/**
 * Система оформления приложения.
 *
 * Палитры хранятся в настройках ключом `theme_id`, пользовательская тема — строкой
 * `theme_colors` вида `АКЦЕНТ,ФОН,ПОВЕРХНОСТЬ` (HEX без решётки). Идентификаторы
 * совпадают с desktop/desktop-версией, чтобы настройки переносились между клиентами.
 */
object AppTheme {
    const val DEFAULT_ID = "midnight"

    /** Порядок вариантов в списке настроек. */
    val ids = listOf("midnight", "nebula", "ocean", "forest", "sunset", "light", "lavender", "amber", "glacier", "sakura", "graphite", "dynamic", "custom")

    /** Соответствие «встроенный id → тройка цветов (акцент, фон, поверхность)». */
    private val palettes = mapOf(
        // «Туманность» — тёмно-фиолетовая палитра под стеклянный стиль.
        "nebula" to Triple(0xFFC4B5FD, 0xFF0B0818, 0xFF1C1633),
        "midnight" to Triple(0xFFD1D5DB, 0xFF10131A, 0xFF1B2130),
        "ocean" to Triple(0xFF67E8F9, 0xFF071923, 0xFF102A3A),
        "forest" to Triple(0xFF86EFAC, 0xFF0D1B16, 0xFF183126),
        "sunset" to Triple(0xFFFDA4AF, 0xFF211318, 0xFF352027),
        "lavender" to Triple(0xFFCFBAFF, 0xFF1C182B, 0xFF312842),
        "amber" to Triple(0xFFFFD17B, 0xFF211B10, 0xFF39301D),
        "glacier" to Triple(0xFF005F80, 0xFFE7F8FF, 0xFFF8FCFF),
        "sakura" to Triple(0xFF993853, 0xFFFCEEF4, 0xFFFFF8FB),
        "graphite" to Triple(0xFFD1D5DB, 0xFF141517, 0xFF2B2D31),
        "light" to Triple(0xFF2563EB, 0xFFF5F7FB, 0xFFFFFFFF)
    )

    /** Цветовая схема Material 3 по id темы и (для `custom`) строке пользовательских цветов. */
    fun colors(id: String, custom: String?): ColorScheme {
        val palette = if (id == "custom" && !custom.isNullOrBlank()) {
            parseCustom(custom) ?: palettes.getValue(DEFAULT_ID)
        } else {
            palettes[id] ?: palettes.getValue(DEFAULT_ID)
        }
        val primary = Color(palette.first)
        val background = Color(palette.second)
        val surface = Color(palette.third)
        if (id == "nebula") {
            return darkColorScheme(
                primary = primary,
                onPrimary = Color(0xFF1E1240),
                primaryContainer = Color(0xFF4C2F9E),
                onPrimaryContainer = Color(0xFFEDE7FF),
                secondary = Color(0xFFA5B4FC),
                secondaryContainer = Color(0xFF3A2D78),
                onSecondaryContainer = Color(0xFFEDE7FF),
                tertiary = Color(0xFFF0ABFC),
                background = background,
                surface = surface,
                onBackground = Color(0xFFEDEBF7),
                onSurface = Color(0xFFEDEBF7),
                onSurfaceVariant = Color(0xFFB9B3D1),
                outline = Color(0xFF8B84A8)
            )
        }
        fun ink(color: Color) = if (color.luminance() > .179f) Color.Black else Color.White
        val base = if (background.luminance() > .179f) lightColorScheme() else darkColorScheme()
        val container = lerp(surface, primary, .22f)
        return base.copy(
            primary = primary, onPrimary = ink(primary),
            primaryContainer = container, onPrimaryContainer = ink(container),
            secondaryContainer = container, onSecondaryContainer = ink(container),
            background = background, onBackground = ink(background),
            surface = surface, onSurface = ink(surface),
            onSurfaceVariant = lerp(ink(surface), surface, .22f),
            surfaceContainerLow = lerp(background, surface, .8f),
            surfaceContainer = surface,
            surfaceContainerHigh = lerp(surface, primary, .09f),
            surfaceContainerHighest = lerp(surface, primary, .14f)
        )
    }

    /** `"67E8F9,071923,102A3A"` → Triple. Неверный формат → null (палитра по умолчанию). */
    fun validCustom(value: String) = parseCustom(value) != null

    private fun parseCustom(value: String): Triple<Long, Long, Long>? = runCatching {
        val parts = value.split(",").map { it.trim().removePrefix("#").also { require(it.length == 6) }.toLong(16) or 0xFF000000L }
        if (parts.size != 3) null else Triple(parts[0], parts[1], parts[2])
    }.getOrNull()

    /** Человекочитаемое имя темы для настроек. */
    fun label(id: String, customName: String = Words.MyTheme.text): String = when (id) {
        "nebula" -> Words.Nebula.text
        "midnight" -> Words.Midnight.text
        "ocean" -> Words.Ocean.text
        "forest" -> Words.Forest.text
        "sunset" -> Words.Sunset.text
        "lavender" -> Words.Lavender.text
        "amber" -> Words.Amber.text
        "glacier" -> Words.Glacier.text
        "sakura" -> Words.CherryBlossom.text
        "graphite" -> Words.Graphite.text
        "light" -> Words.Light.text
        "dynamic" -> Words.SystemColours.text
        "custom" -> customName.ifBlank { Words.MyTheme.text }
        else -> id
    }
}

/** Применение темы из настроек: сохраняет id и перечитывает цвета. */
val LocalAppTheme = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Смена языка из настроек. */
val LocalAppLanguageSetter = staticCompositionLocalOf<(String) -> Unit> { {} }
