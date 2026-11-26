package ru.mesh.foss.ui

import ru.mesh.foss.core.*

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/**
 * «Жидкое стекло» ([Design.LIQUID]) в духе iOS 26 Liquid Glass.
 *
 * Материал ([Modifier.liquidGlass]) собран из тех же слоёв, что и у Apple:
 *  - прозрачная заливка: стекло почти не окрашено, сквозь него виден цветной фон;
 *  - блик вдоль верхней части и «преломление» у кромки — внутреннее свечение,
 *    из-за которого края пластины выглядят толще и ярче середины, как у линзы;
 *  - зеркальная кромка: яркая сверху слева и снова вспыхивающая снизу справа;
 *  - мягкая тень только снаружи — сквозь прозрачное стекло она не просвечивает;
 *  - свет от касания, перетекающий между соседними пластинами.
 *
 * Хром как в iOS: крупный заголовок без плашки, действия в стеклянной капсуле
 * ([LiquidTopBar]) и плавающая капсула вкладок с «линзой», которая переезжает под
 * выбранную вкладку ([LiquidTabBar]).
 */
object Liquid {
    /** Радиус панели вкладок: заметно скруглённые углы, но не капсула. */
    val BarRadius = 28.dp

    /** Отступ линзы от края панели. */
    val BarInset = 4.dp

    /** Радиус линзы: концентричен панели (радиус панели минус отступ). */
    val LensRadius = BarRadius - BarInset

    /** Панель вкладок — плавающая капсула iOS 26 (Kyant0/Shapes). */
    val BarShape: Shape = Capsule()

    /** Линза под выбранной вкладкой — капсула с G2-скруглением. */
    val LensShape: Shape = Capsule()

    /** Стеклянные кнопки: действия в шапке, «Питание». */
    val ButtonShape: Shape = Capsule()

    fun isDark(background: Color) = background.luminance() < 0.45f
}

/**
 * Чистые правила выбора движка Kyant0/AndroidLiquidGlass по версии Android и флагам
 * (тестируются на JVM без эмулятора).
 */
internal object LiquidGlassEngine {
    const val MIN_BACKDROP_SDK = 31 // Android 12 (RenderEffect blur)
    const val MIN_LENS_SDK = 33     // Android 13 (AGSL RuntimeShader lens)

    fun supportsBackdrop(sdkInt: Int): Boolean = sdkInt >= MIN_BACKDROP_SDK

    fun supportsLens(sdkInt: Int): Boolean = sdkInt >= MIN_LENS_SDK

    fun useKyant(sdkInt: Int, previewEnabled: Boolean, hasBackdrop: Boolean): Boolean =
        supportsBackdrop(sdkInt) && !previewEnabled && hasBackdrop

    fun isAchromatic(color: Color): Boolean {
        val maxC = maxOf(color.red, color.green, color.blue)
        val minC = minOf(color.red, color.green, color.blue)
        return (maxC - minC) < 0.14f
    }

    /**
     * Для нейтральных тем вроде `midnight` и `graphite` (где primary — серый)
     * добавляем мягкие сапфирово-индиго акценты в подложку, чтобы у преломления
     * и хроматической дисперсии был живой свет и цвет.
     */
    fun backdropGlows(primary: Color, tertiary: Color): Triple<Color, Color, Color> {
        if (!isAchromatic(primary)) {
            return Triple(primary, lerp(primary, tertiary, 0.5f), tertiary)
        }
        val sapphire = Color(0xFF4F8CFF)
        val indigo = Color(0xFF7C6BFF)
        val cyan = Color(0xFF22D3EE)
        return Triple(sapphire, indigo, lerp(tertiary, cyan, 0.55f))
    }
}

private class GlassLight {
    var touch by mutableStateOf<Offset?>(null)
}

private val LocalGlassLight = staticCompositionLocalOf<GlassLight?> { null }

/**
 * Базовый слой подложки из Kyant0/AndroidLiquidGlass (`io.github.kyant0:backdrop`) для
 * карточек внутри скроллируемого контента (преломляет фон без рекурсии).
 */
internal val LocalKyantBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Слой прокручиваемого контента экрана для плавающих панелей (таб-бар, шапка, FAB).
 */
internal val LocalKyantContentBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Объединённый фон + живой контент экрана (`rememberCombinedBackdrop`), чтобы верхняя капсула,
 * нижний таб-бар, скользящая линза и FAB по-настоящему преломляли карточки при прокрутке.
 */
internal val LocalKyantChromeBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Показывает, что контент рендерится в отдельном окне (`ModalBottomSheet` / `AlertDialog`). */
internal val LocalInModalWindow = staticCompositionLocalOf { false }

/** Оборачивает содержимое диалога/шторки, чтобы кнопки и тумблеры внутри использовали локальную canvas-подложку. */
@Composable
fun LiquidModalScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInModalWindow provides true, content = content)
}

@Composable
internal fun rememberLiquidModalBackdrop(): Backdrop {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val (glowA, _, glowC) = LiquidGlassEngine.backdropGlows(scheme.primary, scheme.tertiary)
    val base = if (dark) lerp(scheme.surface, Color.Black, 0.24f) else scheme.surface
    return rememberCanvasBackdrop {
        drawRect(base)
        drawRect(
            Brush.linearGradient(
                listOf(
                    glowA.copy(alpha = if (dark) 0.22f else 0.14f),
                    Color.Transparent,
                    glowC.copy(alpha = if (dark) 0.18f else 0.12f)
                )
            )
        )
    }
}

