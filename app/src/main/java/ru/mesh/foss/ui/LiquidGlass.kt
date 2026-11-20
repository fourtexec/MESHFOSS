package ru.mesh.foss.ui

import ru.mesh.foss.core.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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

    /** Панель вкладок — квадрат со скруглёнными углами, а не капсула. */
    val BarShape = RoundedCornerShape(BarRadius)

    /** Линза под выбранной вкладкой. */
    val LensShape = RoundedCornerShape(LensRadius)

    /** Стеклянные кнопки: действия в шапке, «Питание». */
    val ButtonShape = RoundedCornerShape(16.dp)

    fun isDark(background: Color) = background.luminance() < 0.45f
}

private class GlassLight {
    var touch by mutableStateOf<Offset?>(null)
}

private val LocalGlassLight = staticCompositionLocalOf<GlassLight?> { null }

/**
 * Фон «жидкого стекла», записанный в слой. Стеклянные панели рисуют свой кусок
 * этого слоя через шейдер преломления ([LiquidShader]) — так стекло по-настоящему
 * искажает то, что под ним.
 */
private class LiquidBackdropSource {
    /** Положение фона в окне. */
    var origin by mutableStateOf(Offset.Zero)

    /** Фон в половинном разрешении (пятна и так размыты) — то, что преломляют панели. */
    var image by mutableStateOf<ImageBitmap?>(null)
}

/** Во сколько раз фон для преломления меньше экрана. */
private const val BackdropScale = 2

private val LocalLiquidBackdrop = staticCompositionLocalOf<LiquidBackdropSource?> { null }

/**
 * Подложка «жидкого стекла»: насыщенные, сильно размытые пятна цветов темы —
 * стеклу нужно, что преломлять, — и свет, следящий за касанием.
 */
@Composable
fun LiquidBackdrop(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val lightSource = remember { GlassLight() }
    val base = if (dark) lerp(scheme.background, Color.Black, 0.35f) else scheme.background
    val primary = scheme.primary
    val tertiary = scheme.tertiary
    val mid = lerp(primary, tertiary, 0.5f)
    val k = if (dark) 1f else 0.6f
    val source = remember { LiquidBackdropSource() }
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
            .onGloballyPositioned { source.origin = it.positionInRoot() }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val r = size.maxDimension
                fun blob(color: Color, alpha: Float, cx: Float, cy: Float, radius: Float) = Brush.radialGradient(
                    0f to color.copy(alpha = alpha * k),
                    0.45f to color.copy(alpha = alpha * k * 0.45f),
                    1f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = radius
                )
                val a = blob(primary, 0.42f, w * 0.08f, h * 0.10f, r * 0.62f)
                val b = blob(mid, 0.26f, w * 1.0f, h * 0.42f, r * 0.50f)
                val c = blob(tertiary, 0.30f, w * 0.18f, h * 0.92f, r * 0.58f)
                // Фон рисуется в картинку: его же, преломлённым, рисуют стеклянные панели.
                val iw = (w / BackdropScale).toInt().coerceAtLeast(1)
                val ih = (h / BackdropScale).toInt().coerceAtLeast(1)
                val image = ImageBitmap(iw, ih)
                CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(image), Size(iw.toFloat(), ih.toFloat())) {
                    scale(1f / BackdropScale, 1f / BackdropScale, pivot = Offset.Zero) {
                        drawRect(base, size = Size(w, h))
                        drawRect(a, size = Size(w, h))
                        drawRect(b, size = Size(w, h))
                        drawRect(c, size = Size(w, h))
                    }
                }
                source.image = image
                onDrawBehind {
                    drawImage(image, dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.Low)
                }
            }
        )
        CompositionLocalProvider(
            LocalLiquidBackdrop provides source,
            LocalGlassLight provides lightSource,
            LocalContentColor provides scheme.onBackground,
            content = content
        )
    }
}

/**
 * Мягкая тень, нарисованная только снаружи [shape]. Обычная тень Compose
 * просвечивала бы сквозь прозрачное стекло и мутила его. До Android 9 аппаратное
 * ускорение не рисует такие тени — там их просто нет.
 */
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

/* Оптика стекла — по мотивам «Glass Lens» (DesignBookmark): стекло читается
 * не по обводке, а по тому, как оно ведёт себя со светом — тёмная полоса
 * преломления у края толстого стекла, тонкий яркий ободок, мягкое отражение окна
 * сверху слева, искорка снизу справа и лёгкая цветная кайма у самой кромки. */

/** Цвет полосы преломления: край толстого стекла чуть темнее. */
private val RefractionInk = Color(0xFF0A141E)

/**
 * Цветная кайма (дисперсия) по кругу: тёплая с одной стороны, холодная с
 * другой. В CSS — `conic-gradient(from 200deg, …)`: отсчёт от «12 часов». У
 * sweepGradient отсчёт от «3 часов», поэтому стопы сдвинуты на 110°. Двигаем
 * именно стопы, а не поворачиваем рисунок, — иначе повернулась бы и сама фигура.
 */
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
    // Цвет на стыке 0/1: между последним и первым стопом после сдвига.
    val last = moved.last()
    val first = moved.first()
    val gap = 1f - last.first + first.first
    val t = if (gap > 0f) (1f - last.first) / gap else 0f
    val seam = lerp(last.second, first.second, t)
    (listOf(0f to seam) + moved + listOf(1f to seam)).toTypedArray()
}

