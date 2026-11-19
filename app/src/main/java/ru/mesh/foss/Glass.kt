package ru.mesh.foss

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * «Стекло» (glassmorphism): полупрозрачные панели со светлой кромкой поверх
 * тёмного фона с мягкими цветными свечениями — как матовая пластина над
 * неоновым туманом.
 *
 * Размыть то, что лежит под панелью, Compose не умеет, но фон здесь статичный и
 * уже размытый (радиальные свечения), поэтому полупрозрачная панель поверх него
 * выглядит как матовое стекло. Составные части:
 *  - [GlassBackdrop] — фон: базовый градиент и три-четыре свечения;
 *  - [Glass.glassify] — полупрозрачные контейнеры в цветовой схеме;
 *  - [Modifier.glass] — блик внутри карточки и градиентная кромка.
 *
 * Включается в настройках ключом `glass_enabled` (по умолчанию включено).
 */
object Glass {
    const val PREF_KEY = "glass_enabled"
    const val DEFAULT = true

    private val Violet = Color(0xFF7C3AED)
    private val Indigo = Color(0xFF4F46E5)
    private val Magenta = Color(0xFFC026D3)

    /**
     * Цвета свечений фона. Для «Туманности» — фиолетовый, индиго и пурпур как на
     * эталонном макете; для остальных тем свечения выводятся из акцента, чтобы
     * стекло подстраивалось под выбранную палитру.
     */
    fun style(themeId: String, scheme: ColorScheme): GlassStyle {
        val dark = scheme.background.luminance() < 0.5f
        val glows = if (themeId == "nebula") {
            listOf(Violet, Indigo, Magenta)
        } else {
            val accent = scheme.primary
            listOf(
                accent,
                lerp(accent, Violet, 0.55f),
                lerp(accent, Indigo, 0.55f)
            )
        }
        return GlassStyle(dark = dark, glows = glows)
    }

    /**
     * Делает поверхности карточек полупрозрачными. Фон схемы не трогаем — его
     * рисует [GlassBackdrop]. Контейнеры диалогов и меню (surfaceContainer/High)
     * остаются почти непрозрачными: под ними нет фона-тумана, и текст не должен
     * просвечивать сквозь них.
     */
    fun glassify(scheme: ColorScheme): ColorScheme {
        val dark = scheme.background.luminance() < 0.5f
        fun pane(alpha: Float) = Color.White.copy(alpha = alpha)
        fun solid(lift: Float, alpha: Float) =
            lerp(scheme.surface, if (dark) Color.White else Color.Black, lift).copy(alpha = alpha)
        return if (dark) {
            scheme.copy(
                surfaceContainerLowest = pane(0.04f),
                surfaceContainerLow = pane(0.07f),
                surfaceContainerHighest = pane(0.09f),
                surfaceVariant = pane(0.10f),
                surfaceContainer = solid(0.05f, 0.96f),
                surfaceContainerHigh = solid(0.08f, 0.97f),
                outlineVariant = Color.White.copy(alpha = 0.14f)
            )
        } else {
            scheme.copy(
                surfaceContainerLowest = pane(0.45f),
                surfaceContainerLow = pane(0.55f),
                surfaceContainerHighest = pane(0.62f),
                surfaceVariant = pane(0.60f),
                surfaceContainer = solid(0.0f, 0.96f),
                surfaceContainerHigh = solid(0.0f, 0.98f),
                outlineVariant = Color.Black.copy(alpha = 0.10f)
            )
        }
    }
}

/** Параметры стекла для текущей темы. */
@Immutable
data class GlassStyle(val dark: Boolean, val glows: List<Color>)

/** Включено ли стекло. */
val LocalGlass = staticCompositionLocalOf { false }

/** Цвета свечений и яркость для текущей темы. */
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle(dark = true, glows = emptyList()) }

/** Переключение стекла из настроек. */
val LocalGlassSetter = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Контейнер «выделенной» карточки (главная цифра экрана). В стекле — чуть более
 * плотная панель, без стекла — привычный surfaceContainerHigh.
 */
@Composable
fun emphasisContainer(): Color {
    if (!LocalGlass.current) return MaterialTheme.colorScheme.surfaceContainerHigh
    return if (LocalGlassStyle.current.dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.75f)
}

/**
 * Стеклянная карточка: диагональный блик внутри и светлая кромка, которая ярче
 * у верхнего левого угла и гаснет к правому краю — как свет на ребре стекла.
 * Ставится последним в цепочке модификаторов карточки (после отступов), чтобы
 * рисовать ровно по её границе. Без стекла модификатор ничего не делает.
 *
 * @param shape форма карточки; по умолчанию — форма Card (`shapes.medium`).
 */