@Composable
internal fun rememberResolvedLiquidBackdrop(explicit: Backdrop? = null): Backdrop {
    if (explicit != null) return explicit
    val windowBackdrop = if (!LocalInModalWindow.current) LocalKyantBackdrop.current else null
    return windowBackdrop ?: rememberLiquidModalBackdrop()
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/** Нижний отступ под плавающий таб-бар, чтобы списки прокручивались сквозь стекло. */
val LocalLiquidBottomPadding = staticCompositionLocalOf { 0.dp }

private class LiquidBackdropSource {
    var origin by mutableStateOf(Offset.Zero)
    var image by mutableStateOf<ImageBitmap?>(null)
}

private const val BackdropScale = 2

private val LocalLiquidBackdrop = staticCompositionLocalOf<LiquidBackdropSource?> { null }

/**
 * Подложка «жидкого стекла» на базе Kyant0/AndroidLiquidGlass:
 * захватывает фон в [bgBackdrop] (для карточек) и объединяет его со слоем
 * скроллируемого контента [contentBackdrop] в [chromeBackdrop] (для плавающих панелей и линзы).
 */
@Composable
fun LiquidBackdrop(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val lightSource = remember { GlassLight() }
    val base = if (dark) lerp(scheme.background, Color.Black, 0.42f) else scheme.background
    val (glowA, glowB, glowC) = LiquidGlassEngine.backdropGlows(scheme.primary, scheme.tertiary)
    val k = if (dark) 1f else 0.72f
    val source = remember { LiquidBackdropSource() }
    val bgBackdrop = rememberLayerBackdrop {
        drawRect(base)
        drawContent()
    }
    val contentBackdrop = rememberLayerBackdrop()
    val chromeBackdrop = rememberCombinedBackdrop(bgBackdrop, contentBackdrop)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(lightSource) {
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            lightSource.touch = event.changes.firstOrNull { it.pressed }?.position
                        }
                    }
                } finally {
                    lightSource.touch = null
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(bgBackdrop)
                .onGloballyPositioned { source.origin = it.positionInRoot() }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val r = size.maxDimension
                    fun blob(color: Color, alpha: Float, cx: Float, cy: Float, radius: Float) = Brush.radialGradient(
                        0f to color.copy(alpha = alpha * k),
                        0.42f to color.copy(alpha = alpha * k * 0.48f),
                        1f to Color.Transparent,
                        center = Offset(cx, cy),
                        radius = radius
                    )
                    val a = blob(glowA, 0.56f, w * 0.12f, h * 0.12f, r * 0.62f)
                    val b = blob(glowB, 0.42f, w * 0.96f, h * 0.44f, r * 0.54f)
                    val c = blob(glowC, 0.44f, w * 0.22f, h * 0.88f, r * 0.58f)
                    val d = blob(glowA, 0.28f, w * 0.82f, h * 0.92f, r * 0.44f)
                    // Чёткие световые ленты и дуги каустики (как в iOS 26 / Desktop):
                    // благодаря им преломление Kyant0 и дисперсия видны на каждой карточке, а не только в таб-баре.
                    val ribbonPrimary = Path().apply {
                        moveTo(-w * 0.12f, h * 0.28f)
                        cubicTo(w * 0.28f, h * 0.14f, w * 0.62f, h * 0.44f, w * 1.12f, h * 0.20f)
                    }
                    val ribbonSecondary = Path().apply {
                        moveTo(-w * 0.10f, h * 0.72f)
                        cubicTo(w * 0.34f, h * 0.54f, w * 0.68f, h * 0.84f, w * 1.14f, h * 0.60f)
                    }
                    val ribbonDiagonal = Path().apply {
                        moveTo(w * 0.18f, -h * 0.05f)
                        cubicTo(w * 0.42f, h * 0.32f, w * 0.58f, h * 0.64f, w * 0.88f, h * 1.06f)
                    }
                    val ribbonBrushA = Brush.linearGradient(
                        listOf(
                            glowA.copy(alpha = 0f),
                            glowA.copy(alpha = if (dark) 0.46f else 0.34f),
                            glowC.copy(alpha = if (dark) 0.38f else 0.28f),
                            glowC.copy(alpha = 0f)
                        ),
                        start = Offset(0f, h * 0.18f),
                        end = Offset(w, h * 0.38f)
                    )
                    val ribbonBrushB = Brush.linearGradient(
                        listOf(
                            glowB.copy(alpha = 0f),
                            glowB.copy(alpha = if (dark) 0.38f else 0.26f),
                            glowA.copy(alpha = if (dark) 0.30f else 0.22f),
                            glowA.copy(alpha = 0f)
                        ),
                        start = Offset(0f, h * 0.78f),
                        end = Offset(w, h * 0.55f)
                    )
                    val ribbonBrushC = Brush.linearGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (dark) 0.12f else 0.42f),
                            glowC.copy(alpha = if (dark) 0.24f else 0.20f),
                            Color.Transparent
                        ),
                        start = Offset(w * 0.2f, 0f),
                        end = Offset(w * 0.9f, h)
                    )
                    val ribbonStrokeWide = Stroke(width = (r * 0.055f).coerceAtLeast(18.dp.toPx()), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    val ribbonStrokeMid = Stroke(width = (r * 0.022f).coerceAtLeast(8.dp.toPx()), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    val ribbonStrokeThin = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    val iw = (w / BackdropScale).toInt().coerceAtLeast(1)
                    val ih = (h / BackdropScale).toInt().coerceAtLeast(1)
                    val image = ImageBitmap(iw, ih)
                    CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(image), Size(iw.toFloat(), ih.toFloat())) {
                        scale(1f / BackdropScale, 1f / BackdropScale, pivot = Offset.Zero) {
                            drawRect(base, size = Size(w, h))
                            drawRect(a, size = Size(w, h))
                            drawRect(b, size = Size(w, h))
                            drawRect(c, size = Size(w, h))
                            drawRect(d, size = Size(w, h))
                            drawPath(ribbonPrimary, ribbonBrushA, style = ribbonStrokeWide)
                            drawPath(ribbonSecondary, ribbonBrushB, style = ribbonStrokeWide)
                            drawPath(ribbonDiagonal, ribbonBrushC, style = ribbonStrokeMid)
                        }
                    }
                    source.image = image
                    onDrawBehind {
                        drawRect(base)
                        drawRect(a)
                        drawRect(b)
                        drawRect(c)
                        drawRect(d)
                        drawPath(ribbonPrimary, ribbonBrushA, style = ribbonStrokeWide)
                        drawPath(ribbonPrimary, Color.White.copy(alpha = if (dark) 0.14f else 0.35f), style = ribbonStrokeThin)
                        drawPath(ribbonSecondary, ribbonBrushB, style = ribbonStrokeWide)
                        drawPath(ribbonSecondary, Color.White.copy(alpha = if (dark) 0.11f else 0.28f), style = ribbonStrokeThin)
                        drawPath(ribbonDiagonal, ribbonBrushC, style = ribbonStrokeMid)
                    }
                }
        )
        CompositionLocalProvider(
            LocalKyantBackdrop provides bgBackdrop,
            LocalKyantContentBackdrop provides contentBackdrop,
            LocalKyantChromeBackdrop provides chromeBackdrop,
            LocalLiquidBackdrop provides source,
            LocalGlassLight provides lightSource,
            LocalContentColor provides scheme.onBackground,
            content = content
        )
    }
}

fun Modifier.outerShadow(shape: Shape, color: Color, blur: Dp, offsetY: Dp): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val paint = Paint().nativePaint.apply {
        isAntiAlias = true
        this.color = android.graphics.Color.BLACK
        setShadowLayer(blur.toPx(), 0f, offsetY.toPx(), color.toArgb())
    }
    onDrawBehind {
        clipPath(path, ClipOp.Difference) {
            drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
        }
    }
}

private val RefractionInk = Color(0xFF0A141E)