/**
 * Эллиптический радиальный градиент с центром ([cx], [cy]) и полуосями [rx], [ry]
 * — аналог CSS `radial-gradient(rx ry at cx cy)`. Рисует прямоугольник,
 * описанный вокруг эллипса.
 */
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

/**
 * Мягкое отражение окна сверху слева и (для [spark]) маленькая искорка снизу
 * справа. Рецепт рассчитан на круглую линзу, поэтому на широких панелях ширина
 * блика ограничена высотой — иначе он вытягивался бы в полосу.
 */
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

/** Кисть цветной каймы: конический градиент, повёрнутый как `from 200deg` в CSS. */
private fun DrawScope.fringeBrush(): Brush = Brush.sweepGradient(*FringeStops, center = center)

/**
 * Материал «жидкого стекла». [tint] слегка окрашивает стекло (выделение,
 * акцентная кнопка); без него стекло нейтральное. [elevation] — размер мягкой
 * внешней тени, 0 — без тени.
 *
 * На Android 13+ стекло по-настоящему преломляет фон под собой шейдером Liquid
 * Glass Studio ([LiquidShader]): искажение у края, дисперсия, френель и блик.
 * [refract] = false — без преломления (например, в диалоге: он живёт в другом
 * окне, и слой фона туда не дотянуть). На старых версиях — прежняя отрисовка.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = Color.Unspecified,
    elevation: Dp = 6.dp,
    refract: Boolean = true
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val clear = if (dark) lerp(scheme.surface, Color.White, 0.10f).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.55f)
    val tintFill = if (tint != Color.Unspecified) tint.copy(alpha = tint.alpha * 0.55f) else Color.Transparent
    val rimTop = Color.White.copy(alpha = if (dark) 0.50f else 0.95f)
    val rimMid = Color.White.copy(alpha = if (dark) 0.10f else 0.45f)
    val rimBottom = Color.White.copy(alpha = if (dark) 0.34f else 0.85f)
    val shadow = Color.Black.copy(alpha = if (dark) 0.40f else 0.10f)
    val specular = if (dark) 1f else 1.6f
    val lightSource = LocalGlassLight.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val backdrop = LocalLiquidBackdrop.current
    val refractive = refract && backdrop != null && LiquidShader.available
    // Заливка стекла для шейдера: оттенок поверх прозрачной основы; альфа — доля заливки.
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
            // Обводки рисуются по контуру и наполовину обрезаются формой, поэтому
            // ширина вдвое больше видимой. Полоса преломления — несколько слоёв:
            // темнее всего у края, к середине плавно сходит на нет.
            val refraction = (if (dark) listOf(20.dp to 0.025f, 12.dp to 0.04f, 6.dp to 0.05f)
                else listOf(14.dp to 0.010f, 8.dp to 0.016f, 4.dp to 0.022f)).map { (w, a) ->
                Stroke(width = w.toPx()) to RefractionInk.copy(alpha = a)
            }
            val rimStroke = Stroke(width = 2.dp.toPx())
            val fringeStroke = Stroke(width = 5.dp.toPx())
            val fringe = Brush.sweepGradient(*FringeStops, center = Offset(size.width / 2, size.height / 2))
            val radiusPx = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: (minOf(size.width, size.height) / 2)
            onDrawWithContent {
                val image = backdrop?.image
                if (refractive && image != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Под стеклом — кусок фона: BitmapShader в координатах панели.
                    val under = android.graphics.BitmapShader(
                        image.asAndroidBitmap(),
                        android.graphics.Shader.TileMode.CLAMP,
                        android.graphics.Shader.TileMode.CLAMP
                    ).apply {
                        setLocalMatrix(android.graphics.Matrix().apply {
                            setScale(BackdropScale.toFloat(), BackdropScale.toFloat())
                            postTranslate(backdrop.origin.x - origin.x, backdrop.origin.y - origin.y)
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

/** Стекло панелей: тоньше и спокойнее линзы — под ним плавный фон, а не иконки. */
private val PanelParams = LiquidShader.Params(thickness = 14f, offsetDp = 10f, dispersion = 5f)

/**
 * Линза: та же форма угла, что у панели (обычная дуга, n = 2), и концентричный
 * радиус ([Liquid.LensRadius] = радиус панели − отступ) — линза повторяет контур
 * панели с ровным зазором, в том числе в углах. Толстое стекло, сильное преломление.
 */
private val LensParams = LiquidShader.Params(roundness = 2f, thickness = 18f, offsetDp = 14f, dispersion = 5f, fresnelFactor = 0.25f)

/**
 * Шапка в стиле iOS: крупный жирный заголовок прямо на фоне и действия в
 * стеклянной капсуле справа. Содержимое — то же, что у обычной шапки.
 */
@Composable
fun LiquidTopBar(title: String, actions: @Composable RowScope.() -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = ink
        )
        Spacer(Modifier.width(10.dp))
        CompositionLocalProvider(LocalContentColor provides ink) {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .liquidGlass(Liquid.ButtonShape, elevation = 8.dp)
                    .padding(horizontal = 2.dp),
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
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(66.dp)
            .liquidGlass(Liquid.BarShape, elevation = 16.dp)
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
            // Android 13+: линза — шейдер Liquid Glass Studio прямо по слою иконок.
            val shaderLens = LiquidShader.effectAvailable
            val lensTint = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
            var rowOrigin by remember { mutableStateOf(Offset.Zero) }
            Row(
                Modifier
                    .fillMaxSize()
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
                        if (shaderLens || LiquidPreview.enabled) {
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
            if (!shaderLens && !LiquidPreview.enabled) Box(
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
