package ru.mesh.foss

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    /** Капсула — форма панели вкладок, линзы и кнопок. */
    val Capsule = RoundedCornerShape(percent = 50)

    fun isDark(background: Color) = background.luminance() < 0.45f
}

private class GlassLight {
    var touch by mutableStateOf<Offset?>(null)
}

private val LocalGlassLight = staticCompositionLocalOf<GlassLight?> { null }

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
            .background(base)
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
                onDrawBehind {
                    drawRect(a)
                    drawRect(b)
                    drawRect(c)
                }
            }
    ) {
        CompositionLocalProvider(
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
    val paint = Paint().asFrameworkPaint().apply {
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

/**
 * Материал «жидкого стекла». [tint] слегка окрашивает стекло (выделение,
 * акцентная кнопка); без него стекло нейтральное. [elevation] — размер тени,
 * 0 — без тени (вложенные элементы).
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = Color.Unspecified,
    elevation: Dp = 10.dp
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val clear = if (dark) lerp(scheme.surface, Color.White, 0.10f).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.52f)
    val tintFill = if (tint != Color.Unspecified) tint.copy(alpha = tint.alpha * 0.55f) else Color.Transparent
    val highlight = Color.White.copy(alpha = if (dark) 0.10f else 0.42f)
    val refraction = if (dark) 0.07f else 0.24f
    val rimTop = Color.White.copy(alpha = if (dark) 0.55f else 0.95f)
    val rimMid = Color.White.copy(alpha = if (dark) 0.07f else 0.30f)
    val rimBottom = Color.White.copy(alpha = if (dark) 0.32f else 0.80f)
    val shadow = Color.Black.copy(alpha = if (dark) 0.45f else 0.12f)
    val lightSource = LocalGlassLight.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val base = if (elevation > 0.dp) outerShadow(shape, shadow, elevation * 1.6f, elevation * 0.45f) else this
    return base
        .onGloballyPositioned { origin = it.positionInRoot() }
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val sheen = Brush.verticalGradient(
                0f to highlight,
                0.35f to highlight.copy(alpha = highlight.alpha * 0.25f),
                0.6f to Color.Transparent,
                1f to Color.White.copy(alpha = highlight.alpha * 0.35f),
            )
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
            // Обводки рисуются по контуру и наполовину обрезаются формой,
            // поэтому ширина вдвое больше видимой. Преломление — несколько слоёв
            // убывающей ширины: свечение плавно гаснет к середине, без ступеньки.
            val glowLayers = listOf(22.dp to 0.18f, 13.dp to 0.26f, 7.dp to 0.32f, 3.dp to 0.40f).map { (w, k) ->
                Stroke(width = w.toPx()) to Color.White.copy(alpha = refraction * k)
            }
            val rimStroke = Stroke(width = 2.4.dp.toPx())
            onDrawWithContent {
                drawOutline(outline, clear)
                if (tintFill.alpha > 0f) drawOutline(outline, tintFill)
                drawOutline(outline, sheen)
                glowLayers.forEach { (stroke, color) -> drawOutline(outline, color, style = stroke) }
                lightSource?.touch?.let { touch ->
                    val center = touch - origin
                    drawContext.transform.translate(center.x, center.y)
                    drawCircle(glint, glintRadius, Offset.Zero)
                    drawContext.transform.translate(-center.x, -center.y)
                }
                drawContent()
                drawOutline(outline, rim, style = rimStroke)
            }
        }
}

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
                    .liquidGlass(Liquid.Capsule, elevation = 8.dp)
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
 * Плавающая капсула вкладок в стиле iOS: под выбранной вкладкой — стеклянная
 * «линза», которая с пружиной переезжает к новой вкладке; иконка и подпись
 * выбранной вкладки окрашены акцентом. Нажатая вкладка чуть увеличивается.
 */
@Composable
fun LiquidTabBar(items: List<BarNavItem>, selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = Liquid.isDark(scheme.background)
    val index = items.indexOfFirst { it.key == selected }
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(66.dp)
            .liquidGlass(Liquid.Capsule, elevation = 16.dp)
            .padding(4.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / items.size.coerceAtLeast(1)
            val lensX by animateDpAsState(
                itemWidth * index.coerceAtLeast(0),
                spring(dampingRatio = 0.72f, stiffness = 420f),
                label = "liquidLens"
            )
            val lensAlpha by animateFloatAsState(if (index >= 0) 1f else 0f, tween(180), label = "liquidLensAlpha")
            // Линза: более плотное стекло со своей кромкой, без тени.
            Box(
                Modifier
                    .offset(x = lensX)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = lensAlpha }
                    .liquidGlass(
                        Liquid.Capsule,
                        tint = if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.10f),
                        elevation = 0.dp
                    )
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                items.forEach { item ->
                    val on = item.key == selected
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        if (pressed) 1.12f else 1f,
                        spring(dampingRatio = 0.55f, stiffness = 500f),
                        label = "liquidPress"
                    )
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
                            .clip(Liquid.Capsule)
                            .clickable(interactionSource = interaction, indication = null) { onSelect(item.key) }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = ink, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.label,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = ink
                        )
                    }
                }
            }
        }
    }
}