private val FringeStops: Array<Pair<Float, Color>> = run {
    val css = listOf(
        0f to Color(0x00FF5050),
        0.10f to Color(0x38FF785A),
        0.20f to Color(0x00FFDC78),
        0.45f to Color(0x005AAAFF),
        0.60f to Color(0x3D5A96FF),
        0.75f to Color(0x00A064FF),
        1f to Color(0x00FF5050)
    )
    val shift = 110f / 360f
    val moved = css.dropLast(1).map { (f, c) -> ((f + shift) % 1f) to c }.sortedBy { it.first }
    val last = moved.last()
    val first = moved.first()
    val gap = 1f - last.first + first.first
    val t = if (gap > 0f) (1f - last.first) / gap else 0f
    val seam = lerp(last.second, first.second, t)
    (listOf(0f to seam) + moved + listOf(1f to seam)).toTypedArray()
}

private fun DrawScope.ellipseGradient(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    vararg stops: Pair<Float, Color>,
    blendMode: BlendMode = BlendMode.SrcOver
) {
    if (rx <= 0f || ry <= 0f) return
    val c = Offset(cx, cy)
    withTransform({ scale(rx / ry, 1f, pivot = c) }) {
        drawRect(
            Brush.radialGradient(*stops, center = c, radius = ry),
            topLeft = Offset(cx - ry, cy - ry),
            size = Size(2 * ry, 2 * ry),
            blendMode = blendMode
        )
    }
}

private fun DrawScope.drawSpecular(strength: Float, spark: Boolean) {
    val w = size.width
    val h = size.height
    val rx = minOf(w * 0.40f, h * 1.1f)
    ellipseGradient(
        minOf(w * 0.34f, rx * 0.85f + h * 0.1f), h * 0.17f, rx, h * 0.22f,
        0f to Color.White.copy(alpha = 0.26f * strength),
        0.6f to Color.White.copy(alpha = 0.06f * strength),
        0.8f to Color.Transparent
    )
    if (spark) {
        ellipseGradient(
            w * 0.70f, h * 0.86f, minOf(w * 0.12f, h * 0.3f), h * 0.07f,
            0f to Color.White.copy(alpha = 0.42f * strength),
            0.8f to Color.Transparent
        )
    }
}

private fun DrawScope.fringeBrush(): Brush = Brush.sweepGradient(*FringeStops, center = center)

/**
 * Материал «жидкого стекла» в точности как в iOS 26 (на базе Kyant0/AndroidLiquidGlass).
 * На Android 13+ использует `drawBackdrop` с физическим AGSL-шейдером преломления,
 * 7-полосной хроматической дисперсией, vibrancy, направленным бликом [Highlight]
 * и мягкими внутренними/внешними тенями.
 */
/** Помечает скроллируемое содержимое экрана как источник преломления для плавающих панелей. */
@Composable
fun Modifier.liquidContentLayer(): Modifier {
    val contentBackdrop = LocalKyantContentBackdrop.current
    return if (LocalDesign.current == Design.LIQUID && contentBackdrop != null && !LiquidPreview.enabled) {
        this.layerBackdrop(contentBackdrop)
    } else {
        this
    }
}

/** Вариант [liquidGlass] для плавающих элементов (FAB, кнопки), преломляющий фон и контент под ними. */
@Composable
fun Modifier.liquidChromeGlass(
    shape: Shape = Liquid.ButtonShape,
    tint: Color = Color.Unspecified,
    elevation: Dp = 14.dp
): Modifier = liquidGlass(
    shape = shape,
    tint = tint,
    elevation = elevation,
    backdrop = LocalKyantChromeBackdrop.current ?: LocalKyantBackdrop.current
)

@Composable
fun Modifier.liquidGlass(
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = Color.Unspecified,
    elevation: Dp = 6.dp,
    refract: Boolean = true,
    backdrop: Backdrop? = null,
    blurRadius: Dp = Dp.Unspecified,
    refractionHeight: Dp = 20.dp,
    refractionAmount: Dp = 42.dp,
    surfaceAlpha: Float? = null
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val kyantBackdrop = backdrop ?: LocalKyantBackdrop.current
    val resolvedAlpha = surfaceAlpha ?: if (dark) 0.13f else 0.22f
    val surfaceTint = if (dark) Color(0xFF141926).copy(alpha = resolvedAlpha) else Color.White.copy(alpha = resolvedAlpha)
    val accentTint = if (tint != Color.Unspecified) tint else Color.Unspecified

    if (refract && LiquidGlassEngine.useKyant(Build.VERSION.SDK_INT, LiquidPreview.enabled, kyantBackdrop != null) && kyantBackdrop != null) {
        val resolvedBlur = when {
            blurRadius != Dp.Unspecified -> blurRadius
            dark -> 2.5.dp
            else -> 2.dp
        }
        return this.drawBackdrop(
            backdrop = kyantBackdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(resolvedBlur.toPx())
                if (LiquidGlassEngine.supportsLens(Build.VERSION.SDK_INT)) {
                    lens(
                        refractionHeight = refractionHeight.toPx(),
                        refractionAmount = refractionAmount.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                }
            },
            highlight = {
                Highlight.Default
            },
            innerShadow = {
                InnerShadow(
                    radius = 8.dp,
                    alpha = if (dark) 0.18f else 0.08f
                )
            },
            shadow = {
                if (elevation > 0.dp) {
                    Shadow(
                        radius = elevation * 1.4f,
                        color = Color.Black.copy(alpha = if (dark) 0.34f else 0.14f)
                    )
                } else {
                    null
                }
            },
            onDrawSurface = {
                drawRect(surfaceTint)
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.07f else 0.22f),
                            Color.Transparent
                        )
                    )
                )
                if (accentTint != Color.Unspecified) {
                    drawRect(accentTint.copy(alpha = if (dark) 0.24f else 0.20f))
                }
            }
        )
    }

    val clear = if (dark) lerp(scheme.surface, Color.White, 0.10f).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.55f)
    val tintFill = if (tint != Color.Unspecified) tint.copy(alpha = tint.alpha * 0.55f) else Color.Transparent
    val rimTop = Color.White.copy(alpha = if (dark) 0.50f else 0.95f)
    val rimMid = Color.White.copy(alpha = if (dark) 0.10f else 0.45f)
    val rimBottom = Color.White.copy(alpha = if (dark) 0.34f else 0.85f)
    val shadow = Color.Black.copy(alpha = if (dark) 0.40f else 0.10f)
    val specular = if (dark) 1f else 1.6f
    val lightSource = LocalGlassLight.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val legacyBackdrop = LocalLiquidBackdrop.current
    val refractive = refract && legacyBackdrop != null && LiquidShader.available
    val glassTint = tintFill.compositeOver(clear)
    val base = if (elevation > 0.dp) outerShadow(shape, shadow, elevation * 1.6f, elevation * 0.45f) else this
    return base
        .onGloballyPositioned { origin = it.positionInRoot() }
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val rim = Brush.linearGradient(
                0f to rimTop,
                0.32f to rimMid,
                0.68f to rimMid,
                1f to rimBottom,
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val glintRadius = 120.dp.toPx()
            val glint = Brush.radialGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.14f else 0.30f), Color.Transparent),
                Offset.Zero,
                glintRadius
            )
            val refraction = (if (dark) listOf(20.dp to 0.025f, 12.dp to 0.04f, 6.dp to 0.05f)
                else listOf(14.dp to 0.010f, 8.dp to 0.016f, 4.dp to 0.022f)).map { (w, a) ->
                Stroke(width = w.toPx()) to RefractionInk.copy(alpha = a)
            }
            val rimStroke = Stroke(width = 2.dp.toPx())
            val fringeStroke = Stroke(width = 5.dp.toPx())
            val fringe = Brush.sweepGradient(*FringeStops, center = Offset(size.width / 2, size.height / 2))
            val radiusPx = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: (minOf(size.width, size.height) / 2)
            onDrawWithContent {
                val image = legacyBackdrop?.image
                if (refractive && image != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val under = android.graphics.BitmapShader(
                        image.asAndroidBitmap(),
                        android.graphics.Shader.TileMode.CLAMP,
                        android.graphics.Shader.TileMode.CLAMP
                    ).apply {
                        setLocalMatrix(android.graphics.Matrix().apply {
                            setScale(BackdropScale.toFloat(), BackdropScale.toFloat())
                            postTranslate(legacyBackdrop.origin.x - origin.x, legacyBackdrop.origin.y - origin.y)
                        })
                    }
                    drawRect(LiquidShader.brush(under, Offset.Zero, size, radiusPx, density, glassTint, PanelParams))
                } else {
                    drawOutline(outline, clear)
                    if (tintFill.alpha > 0f) drawOutline(outline, tintFill)
                    refraction.forEach { (stroke, color) -> drawOutline(outline, color, style = stroke) }
                    drawSpecular(specular, spark = false)
                }
                lightSource?.touch?.let { touch ->
                    val c = touch - origin
                    drawContext.transform.translate(c.x, c.y)
                    drawCircle(glint, glintRadius, Offset.Zero)
                    drawContext.transform.translate(-c.x, -c.y)
                }
                drawContent()
                if (!refractive) {
                    drawOutline(outline, fringe, style = fringeStroke, blendMode = BlendMode.Screen)
                    drawOutline(outline, rim, style = rimStroke)
                }
            }
        }
}