fun Modifier.glass(shape: Shape? = null): Modifier = composed {
    if (!LocalGlass.current) return@composed this
    val style = LocalGlassStyle.current
    val outlineShape = shape ?: MaterialTheme.shapes.medium
    val glow = style.glows.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val edgeTop: Color
    val edgeMid: Color
    val edgeBottom: Color
    val sheenTop: Color
    if (style.dark) {
        edgeTop = lerp(Color.White, glow, 0.25f).copy(alpha = 0.55f)
        edgeMid = Color.White.copy(alpha = 0.10f)
        edgeBottom = Color.White.copy(alpha = 0.22f)
        sheenTop = Color.White.copy(alpha = 0.09f)
    } else {
        edgeTop = Color.White.copy(alpha = 0.95f)
        edgeMid = Color.White.copy(alpha = 0.45f)
        edgeBottom = Color.White.copy(alpha = 0.75f)
        sheenTop = Color.White.copy(alpha = 0.35f)
    }
    this.drawWithCache {
        val outline = outlineShape.createOutline(size, layoutDirection, this)
        val strokeWidth = 1.dp.toPx()
        val diagonalEnd = Offset(size.width, size.height)
        val sheen = Brush.linearGradient(
            colorStops = arrayOf(
                0f to sheenTop,
                0.45f to sheenTop.copy(alpha = sheenTop.alpha * 0.25f),
                1f to Color.Transparent
            ),
            start = Offset.Zero,
            end = diagonalEnd
        )
        val edge = Brush.linearGradient(
            colorStops = arrayOf(0f to edgeTop, 0.55f to edgeMid, 1f to edgeBottom),
            start = Offset.Zero,
            end = diagonalEnd
        )
        onDrawWithContent {
            drawOutline(outline, sheen)
            drawContent()
            drawOutline(outline, edge, style = Stroke(width = strokeWidth))
        }
    }
}

/**
 * Фон под стеклом: базовый градиент (светлее слева, почти чёрный справа) и
 * крупные размытые свечения цветов темы. Всё статично — без анимаций, чтобы не
 * тратить батарею.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val style = LocalGlassStyle.current
    val base = scheme.background
    val glows = style.glows.ifEmpty { listOf(scheme.primary, scheme.primary, scheme.primary) }
    val strength = if (style.dark) 0.62f else 0.30f
    val baseBrush = if (style.dark) {
        Brush.linearGradient(
            listOf(lerp(base, glows[0], 0.10f), base, lerp(base, Color.Black, 0.45f))
        )
    } else {
        Brush.linearGradient(listOf(lerp(base, glows[0], 0.06f), base))
    }
    Box(modifier = modifier.fillMaxSize().background(baseBrush)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r = maxOf(w, h)
            // (центр, радиус, цвет, сила) — раскладка как на макете: главное
            // свечение слева посередине, второе ниже, слабые — сверху и справа.
            val spots = listOf(
                Spot(Offset(w * 0.12f, h * 0.36f), r * 0.48f, glows[0], 1.0f),
                Spot(Offset(w * 0.22f, h * 0.70f), r * 0.42f, glows[1 % glows.size], 0.85f),
                Spot(Offset(w * 0.62f, h * 0.06f), r * 0.30f, glows[0], 0.45f),
                Spot(Offset(w * 1.02f, h * 0.88f), r * 0.38f, glows[2 % glows.size], 0.35f)
            )
            spots.forEach { spot ->
                val a = strength * spot.power
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to spot.color.copy(alpha = a),
                            0.35f to spot.color.copy(alpha = a * 0.55f),
                            0.7f to spot.color.copy(alpha = a * 0.15f),
                            1f to spot.color.copy(alpha = 0f)
                        ),
                        center = spot.center,
                        radius = spot.radius
                    ),
                    radius = spot.radius,
                    center = spot.center
                )
            }
        }
        content()
    }
}

private class Spot(val center: Offset, val radius: Float, val color: Color, val power: Float)

/** Верхняя светлая кромка стеклянной нижней панели. */
fun Modifier.glassBar(dark: Boolean): Modifier = this.drawWithCache {
    val line = 1.dp.toPx()
    val edge = Brush.horizontalGradient(
        if (dark) {
            listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.18f))
        } else {
            listOf(Color.White, Color.White.copy(alpha = 0.6f), Color.White)
        }
    )
    onDrawWithContent {
        drawContent()
        drawRect(edge, size = androidx.compose.ui.geometry.Size(size.width, line))
    }
}
