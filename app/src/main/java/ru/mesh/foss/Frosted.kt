package ru.mesh.foss

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * «Матовое стекло» ([Design.FROSTED]) — внешний вид в духе Uxaoro.
 *
 * Переносится только оформление, поведение экранов не меняется:
 *  - почти нейтральный фон (тёмный с лёгкой сине-фиолетовой ноткой или светлый) и
 *    медленно дрейфующие свечения цвета акцента — [FrostedBackdrop];
 *  - плотные (непрозрачные) карточки со скруглением 16dp и тенью, окрашенной в
 *    акцент, — [Modifier.frostedCard]; матовость оставлена «хрому», чтобы
 *    содержимое читалось спокойно;
 *  - плавающие матовые шапка и нижняя панель со скруглением 14dp, светлой кромкой и
 *    бликом сверху — [FrostedTopBar], [FrostedNavBar];
 *  - выбранная вкладка — заливка акцентом («таблетка») с точкой под подписью;
 *  - диалоги и всплывающие панели — полупрозрачная пластина с кромкой
 *    ([Modifier.frostedSurface]).
 *
 * Акцент берётся из выбранной темы, поэтому все палитры работают и здесь.
 */
object Frosted {
    /** Скругление карточек и полей. */
    val CardShape = RoundedCornerShape(16.dp)

    /** Скругление плавающих шапки и нижней панели. */
    val ChromeShape = RoundedCornerShape(14.dp)

    fun isDark(scheme: ColorScheme) = scheme.background.luminance() < 0.45f

    /**
     * Цветовая схема в стиле Uxaoro: нейтральные фон и поверхности с лёгким
     * оттенком акцента, акцент — из темы.
     */
    fun scheme(base: ColorScheme): ColorScheme {
        val dark = isDark(base)
        val accent = base.primary
        val bg = if (dark) Color(0xFF0A0A10) else Color(0xFFFBFAFE)
        val onBg = if (dark) Color(0xFFF4F4F6) else Color(0xFF14131A)
        val surface = if (dark) Color(0xFF0C0C12) else Color(0xFFF3F2F8)
        val variant = if (dark) lerp(Color(0xFF16161E), accent, 0.10f) else lerp(Color(0xFFF0EFF6), accent, 0.06f)
        val onVariant = if (dark) Color(0xFFDDDDE4) else Color(0xFF565560)
        val outline = if (dark) Color(0xFF2A2A34) else Color(0xFFE3E2EA)
        val card = cardFill(dark, surface)
        val selected = lerp(variant, accent, if (dark) 0.22f else 0.16f)
        return base.copy(
            primary = accent,
            onPrimary = ink(accent),
            primaryContainer = selected,
            onPrimaryContainer = onBg,
            secondary = accent.copy(alpha = 0.85f),
            onSecondary = ink(accent),
            secondaryContainer = selected,
            onSecondaryContainer = onBg,
            tertiary = accent,
            onTertiary = ink(accent),
            background = bg,
            onBackground = onBg,
            surface = surface,
            onSurface = onBg,
            surfaceVariant = variant,
            onSurfaceVariant = onVariant,
            surfaceTint = accent,
            outline = outline,
            outlineVariant = outline,
            surfaceContainerLowest = bg,
            surfaceContainerLow = card,
            surfaceContainer = card,
            surfaceContainerHigh = if (dark) Color(0xFF11121A) else variant,
            surfaceContainerHighest = variant,
            surfaceBright = variant,
            surfaceDim = bg
        )
    }

    /** Заливка плотной карточки. */
    fun cardFill(dark: Boolean, surface: Color): Color = if (dark) Color(0xFF0D0E14) else surface

    private fun ink(color: Color): Color {
        val l = color.luminance()
        return if ((l + 0.05f) / 0.05f >= 1.05f / (l + 0.05f)) Color.Black else Color.White
    }
}

/**
 * Тень карточек и панелей: в тёмной теме — почти чёрная, в светлой — мягкая с
 * оттенком акцента. Под полупрозрачной пластиной тень просвечивает сквозь заливку,
 * поэтому для неё берётся вариант слабее ([translucent]).
 */
@Composable
private fun frostedShadow(translucent: Boolean = false): Color {
    val scheme = MaterialTheme.colorScheme
    return if (Frosted.isDark(scheme)) {
        Color.Black.copy(alpha = if (translucent) 0.40f else 0.68f)
    } else {
        scheme.primary.copy(alpha = if (translucent) 0.10f else 0.16f)
    }
}

/** Светлая кромка в 1dp по краю матовой пластины. */
@Composable
fun frostedHairline(): Color =
    if (Frosted.isDark(MaterialTheme.colorScheme)) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.70f)

/**
 * Плотная карточка «матового стекла»: непрозрачная заливка и тень с оттенком
 * акцента. [tint] — своя заливка (например, подсветка выбранного элемента).
 */
@Composable
fun Modifier.frostedCard(
    shape: Shape = Frosted.CardShape,
    tint: Color = Color.Unspecified,
    elevation: Dp = 8.dp
): Modifier {
    val scheme = MaterialTheme.colorScheme
    // Карточка всегда непрозрачна: полупрозрачный оттенок накладываем на заливку,
    // иначе сквозь него просвечивала бы тень.
    val card = scheme.surfaceContainerLow.copy(alpha = 1f)
    val fill = if (tint != Color.Unspecified) tint.compositeOver(card) else card
    val shadow = frostedShadow()
    return this
        .shadow(elevation, shape, ambientColor = shadow, spotColor = shadow)
        .clip(shape)
        .background(fill, shape)
}