private val PanelParams = LiquidShader.Params(thickness = 14f, offsetDp = 10f, dispersion = 5f)
private val LensParams = LiquidShader.Params(roundness = 2f, thickness = 18f, offsetDp = 14f, dispersion = 5f, fresnelFactor = 0.25f)

/**
 * Настоящее Kyant0/AndroidLiquidGlass для диалогов и шторки «Ещё» (по мотивам `DialogContent.kt`
 * из каталога Kyant0): `colorControls`, адаптивный `blur`, `lens(..., depthEffect = true)`,
 * мягкий `Highlight.Plain` и внешняя/внутренняя тени.
 */
@Composable
internal fun Modifier.liquidDialogGlass(shape: Shape = MaterialTheme.shapes.extraLarge): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val (glowA, _, glowC) = LiquidGlassEngine.backdropGlows(scheme.primary, scheme.tertiary)
    val surfaceColor = if (dark) {
        lerp(scheme.surface, Color.Black, 0.20f).copy(alpha = 0.88f)
    } else {
        Color(0xFFFAFAFA).copy(alpha = 0.90f)
    }
    if (!LiquidGlassEngine.supportsBackdrop(Build.VERSION.SDK_INT) || LiquidPreview.enabled) {
        return this.background(surfaceColor, shape).liquidGlass(shape, elevation = 20.dp, refract = false)
    }
    val canvasBackdrop = rememberCanvasBackdrop {
        drawRect(surfaceColor)
        drawRect(
            Brush.linearGradient(
                listOf(
                    glowA.copy(alpha = if (dark) 0.20f else 0.12f),
                    Color.Transparent,
                    glowC.copy(alpha = if (dark) 0.16f else 0.10f)
                )
            )
        )
    }
    return this.drawBackdrop(
        backdrop = canvasBackdrop,
        shape = { shape },
        effects = {
            colorControls(
                brightness = if (dark) 0f else 0.14f,
                contrast = 1f,
                saturation = 1.45f
            )
            blur(if (dark) 8f.dp.toPx() else 14f.dp.toPx())
            if (LiquidGlassEngine.supportsLens(Build.VERSION.SDK_INT)) {
                lens(
                    refractionHeight = 22f.dp.toPx(),
                    refractionAmount = 44f.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true
                )
            }
        },
        highlight = { if (dark) Highlight.Default else Highlight.Plain },
        innerShadow = {
            InnerShadow(
                radius = 10.dp,
                alpha = if (dark) 0.22f else 0.08f
            )
        },
        shadow = {
            Shadow(
                radius = 24.dp,
                color = Color.Black.copy(alpha = if (dark) 0.48f else 0.18f)
            )
        },
        onDrawSurface = {
            drawRect(surfaceColor.copy(alpha = if (dark) 0.68f else 0.58f))
            drawRect(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.07f else 0.24f),
                        Color.Transparent
                    )
                )
            )
        }
    )
}

/* -------------------------------------------------------------------------- */
/* Компоненты из каталога Kyant0/AndroidLiquidGlass                           */
/* (InteractiveHighlight, DampedDragAnimation, LiquidButton, LiquidToggle)    */
/* -------------------------------------------------------------------------- */

