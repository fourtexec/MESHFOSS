package ru.mesh.foss

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* -------------------------------------------------------------------------- */
/* Матовое стекло (frosted glass) — оформление в стиле приложения Uxaoro.       */
/* -------------------------------------------------------------------------- */

/**
 * «Матовое стекло» ([Design.FROSTED]) — оформление по образцу Android-клиента Uxaoro:
 *  - спокойный почти чёрный (или почти белый) фон с медленно плывущими свечениями
 *    цвета акцента ([FrostedBackdrop]);
 *  - размытие (Haze) только у «хрома»: плавающие шапка и нижняя панель вкладок
 *    ([FrostedTopBar], [FrostedNavBar], [Modifier.frostedBar]);
 *  - карточки — плотные приподнятые поверхности с тенью в тон акцента
 *    ([Modifier.frostedSurface]), чтобы содержимое читалось поверх живого фона.
 *
 * Акцент берётся из выбранной темы, так что палитры по-прежнему работают.
 */
object Frosted {
    private val DarkBackground = Color(0xFF0A0A10)
    private val DarkSurface = Color(0xFF0C0C12)
    private val DarkCard = Color(0xFF0D0E14)
    private val DarkCardIntense = Color(0xFF11121A)
    private val DarkVariant = Color(0xFF16161E)
    private val DarkInk = Color(0xFFF4F4F6)
    private val DarkInkVariant = Color(0xFFDDDDE4)
    private val DarkOutline = Color(0xFF2A2A34)

    private val LightBackground = Color(0xFFFBFAFE)
    private val LightSurface = Color(0xFFF3F2F8)
    private val LightVariant = Color(0xFFF0EFF6)
    private val LightInk = Color(0xFF14131A)
    private val LightInkVariant = Color(0xFF565560)
    private val LightOutline = Color(0xFFE3E2EA)

    fun isDark(scheme: ColorScheme) = scheme.background.luminance() < 0.45f

    /**
     * Схема Uxaoro поверх выбранной темы: нейтральные фон и поверхности с лёгким
     * оттенком акцента, акцент и светлота (тёмная/светлая) — из темы.
     */
    fun colors(base: ColorScheme): ColorScheme {
        val dark = isDark(base)
        val accent = base.primary
        fun ink(color: Color) = if (color.luminance() > .179f) Color.Black else Color.White
        // Чуть-чуть фона исходной темы, чтобы «Океан» и «Лес» не сливались в один чёрный.
        fun tinted(neutral: Color, amount: Float) = lerp(neutral, base.background, amount)
        return if (dark) {
            val background = tinted(DarkBackground, .14f)
            val variant = lerp(DarkVariant, accent, .10f)
            val selection = lerp(DarkVariant, accent, .26f)
            base.copy(
                primary = accent, onPrimary = ink(accent),
                primaryContainer = selection, onPrimaryContainer = DarkInk,
                secondary = accent.copy(alpha = .85f), onSecondary = ink(accent),
                secondaryContainer = selection, onSecondaryContainer = DarkInk,
                tertiary = accent, onTertiary = ink(accent),
                background = background, onBackground = DarkInk,
                surface = tinted(DarkSurface, .14f), onSurface = DarkInk,
                surfaceVariant = variant, onSurfaceVariant = DarkInkVariant,
                surfaceContainerLowest = background,
                surfaceContainerLow = tinted(DarkCard, .14f),
                surfaceContainer = tinted(DarkCardIntense, .14f),
                surfaceContainerHigh = variant,
                surfaceContainerHighest = lerp(DarkVariant, accent, .16f),
                surfaceBright = lerp(DarkVariant, accent, .16f),
                surfaceDim = background,
                outline = DarkOutline,
                outlineVariant = DarkOutline
            )
        } else {
            val background = tinted(LightBackground, .10f)
            val variant = lerp(LightVariant, accent, .06f)
            val selection = lerp(LightVariant, accent, .20f)
            base.copy(
                primary = accent, onPrimary = ink(accent),
                primaryContainer = selection, onPrimaryContainer = LightInk,
                secondary = accent, onSecondary = ink(accent),
                secondaryContainer = selection, onSecondaryContainer = LightInk,
                tertiary = accent, onTertiary = ink(accent),
                background = background, onBackground = LightInk,
                surface = tinted(LightSurface, .10f), onSurface = LightInk,
                surfaceVariant = variant, onSurfaceVariant = LightInkVariant,
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = tinted(LightSurface, .10f),
                surfaceContainer = variant,
                surfaceContainerHigh = variant,
                surfaceContainerHighest = lerp(LightVariant, accent, .10f),
                surfaceBright = Color.White,
                surfaceDim = background,
                outline = LightOutline,
                outlineVariant = LightOutline
            )
        }
    }
}

