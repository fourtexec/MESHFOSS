package ru.mesh.foss

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * «Матовое стекло»: полупрозрачные поверхности поверх мягкого цветного фона.
 *
 * Настоящего размытия того, что лежит под панелью, Compose не даёт, поэтому эффект
 * собирается из трёх частей: размытые цветные пятна на фоне ([GlassBackdrop]),
 * полупрозрачные контейнеры в цветовой схеме ([glassify]) и светлая кромка
 * у карточек ([glassBorder]). Включается в настройках ключом `glass_enabled`.
 */
object Glass {
    const val PREF_KEY = "glass_enabled"
    const val DEFAULT = true

    /**
     * Делает поверхности схемы полупрозрачными. Фон схемы остаётся непрозрачным:
     * его рисует [GlassBackdrop]. Диалоги и меню (surfaceContainer/High/Highest)
     * почти непрозрачны — под ними нет размытия, и текст не должен просвечивать.
     */
    fun glassify(scheme: ColorScheme): ColorScheme {
        val dark = scheme.background.luminance() < 0.5f
        val base = scheme.surface
        fun pane(lift: Float, alpha: Float) = lerp(base, Color.White, lift).copy(alpha = alpha)
        return if (dark) {
            scheme.copy(
                surface = pane(0.02f, 0.55f),
                surfaceContainerLowest = pane(0.02f, 0.40f),
                surfaceContainerLow = pane(0.05f, 0.48f),
                surfaceContainer = pane(0.06f, 0.82f),
                surfaceContainerHigh = pane(0.08f, 0.90f),
                surfaceContainerHighest = pane(0.07f, 0.55f),
                surfaceVariant = pane(0.10f, 0.50f)
            )
        } else {
            scheme.copy(
                surface = pane(0.0f, 0.60f),
                surfaceContainerLowest = pane(0.0f, 0.50f),
                surfaceContainerLow = pane(0.0f, 0.58f),
                surfaceContainer = pane(0.0f, 0.86f),
                surfaceContainerHigh = pane(0.0f, 0.92f),
                surfaceContainerHighest = pane(0.0f, 0.62f),
                surfaceVariant = pane(0.0f, 0.55f)
            )
        }
    }
}

/** Включено ли матовое стекло. */
val LocalGlass = staticCompositionLocalOf { false }

/** Переключение стекла из настроек. */
val LocalGlassSetter = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Светлая кромка стеклянной карточки: ярче сверху, гаснет к низу — как блик
 * на краю стекла. Без стекла — null (обычная карточка без рамки).
 */
@Composable
fun glassBorder(): BorderStroke? {
    if (!LocalGlass.current) return null
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = if (dark) 0.22f else 0.85f
    val bottom = if (dark) 0.04f else 0.30f
    return BorderStroke(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom)))
    )
}

/**
 * Фон под стеклом: цвет фона темы и три больших размытых пятна — акцент темы и
 * два соседних оттенка. Пятна статичны, чтобы не тратить батарею на анимацию.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val alpha = if (dark) 0.38f else 0.42f
    val accent = scheme.primary
    val blobs = listOf(
        accent,
        lerp(accent, Color(0xFF8B5CF6), 0.65f),
        lerp(accent, Color(0xFF06B6D4), 0.65f)
    )
    Box(modifier = modifier.fillMaxSize().background(scheme.background)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // На Android 12+ дополнительно размываем слой — пятна сливаются мягче.
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(48.dp) else Modifier)
        ) {
            val w = size.width
            val h = size.height
            val r = maxOf(w, h)
            val spots = listOf(
                Triple(Offset(w * 0.10f, h * 0.12f), r * 0.55f, blobs[0]),
                Triple(Offset(w * 0.95f, h * 0.45f), r * 0.50f, blobs[1]),
                Triple(Offset(w * 0.25f, h * 0.92f), r * 0.55f, blobs[2])
            )
            spots.forEach { (center, radius, color) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
        content()
    }
}