private suspend inline fun PointerInputScope.inspectDragGestures(
    crossinline onDragStart: (down: PointerInputChange) -> Unit = {},
    crossinline onDragEnd: (change: PointerInputChange) -> Unit = {},
    crossinline onDragCancel: () -> Unit = {},
    crossinline onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent = inspectDrag(
            pointerId = initialDown.id,
            onDrag = { onDrag(it, it.positionChange()) }
        )
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.inspectDrag(
    pointerId: PointerId,
    crossinline onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.firstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitInspectDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitInspectDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.firstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.firstOrNull { it.pressed } ?: return dragEvent
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}

/**
 * Интерактивный блик из `catalog/utils/InteractiveHighlight.kt` (Kyant0):
 * при касании/перетаскивании зажигает под пальцем AGSL-радиальное свечение и мягко
 * смещает/приподнимает стеклянную поверхность.
 */
internal class LiquidInteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            if (shader != null) {
                drawRect(Color.White.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)
                shader.apply {
                    val pos = position(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(alpha = 0.16f * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        pos.x.coerceIn(0f, size.width),
                        pos.y.coerceIn(0f, size.height)
                    )
                }
                drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(alpha = 0.22f * progress), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

/** Пружинная физика перетаскивания из `catalog/utils/DampedDragAnimation.kt` (Kyant0). */
internal class LiquidDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    val valueRange: ClosedRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val onDragStarted: LiquidDragAnimation.(position: Offset) -> Unit,
    private val onDragStopped: LiquidDragAnimation.() -> Unit,
    private val onDrag: LiquidDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { _, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
        val targetVelocity = velocityTracker.calculateVelocity().x / span
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

/**
 * Стеклянная кнопка из `catalog/components/LiquidButton.kt` (`Kyant0/AndroidLiquidGlass`):
 * капсула (или произвольная форма) с `drawBackdrop`, `vibrancy()`, `blur`, `lens`,
 * [Highlight.Default], тонированием через [BlendMode.Hue] и живым бликом под пальцем.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    shape: Shape = Liquid.ButtonShape,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    backdrop: Backdrop? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    contentColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val resolvedBackdrop = rememberResolvedLiquidBackdrop(backdrop)
    val kyantActive = LiquidGlassEngine.supportsBackdrop(Build.VERSION.SDK_INT) && !LiquidPreview.enabled
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { LiquidInteractiveHighlight(animationScope) }
    val resolvedContentColor = when {
        contentColor.isSpecified -> contentColor
        tint.isSpecified -> if (tint.luminance() > 0.62f) Color(0xFF0E1420) else Color.White
        else -> scheme.onSurface
    }
    val defaultSurface = if (surfaceColor.isSpecified) {
        surfaceColor
    } else if (!tint.isSpecified) {
        if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.34f)
    } else {
        Color.Unspecified
    }

    val backdropModifier = if (kyantActive) {
        Modifier.drawBackdrop(
            backdrop = resolvedBackdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(2f.dp.toPx())
                if (LiquidGlassEngine.supportsLens(Build.VERSION.SDK_INT)) {
                    lens(
                        refractionHeight = 12f.dp.toPx(),
                        refractionAmount = 24f.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                }
            },
            highlight = { Highlight.Default },
            innerShadow = {
                InnerShadow(
                    radius = 5.dp,
                    alpha = if (dark) 0.16f else 0.08f
                )
            },
            shadow = {
                Shadow(
                    radius = 10.dp,
                    color = Color.Black.copy(alpha = if (dark) 0.28f else 0.12f)
                )
            },
            layerBlock = if (enabled) {
                {
                    val w = size.width
                    val h = size.height.coerceAtLeast(1f)
                    val progress = interactiveHighlight.pressProgress
                    val scale = lerpFloat(1f, 1f + 3f.dp.toPx() / h, progress)
                    val maxOffset = size.minDimension.coerceAtLeast(1f)
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                    translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                    val maxDragScale = 3f.dp.toPx() / h
                    val offsetAngle = atan2(offset.y, offset.x)
                    val maxDim = size.maxDimension.coerceAtLeast(1f)
                    scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / maxDim) * (w / h).coerceAtMost(1f)
                    scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / maxDim) * (h / w).coerceAtMost(1f)
                }
            } else {
                null
            },
            onDrawSurface = {
                if (tint.isSpecified) {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = if (enabled) 0.72f else 0.38f))
                }
                if (defaultSurface.isSpecified) {
                    drawRect(defaultSurface)
                }
            }
        )
    } else {
        Modifier.liquidGlass(shape = shape, tint = tint, elevation = 6.dp)
    }

    CompositionLocalProvider(LocalContentColor provides resolvedContentColor.copy(alpha = if (enabled) 1f else 0.48f)) {
        Row(
            modifier = modifier
                .alpha(if (enabled) 1f else 0.62f)
                .then(backdropModifier)
                .then(
                    if (enabled) {
                        Modifier
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Button,
                                onClick = onClick
                            )
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier)
                    } else {
                        Modifier
                    }
                )
                .heightIn(min = height)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Круглая кнопка-орб из жидкого стекла (`CircleShape`). */
@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    size: Dp = 42.dp,
    content: @Composable () -> Unit
) {
    LiquidButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        height = size,
        shape = CircleShape,
        tint = tint,
        surfaceColor = surfaceColor,
        contentPadding = PaddingValues(0.dp)
    ) {
        content()
    }
}

/**
 * Переключатель из `catalog/components/LiquidToggle.kt` (`Kyant0/AndroidLiquidGlass`):
 * капсула дорожки + хрустальная подвижная капля-бегунок, которая при касании
 * становится прозрачной преломляющей линзой с дисперсией и упруго вытягивается от скорости.
 */
@Composable
fun LiquidToggle(
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop? = null
) {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val resolvedBackdrop = rememberResolvedLiquidBackdrop(backdrop)
    val kyantActive = LiquidGlassEngine.supportsBackdrop(Build.VERSION.SDK_INT) && !LiquidPreview.enabled
    val accentColor = scheme.primary
    val trackColor = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.22f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope) {
        LiquidDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.42f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selected) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) (fraction + delta).coerceIn(0f, 1f) else (fraction - delta).coerceIn(0f, 1f)
            }
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { dampedDragAnimation.updateValue(it) }
    }
    LaunchedEffect(selected) {
        val target = if (selected) 1f else 0f
        if (target != fraction) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val thumbBackdrop = rememberCombinedBackdrop(
        resolvedBackdrop,
        rememberBackdrop(trackBackdrop) { drawBackdrop ->
            val progress = dampedDragAnimation.pressProgress
            val sx = lerpFloat(2f / 3f, 0.78f, progress)
            val sy = lerpFloat(0f, 0.78f, progress)
            scale(sx, sy) { drawBackdrop() }
        }
    )

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .then(if (kyantActive) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .clip(Capsule())
                .drawBehind {
                    drawRect(lerp(trackColor, accentColor, dampedDragAnimation.value))
                }
                .size(64f.dp, 28f.dp)
        )

        val thumbBaseModifier = Modifier
            .graphicsLayer {
                val f = dampedDragAnimation.value
                val pad = 2f.dp.toPx()
                translationX = if (isLtr) lerpFloat(pad, pad + dragWidth, f) else lerpFloat(-pad, -(pad + dragWidth), f)
            }
            .semantics { role = Role.Switch }
            .then(if (enabled) dampedDragAnimation.modifier else Modifier)

        if (kyantActive) {
            Box(
                thumbBaseModifier
                    .drawBackdrop(
                        backdrop = thumbBackdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            blur(8f.dp.toPx() * (1f - progress))
                            if (LiquidGlassEngine.supportsLens(Build.VERSION.SDK_INT)) {
                                lens(
                                    refractionHeight = 5f.dp.toPx() * progress,
                                    refractionAmount = 10f.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = true
                                )
                            }
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 4f.dp,
                                color = Color.Black.copy(alpha = 0.08f)
                            )
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 4f.dp * progress,
                                alpha = progress
                            )
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 50f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(Color.White.copy(alpha = (1f - progress).coerceAtLeast(0.18f)))
                        }
                    )
                    .size(40f.dp, 24f.dp)
            )
        } else {
            Box(
                thumbBaseModifier
                    .size(40f.dp, 24f.dp)
                    .clip(Capsule())
                    .background(Color.White)
            )
        }
    }
}