/** Источник размытия для шапки и нижней панели (фон со свечениями). */
val LocalFrostedHaze = staticCompositionLocalOf<HazeState?> { null }

/** Тень карточек и панелей в тон акцента; на светлой теме — едва заметная. */
@Composable
private fun frostedShadow(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (Frosted.isDark(scheme)) Color.Black.copy(alpha = 0.68f) else scheme.primary.copy(alpha = 0.16f)
}

/**
 * Светлая кромка в 1dp по краю стекла: по ней матовая пластина и читается как
 * стекло, а не как выцветшая панель.
 */
@Composable
fun frostedHairline(): Color =
    if (Frosted.isDark(MaterialTheme.colorScheme)) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.70f)

/** Стиль размытия: почти чёрный тинт в тёмной теме, чтобы хром не становился «молочным». */
@Composable
fun frostedHazeStyle(): HazeStyle {
    val scheme = MaterialTheme.colorScheme
    val dark = Frosted.isDark(scheme)
    return HazeStyle(
        backgroundColor = scheme.background,
        tint = HazeTint(if (dark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.40f)),
        blurRadius = if (dark) 20.dp else 24.dp,
        noiseFactor = 0f,
        // Android < 12 не умеет RenderEffect-размытие: плотная заливка вместо стекла.
        fallbackTint = HazeTint(
            if (dark) Color(0xFF101119).copy(alpha = 0.94f) else scheme.surfaceVariant.copy(alpha = 0.96f)
        )
    )
}

/**
 * Плотная приподнятая поверхность для карточек и полей: заливка, тень в тон
 * акцента и тонкая светлая кромка. Размытие оставлено только хрому.
 */
@Composable
fun Modifier.frostedSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    fill: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    elevation: Dp = 6.dp
): Modifier {
    val shadow = frostedShadow()
    return this
        .shadow(elevation, shape, ambientColor = shadow, spotColor = shadow)
        .clip(shape)
        .background(fill, shape)
        .border(1.dp, frostedHairline().copy(alpha = if (Frosted.isDark(MaterialTheme.colorScheme)) 0.05f else 0.6f), shape)
}

/**
 * Матовый «хром» (шапка, нижняя панель): размывает живой фон под собой. Без
 * источника размытия — плотная заливка.
 */
@Composable
fun Modifier.frostedBar(
    shape: Shape = RoundedCornerShape(14.dp),
    elevation: Dp = 8.dp
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val haze = LocalFrostedHaze.current
    val shadow = frostedShadow()
    val fallback = if (Frosted.isDark(scheme)) Color(0xFF101119) else scheme.surfaceVariant
    return this
        .shadow(elevation, shape, ambientColor = shadow, spotColor = shadow)
        .clip(shape)
        .then(
            if (haze != null) Modifier.hazeEffect(state = haze, style = frostedHazeStyle())
            else Modifier.background(fallback, shape)
        )
        .border(1.dp, frostedHairline(), shape)
}

/** Пользователь отключил анимации в системе («Удалить анимацию»). */
@Composable
private fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Фон «матового стекла»: базовый цвет и три мягких свечения акцента, медленно
 * дрейфующих (цикл 26 с). Фон — отдельный слой-источник размытия для хрома, а
 * содержимое рисуется поверх него. Анимация читается только в фазе отрисовки —
 * без рекомпозиций. При отключённых анимациях фон статичен.
 */