/**
 * Матовая пластина: полупрозрачная заливка, блик сверху и светлая кромка. Для
 * шапки, нижней панели и диалогов.
 */
@Composable
fun Modifier.frostedSurface(
    shape: Shape = Frosted.ChromeShape,
    elevation: Dp = 8.dp,
    opacity: Float? = null
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = Frosted.isDark(scheme)
    val base = if (dark) Color(0xFF101119) else scheme.surfaceVariant
    val alpha = opacity ?: if (dark) 0.84f else 0.86f
    val fill = base.copy(alpha = alpha)
    val sheen = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f)
    val shadow = frostedShadow(translucent = alpha < 0.95f)
    return this
        .shadow(elevation, shape, ambientColor = shadow, spotColor = shadow)
        .clip(shape)
        .background(fill, shape)
        .background(Brush.verticalGradient(listOf(sheen, Color.Transparent, Color.Transparent)), shape)
        .border(1.dp, frostedHairline(), shape)
}

/** Выключены ли анимации в системе (масштаб длительности анимаций = 0); проверяется при входе. */
@Composable
private fun systemAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Фон «матового стекла»: базовый цвет и три мягких свечения акцента, медленно
 * дрейфующих по экрану (основной период 26 с). Позиции считаются на этапе
 * отрисовки, так что анимация не вызывает рекомпозиций. При выключенных системных
 * анимациях фон статичный.
 */
@Composable
fun FrostedBackdrop(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = Frosted.isDark(scheme)
    val base = scheme.background
    val glowA = scheme.primary.copy(alpha = if (dark) 0.11f else 0.10f)
    val glowB = scheme.tertiary.copy(alpha = if (dark) 0.08f else 0.07f)
    val glowC = scheme.primary.copy(alpha = 0.06f)
    val sheen = scheme.primary.copy(alpha = 0.025f)
    val still = systemAnimationsOff()
    // Множители фаз ниже кратны 0.1, поэтому за 20π все свечения проходят целое число
    // циклов и перезапуск анимации не даёт скачка. Скорость — 2π за 26 с.
    val driftState: State<Float> = if (still) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "frostedMesh").animateFloat(
            initialValue = 0f,
            targetValue = (20.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(260_000, easing = LinearEasing), RepeatMode.Restart),
            label = "frostedDrift"
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                val drift = driftState.value
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                fun glow(color: Color, center: Offset, radius: Float, mid: Float) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(color, color.copy(alpha = color.alpha * mid), Color.Transparent),
                            center = center,
                            radius = radius
                        )
                    )
                }
                glow(
                    glowA,
                    Offset(w * (0.22f + 0.10f * sin(drift)), h * (0.14f + 0.08f * cos(drift * 0.7f))),
                    w * if (dark) 0.78f else 0.95f,
                    0.45f
                )
                glow(
                    glowB,
                    Offset(w * (0.80f + 0.08f * cos(drift * 0.9f)), h * (0.52f + 0.10f * sin(drift * 0.6f))),
                    w * if (dark) 0.70f else 0.85f,
                    0.40f
                )
                glow(
                    glowC,
                    Offset(w * (0.46f + 0.12f * sin(drift * 0.5f + 1.2f)), h * (0.96f + 0.03f * cos(drift * 0.8f))),
                    w * if (dark) 0.85f else 0.95f,
                    0.45f
                )
                if (dark) {
                    // Лёгкий блик сверху приподнимает зону шапки над чёрным фоном.
                    drawRect(Brush.verticalGradient(listOf(sheen, Color.Transparent), startY = 0f, endY = h * 0.45f))
                }
            }
    ) {
        // Прозрачные Scaffold/Surface берут цвет текста отсюда — иначе он был бы чёрным.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

/**
 * Плавающая матовая шапка: пластина высотой 44dp со скруглением 14dp и
 * компактным заголовком 15sp. Содержимое (заголовок и действия) — то же, что у
 * обычной шапки.
 */
@Composable
fun FrostedTopBar(title: String, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(48.dp)
            .frostedSurface(Frosted.ChromeShape, elevation = 6.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}


/**
 * Плавающая матовая нижняя панель: выбранная вкладка залита акцентом, под
 * подписью — точка, выбранная вкладка слегка увеличивается пружиной.
 */
@Composable
fun FrostedNavBar(items: List<BarNavItem>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .frostedSurface(Frosted.ChromeShape, elevation = 14.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val scheme = MaterialTheme.colorScheme
        items.forEach { item ->
            val on = item.key == selected
            val pill by animateColorAsState(if (on) scheme.primary else scheme.primary.copy(alpha = 0f), tween(220), label = "frostedPill")
            val ink by animateColorAsState(if (on) scheme.onPrimary else scheme.onBackground, tween(220), label = "frostedInk")
            val scale by animateFloatAsState(
                if (on) 1.08f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "frostedScale"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(11.dp))
                    .background(pill)
                    .clickable { onSelect(item.key) }
                    .padding(horizontal = 2.dp, vertical = 5.dp)
            ) {
                Icon(item.icon, contentDescription = item.label, tint = ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(2.dp))
                Text(
                    item.label,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = ink
                )
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(if (on) ink else Color.Transparent)
                )
            }
        }
    }
}