/**
 * Сегментированный переключатель в стиле iOS 26 Liquid Glass (`Kyant0/AndroidLiquidGlass`):
 * стеклянная капсула с переезжающей линзой под выбранным пунктом.
 */
@Composable
fun LiquidSegmentedTabs(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val backdrop = LocalKyantBackdrop.current
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val kyantActive = LiquidGlassEngine.useKyant(Build.VERSION.SDK_INT, LiquidPreview.enabled, backdrop != null) && backdrop != null
    val kyantLensActive = kyantActive && LiquidGlassEngine.supportsLens(Build.VERSION.SDK_INT)
    val rowBackdrop = rememberLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(*listOfNotNull(backdrop, rowBackdrop).toTypedArray())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .liquidGlass(
                shape = Liquid.BarShape,
                elevation = 8.dp,
                blurRadius = if (dark) 3.dp else 2.dp,
                refractionHeight = 12.dp,
                refractionAmount = 24.dp,
                surfaceAlpha = if (dark) 0.14f else 0.24f
            )
            .padding(3.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val count = options.size.coerceAtLeast(1)
            val itemWidth = maxWidth / count
            val localDensity = androidx.compose.ui.platform.LocalDensity.current
            val itemWidthPx = with(localDensity) { itemWidth.toPx() }
            val lensOffsetPx by animateFloatAsState(
                targetValue = selectedIndex * itemWidthPx,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
                label = "liquidSegmentOffset"
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (kyantLensActive) Modifier.layerBackdrop(rowBackdrop) else Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { (key, label) ->
                    val on = key == selected
                    val textColor by animateColorAsState(
                        if (on) scheme.primary else scheme.onSurface,
                        tween(180),
                        label = "liquidSegmentText"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(Liquid.LensShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(key) }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (options.isNotEmpty()) {
                val pillModifier = Modifier
                    .offset { IntOffset(lensOffsetPx.roundToInt(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                if (kyantLensActive) {
                    Box(
                        pillModifier.drawBackdrop(
                            backdrop = lensBackdrop,
                            shape = { Liquid.LensShape },
                            effects = {
                                vibrancy()
                                lens(
                                    refractionHeight = 10f.dp.toPx(),
                                    refractionAmount = 20f.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Default },
                            shadow = {
                                Shadow(
                                    radius = 6.dp,
                                    color = Color.Black.copy(alpha = if (dark) 0.24f else 0.10f)
                                )
                            },
                            innerShadow = {
                                InnerShadow(
                                    radius = 5.dp,
                                    alpha = if (dark) 0.18f else 0.10f
                                )
                            },
                            onDrawSurface = {
                                drawRect(if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.34f))
                            }
                        )
                    )
                } else {
                    Box(
                        pillModifier
                            .clip(Liquid.LensShape)
                            .background(if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.55f))
                    )
                }
            }
        }
    }
}

/**
 * Шапка в стиле iOS 26 Liquid Glass: плавающая стеклянная капсула с крупным заголовком
 * и действиями, использующая живой [chromeBackdrop] из Kyant0/AndroidLiquidGlass.
 */
@Composable
fun LiquidTopBar(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    navigationIcon: (@Composable () -> Unit)? = null
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val dark = Liquid.isDark(MaterialTheme.colorScheme.background)
    val chromeBackdrop = LocalKyantChromeBackdrop.current ?: LocalKyantBackdrop.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .heightIn(min = 56.dp)
            .liquidGlass(
                shape = Liquid.BarShape,
                elevation = 12.dp,
                backdrop = chromeBackdrop,
                blurRadius = if (dark) 4.dp else 3.dp,
                refractionHeight = 16.dp,
                refractionAmount = 32.dp,
                surfaceAlpha = if (dark) 0.16f else 0.28f
            )
            .padding(start = if (navigationIcon != null) 6.dp else 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (navigationIcon != null) {
            CompositionLocalProvider(LocalContentColor provides ink) {
                navigationIcon()
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = ink
        )
        Spacer(Modifier.width(8.dp))
        CompositionLocalProvider(LocalContentColor provides ink) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/** Пункт нижней панели. */
data class BarNavItem(val key: String, val label: String, val icon: androidx.compose.ui.graphics.painter.Painter)

/**
 * Плавающая панель вкладок (квадрат со скруглёнными углами) с оптической
 * «линзой» под выбранной вкладкой.
 *
 * Линза — по мотивам «Glass Lens» (DesignBookmark): внутри неё три копии иконок,
 * каждая увеличена чуть сильнее (1×, 1.05×, 1.12×) и видна чуть ближе к ободку,
 * так что изображение выгибается наружу, как в выпуклом стекле. Поверх — тёмная
 * полоса преломления, тонкий яркий ободок, блики и цветная кайма. Линза плывёт к
 * вкладке на мягкой пружине, как капля воды, а при перетаскивании следует за пальцем.
 * Выбор вкладки — по отпусканию.
 */
@Composable
fun LiquidTabBar(items: List<BarNavItem>, selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val index = items.indexOfFirst { it.key == selected }
    val sources = remember(items.size) { List(items.size) { MutableInteractionSource() } }
    val icons = rememberGraphicsLayer()
    val chromeBackdrop = LocalKyantChromeBackdrop.current ?: LocalKyantBackdrop.current
    val kyantBarActive = LiquidGlassEngine.useKyant(Build.VERSION.SDK_INT, LiquidPreview.enabled, chromeBackdrop != null)
    val kyantLensActive = kyantBarActive && LiquidGlassEngine.supportsLens(Build.VERSION.SDK_INT)
    val tabsBackdrop = rememberLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(*listOfNotNull(chromeBackdrop, tabsBackdrop).toTypedArray())
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(66.dp)
            .liquidGlass(
                shape = Liquid.BarShape,
                elevation = 16.dp,
                backdrop = chromeBackdrop,
                blurRadius = if (dark) 6.dp else 4.dp,
                refractionHeight = 18.dp,
                refractionAmount = 36.dp,
                surfaceAlpha = if (dark) 0.18f else 0.30f
            )
            .padding(Liquid.BarInset)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val count = items.size.coerceAtLeast(1)
            val itemWidth = maxWidth / count
            val localDensity = androidx.compose.ui.platform.LocalDensity.current
            val itemWidthPx = with(localDensity) { itemWidth.toPx() }
            val selectedIndex = index.coerceAtLeast(0)
            // Линза живёт в пикселях: так её можно вести пальцем, не дожидаясь
            // анимации к соседней вкладке и не «дёргая» её на каждом кадре.
            var lensX by remember(itemWidthPx) {
                mutableFloatStateOf(selectedIndex * itemWidthPx)
            }
            var dragging by remember { mutableStateOf(false) }
            // Позиция, к которой линза должна приехать после нажатия/отпускания.
            // Не берём pressed-состояние у clickable: при перехвате drag оно может
            // остаться «зажатым», и линза застревает на месте.
            var activeIndex by remember { mutableStateOf<Int?>(null) }
            val lensTarget = activeIndex ?: selectedIndex
            // Внешняя навигация (кнопка, FAB) меняет selected — отпускаем ручной target.
            LaunchedEffect(selectedIndex) { activeIndex = null }
            LaunchedEffect(lensTarget, itemWidthPx, dragging) {
                if (!dragging) {
                    // Пружина «капли» из Glass Lens: жёсткость 180, затухание 17.
                    animate(
                        initialValue = lensX,
                        targetValue = lensTarget * itemWidthPx,
                        animationSpec = spring(dampingRatio = 0.634f, stiffness = 180f)
                    ) { value, _ -> lensX = value }
                }
            }
            // Линза не увеличивает иконки: двигается только стекло, как в iOS.
            val zoom = 1f
            val bend = 0.3f
            val lensAlpha by animateFloatAsState(if (items.isEmpty()) 0f else 1f, tween(180), label = "liquidLensAlpha")
            val showLens = items.isNotEmpty()
            // Android 13+: приоритет — нативная линза Kyant0/AndroidLiquidGlass поверх tabsBackdrop.
            val shaderLens = !kyantLensActive && LiquidShader.effectAvailable
            val lensTint = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
            var rowOrigin by remember { mutableStateOf(Offset.Zero) }
            Row(
                Modifier
                    .fillMaxSize()
                    .then(if (kyantLensActive) Modifier.layerBackdrop(tabsBackdrop) else Modifier)
                    .onGloballyPositioned { rowOrigin = it.positionInWindow() }
                    .pointerInput(items.size, itemWidthPx) {
                        if (items.isEmpty()) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val pointerId = down.id
                            var dragged = false
                            var grabOffset = 0f
                            val maxX = (size.width - itemWidthPx).coerceAtLeast(0f)
                            val downIndex = (down.position.x / itemWidthPx)
                                .toInt()
                                .coerceIn(0, items.lastIndex)
                            activeIndex = downIndex
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!dragged &&
                                        kotlin.math.abs(change.position.x - startX) > viewConfiguration.touchSlop
                                    ) {
                                        dragged = true
                                        dragging = true
                                        // Смещение считаем от центра вкладки, на которой
                                        // начался жест: линза не прыгает и не отстаёт.
                                        grabOffset = downIndex * itemWidthPx - startX
                                    }
                                    if (dragged) {
                                        // Линза едет за пальцем с сохранённым смещением;
                                        // у центров вкладок слегка «прилипает» и замедляется.
                                        val raw = (change.position.x + grabOffset).coerceIn(0f, maxX)
                                        lensX = magnetizeLens(raw, itemWidthPx)
                                        // Up не потребляем: clickable должен снять press-состояние.
                                        if (change.pressed) change.consume()
                                    }
                                    if (!change.pressed) {
                                        if (dragged) {
                                            val chosen = (lensX / itemWidthPx)
                                                .roundToInt()
                                                .coerceIn(0, items.lastIndex)
                                            activeIndex = chosen
                                            onSelect(items[chosen].key)
                                        }
                                        break
                                    }
                                }
                            } finally {
                                // awaitEachGesture живёт между жестами, поэтому сбрасываем
                                // флаг здесь, иначе линза «застревает» после первого drag.
                                dragging = false
                            }
                        }
                    }
                    .graphicsLayer {
                        if (LiquidPreview.enabled) {
                            LiquidPreview.lens = if (!showLens) null else LensSnapshot(
                                origin = rowOrigin + Offset(lensX, 0f),
                                size = Size(itemWidthPx, size.height),
                                radius = Liquid.LensRadius.toPx(),
                                density = density,
                                tint = lensTint.copy(alpha = lensTint.alpha * lensAlpha),
                                params = LensParams.copy(offsetDp = LensParams.offsetDp * bend),
                                zoom = zoom
                            )
                        }
                        if (shaderLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            renderEffect = if (!showLens) null else LiquidShader.effect(
                                origin = Offset(lensX, 0f),
                                size = Size(itemWidthPx, size.height),
                                radius = Liquid.LensRadius.toPx(),
                                density = density,
                                tint = lensTint.copy(alpha = lensTint.alpha * lensAlpha),
                                params = LensParams.copy(offsetDp = LensParams.offsetDp * bend),
                                zoom = zoom
                            )
                        }
                    }
                    .drawWithContent {
                        if (kyantLensActive || shaderLens || LiquidPreview.enabled) {
                            drawContent()
                            return@drawWithContent
                        }
                        // Иконки записываются в слой: его же рисует линза, увеличенным.
                        icons.record { this@drawWithContent.drawContent() }
                        if (!showLens) {
                            drawLayer(icons)
                        } else {
                            val lens = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = lensX,
                                        top = 0f,
                                        right = lensX + itemWidthPx,
                                        bottom = size.height,
                                        cornerRadius = CornerRadius(Liquid.LensRadius.toPx())
                                    )
                                )
                            }
                            // Под линзой иконки видны только через неё.
                            clipPath(lens, ClipOp.Difference) { drawLayer(icons) }
                        }
                    },
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEachIndexed { i, item ->
                    val on = item.key == selected
                    val ink by animateColorAsState(
                        if (on) scheme.primary else scheme.onBackground,
                        tween(200),
                        label = "liquidInk"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // Без clip: иначе скруглённая «линза» срезает края
                            // длинных подписей («Расписание») на узких экранах.
                            .clickable(interactionSource = sources[i], indication = null) { onSelect(item.key) }
                            .padding(horizontal = 1.dp)
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = ink, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.label,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = ink
                        )
                    }
                }
            }
            if (kyantLensActive && showLens) {
                Box(
                    Modifier
                        .offset { IntOffset(lensX.roundToInt(), 0) }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = lensAlpha }
                        .drawBackdrop(
                            backdrop = lensBackdrop,
                            shape = { Liquid.LensShape },
                            effects = {
                                vibrancy()
                                lens(
                                    refractionHeight = 12f.dp.toPx(),
                                    refractionAmount = 24f.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = true
                                )
                            },
                            highlight = { Highlight.Default },
                            shadow = {
                                Shadow(
                                    radius = 6.dp,
                                    color = Color.Black.copy(alpha = if (dark) 0.24f else 0.10f)
                                )
                            },
                            innerShadow = {
                                InnerShadow(
                                    radius = 6.dp,
                                    alpha = if (dark) 0.22f else 0.12f
                                )
                            },
                            onDrawSurface = {
                                drawRect(if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.34f))
                            }
                        )
                )
            } else if (!shaderLens && !LiquidPreview.enabled) {
                Box(
                    Modifier
                        .offset { IntOffset(lensX.roundToInt(), 0) }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = lensAlpha }
                        .clip(Liquid.LensShape)
                        .drawBehind { drawLens(icons, lensX, zoom, bend, dark, Liquid.LensShape) }
                )
            }
        }
    }
}