@Composable
fun FrostedBackdrop(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = Frosted.isDark(scheme)
    val haze = remember { HazeState() }
    val glowA = scheme.primary.copy(alpha = if (dark) 0.13f else 0.10f)
    val glowB = scheme.tertiary.copy(alpha = if (dark) 0.09f else 0.07f)
    val glowC = scheme.primary.copy(alpha = if (dark) 0.07f else 0.06f)
    val sheen = scheme.primary.copy(alpha = 0.03f)
    val drift = if (reduceMotion()) {
        null
    } else {
        rememberInfiniteTransition(label = "frostedMesh").animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(26_000), RepeatMode.Restart),
            label = "frostedDrift"
        )
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().hazeSource(haze).background(scheme.background)) {
            val d = drift?.value ?: 0f
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            // Три свечения: слева сверху, справа посередине и снизу по центру.
            val c1 = Offset(w * (0.22f + 0.10f * sin(d)), h * (0.14f + 0.08f * cos(d * 0.7f)))
            val c2 = Offset(w * (0.80f + 0.08f * cos(d * 0.9f)), h * (0.52f + 0.10f * sin(d * 0.6f)))
            val c3 = Offset(w * (0.46f + 0.12f * sin(d * 0.5f + 1.2f)), h * (0.96f + 0.03f * cos(d * 0.8f)))
            fun glow(color: Color, center: Offset, radius: Float, mid: Float) = drawRect(
                Brush.radialGradient(
                    listOf(color, color.copy(alpha = color.alpha * mid), Color.Transparent),
                    center = center,
                    radius = radius
                )
            )
            glow(glowA, c1, w * (if (dark) 0.78f else 0.95f), 0.45f)
            glow(glowB, c2, w * (if (dark) 0.70f else 0.85f), 0.40f)
            glow(glowC, c3, w * (if (dark) 0.85f else 0.95f), 0.45f)
            if (dark) {
                // Едва заметный верхний отсвет отделяет зону шапки от чёрного.
                drawRect(Brush.verticalGradient(listOf(sheen, Color.Transparent), startY = 0f, endY = h * 0.45f))
            }
        }
        CompositionLocalProvider(
            LocalFrostedHaze provides haze,
            LocalContentColor provides scheme.onBackground,
            content = content
        )
    }
}

/**
 * Плавающая матовая шапка: скруглённая пластина высотой 44dp с отступами от краёв,
 * заголовок 15sp полужирным, справа — действия.
 */
@Composable
fun FrostedTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(44.dp)
            .frostedBar(RoundedCornerShape(14.dp), elevation = 6.dp)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = scheme.onBackground
        )
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Пункт нижней панели «матового стекла». */
class FrostedNavItem(val key: String, val label: String, val icon: Painter)

/**
 * Плавающая матовая панель вкладок: выбранная вкладка — «пилюля» цвета акцента,
 * иконка чуть увеличивается, под подписью — точка-маркер.
 */
@Composable
fun FrostedNavBar(items: List<FrostedNavItem>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .frostedBar(RoundedCornerShape(16.dp), elevation = 14.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val on = item.key == selected
            val scheme = MaterialTheme.colorScheme
            val pill by animateColorAsState(if (on) scheme.primary else Color.Transparent, tween(220), label = "tabPill")
            val ink by animateColorAsState(if (on) scheme.onPrimary else scheme.onBackground, tween(220), label = "tabInk")
            val scale by animateFloatAsState(if (on) 1.06f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "tabScale")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(pill)
                    .clickable(role = Role.Tab) { onSelect(item.key) }
                    .padding(horizontal = 2.dp, vertical = 5.dp)
            ) {
                Icon(item.icon, contentDescription = item.label, tint = ink, modifier = Modifier.size(20.dp))
                Text(
                    item.label,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = ink
                )
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(if (on) ink else Color.Transparent)
                )
            }
        }
    }
}
