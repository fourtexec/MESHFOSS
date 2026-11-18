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
    val ids = listOf("midnight", "ocean", "forest", "sunset", "light", "dynamic", "custom")

    /** Соответствие «встроенный id → тройка цветов (акцент, фон, поверхность)». */
    private val palettes = mapOf(
        "midnight" to Triple(0xFF92DEED, 0xFF0D1724, 0xFF213445),
        "ocean" to Triple(0xFF67E8F9, 0xFF071923, 0xFF102A3A),
        "forest" to Triple(0xFF86EFAC, 0xFF0D1B16, 0xFF183126),
        "sunset" to Triple(0xFFFDA4AF, 0xFF211318, 0xFF352027),
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
    private fun parseCustom(value: String): Triple<Long, Long, Long>? = runCatching {
        val parts = value.split(",").map { it.trim().removePrefix("#").also { require(it.length == 6) }.toLong(16) or 0xFF000000L }
        if (parts.size < 3) null else Triple(parts[0], parts[1], parts[2])
    }.getOrNull()

    /** Человекочитаемое имя темы для настроек. */
    fun label(id: String, customName: String = "Моя тема"): String = when (id) {
        "midnight" -> "Полночь"
        "ocean" -> "Океан"
        "forest" -> "Лес"
        "sunset" -> "Закат"
        "light" -> "Светлая"
        "dynamic" -> "Динамическая (M3E)"
        "custom" -> customName.ifBlank { "Моя тема" }
        else -> id
    }
}

/** Применение темы из настроек: сохраняет id и перечитывает цвета. */
val LocalAppTheme = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Текущий язык интерфейса: `ru` или `en`. */
val LocalAppLanguage = staticCompositionLocalOf { "ru" }

/** Смена языка из настроек. */
val LocalAppLanguageSetter = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Выбор строки интерфейса по текущему языку. */
@Composable
fun tr(ru: String, en: String): String = if (LocalAppLanguage.current == "en") en else ru

/** Короткая подпись языка для селектора. */
fun languageLabel(code: String): String = if (code == "en") "English" else "Русский"