/**
 * Лёгкое магнитное притяжение к центрам вкладок. В середине пункта линза
 * движется примерно на 30% медленнее пальца, между пунктами — один в один.
 * Так перетаскивание остаётся плавным, но ощущается «щелчками».
 */
private fun magnetizeLens(raw: Float, itemWidthPx: Float): Float {
    if (itemWidthPx <= 0f) return raw
    val nearest = (raw / itemWidthPx).roundToInt() * itemWidthPx
    val offset = (raw - nearest) / itemWidthPx
    val proximity = (1f - kotlin.math.abs(offset) * 2f).coerceIn(0f, 1f)
    // smoothstep: мягкий вход/выход из зоны притяжения.
    val pull = proximity * proximity * (3f - 2f * proximity) * 0.32f
    return raw + (nearest - raw) * pull
}

/**
 * Только для превью в тестах: в Robolectric нет RenderEffect с шейдером, поэтому
 * линза не рисуется, а её текущие параметры (в координатах окна) сохраняются сюда —
 * тест накладывает тот же шейдер на готовый кадр. В приложении выключено.
 */
internal object LiquidPreview {
    var enabled = false
    var lens: LensSnapshot? = null
}

internal data class LensSnapshot(
    val origin: Offset,
    val size: Size,
    val radius: Float,
    val density: Float,
    val tint: Color,
    val params: LiquidShader.Params,
    val zoom: Float
)

/** Насколько сильнее увеличена каждая копия к ободку: середина, внешнее кольцо, ободок. */
private val LensBend = floatArrayOf(1f, 1.05f, 1.12f)

/**
 * Маска «по расстоянию до края» для прямоугольной линзы: прозрачна у края
 * (ближе [from] px), непрозрачна внутри (дальше [to] px), между ними — плавный
 * переход. Составлена как произведение горизонтального и вертикального профилей
 * и накладывается на текущий слой режимом [mode] (DstIn — оставить внутреннюю
 * часть, DstOut — оставить кромку).
 */
private fun DrawScope.edgeMask(from: Float, to: Float, mode: BlendMode) {
    val w = size.width
    val h = size.height
    fun profile(len: Float): Array<Pair<Float, Color>> {
        val a = (from / len).coerceIn(0f, 0.5f)
        val b = (to / len).coerceIn(a, 0.5f)
        return arrayOf(
            0f to Color.Transparent, a to Color.Transparent, b to Color.Black,
            1f - b to Color.Black, 1f - a to Color.Transparent, 1f to Color.Transparent
        )
    }
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint().apply { blendMode = mode }) }
    drawRect(Brush.horizontalGradient(*profile(w)))
    drawRect(Brush.verticalGradient(*profile(h)), blendMode = BlendMode.DstIn)
    drawIntoCanvas { it.restore() }
}

/**
 * Рисует линзу формы [shape] размером с текущую область: заливку, три
 * увеличенные копии [content] (слой строки иконок, левый край линзы в нём —
 * [left]) и оптику стекла поверх. Кольца изгиба идут вдоль краёв: чем ближе к
 * краю, тем сильнее увеличение — как у толстого стекла с прямыми гранями.
 * [bendAmount] — доля изгиба (0 — плоское стекло, 1 — как в Glass Lens).
 */
private fun DrawScope.drawLens(content: GraphicsLayer, left: Float, zoom: Float, bendAmount: Float, dark: Boolean, shape: Shape) {
    val bounds = Rect(Offset.Zero, size)
    val outline = shape.createOutline(size, layoutDirection, this)
    drawRect(if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f))
    // Границы колец в px от края — те же доли, что у круглой линзы (52/74/90% радиуса).
    val r = minOf(size.width, size.height) / 2
    val inner = r * 0.48f
    val middle = r * 0.26f
    val rim = r * 0.10f
    LensBend.forEachIndexed { i, b ->
        val bend = 1f + (b - 1f) * bendAmount
        drawIntoCanvas { it.saveLayer(bounds, Paint()) }
        withTransform({
            scale(zoom * bend, zoom * bend, pivot = center)
            translate(-left, 0f)
        }) { drawLayer(content) }
        // Каждая копия плавно уступает место следующей, без двоения.
        when (i) {
            0 -> edgeMask(middle, inner, BlendMode.DstIn)
            1 -> {
                edgeMask(rim, middle, BlendMode.DstIn)
                edgeMask(middle, inner, BlendMode.DstOut)
            }
            else -> edgeMask(rim, middle, BlendMode.DstOut)
        }
        drawIntoCanvas { it.restore() }
    }
    // Полоса преломления: край толстого стекла чуть темнее (обводки наполовину
    // обрезаны формой, поэтому ширина вдвое больше видимой).
    listOf(middle * 2 to 0.03f, middle to 0.045f, rim * 1.2f to 0.055f).forEach { (width, alpha) ->
        drawOutline(outline, RefractionInk.copy(alpha = alpha), style = Stroke(width))
    }
    drawSpecular(1f, spark = true)
    // Цветная кайма у кромки, наложение «экран», и тонкий яркий ободок.
    drawOutline(outline, fringeBrush(), style = Stroke(5.dp.toPx()), blendMode = BlendMode.Screen)
    drawOutline(outline, Color.White.copy(alpha = 0.07f), style = Stroke(6.dp.toPx()))
    drawOutline(outline, Color.White.copy(alpha = 0.32f), style = Stroke(2.2.dp.toPx()))
}
