package ru.mesh.foss.ui

import ru.mesh.foss.core.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Дизайн интерфейса. Хранится в настройке `design`:
 *  - [PLAIN] — стандартный Material 3 Expressive (M3E) с размытой нижней панелью;
 *  - [LIQUID] — «жидкое стекло»: лёгкие стеклянные карточки и контролы, а нижняя панель
 *    и анимации используют преломление `Kyant0/AndroidLiquidGlass`.
 */
object Design {
    const val PREF_KEY = "design"
    const val PLAIN = "plain"
    const val LIQUID = "liquid"
    const val DEFAULT = PLAIN

    /** Старый ключ переключателя (для безопасной миграции). */
    const val LEGACY_GLASS_KEY = "glass_enabled"

    fun resolve(savedDesign: String?, legacyGlassEnabled: Boolean? = null): String =
        if (savedDesign == LIQUID) LIQUID else PLAIN

    fun migrate(prefs: android.content.SharedPreferences): String =
        resolve(
            savedDesign = prefs.getString(PREF_KEY, null),
            legacyGlassEnabled = if (prefs.contains(LEGACY_GLASS_KEY)) prefs.getBoolean(LEGACY_GLASS_KEY, false) else null
        )
}

/** Текущий дизайн интерфейса. */
val LocalDesign = staticCompositionLocalOf { Design.PLAIN }

/** Переключение дизайна из настроек. */
val LocalDesignSetter = staticCompositionLocalOf<(String) -> Unit> { {} }

/* -------------------------------------------------------------------------- */
/* Общие стеклянные примитивы (M3E / Liquid Glass).                           */
/* -------------------------------------------------------------------------- */

/**
 * Панель «жидкого стекла» ([liquidGlass]) в стиле iOS без тяжёлых поэкранных шейдеров:
 * в [Design.LIQUID] — лёгкая стеклянная поверхность с зеркальной кромкой и бликом;
 * в [Design.PLAIN] (M3E) модификатор ничего не делает.
 */
@Composable
fun Modifier.glassPane(
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = MaterialTheme.colorScheme.surface
): Modifier {
    if (LocalDesign.current != Design.LIQUID) return this
    val scheme = MaterialTheme.colorScheme
    // Обычные нейтральные поверхности темы не перекрывают стекло.
    val isNeutralSurface = tint == Color.Unspecified ||
        tint == scheme.surface ||
        tint == scheme.surfaceContainerLowest ||
        tint == scheme.surfaceContainerLow ||
        tint == scheme.surfaceContainer ||
        tint == scheme.surfaceContainerHigh ||
        tint == scheme.surfaceContainerHighest
    return liquidGlass(shape, if (isNeutralSurface) Color.Unspecified else tint, refract = false)
}

/**
 * Подложка выделения (плитка темы и т.п.): в [Design.LIQUID] — панель «жидкого
 * стекла», иначе — обычная заливка [tint].
 */
@Composable
fun Modifier.selectionPane(shape: Shape, tint: Color): Modifier = when (LocalDesign.current) {
    Design.LIQUID -> glassPane(shape, tint)
    else -> Modifier.clip(shape).background(tint)
}

/**
 * Подложка диалога. В [Design.LIQUID] — панель «жидкого стекла»; в [Design.PLAIN]
 * — непрозрачный фон темы (диалоги задают прозрачный containerColor).
 */
@Composable
internal fun Modifier.dialogGlass(shape: Shape = MaterialTheme.shapes.extraLarge): Modifier {
    val solid = background(MaterialTheme.colorScheme.surface.copy(alpha = 1f), shape)
    return when (LocalDesign.current) {
        Design.LIQUID -> liquidDialogGlass(shape)
        else -> solid
    }
}

/**
 * Основная кнопка: в [Design.LIQUID] — настоящая капсула из `Kyant0/AndroidLiquidGlass`
 * (`LiquidButton`) с преломлением, вибрацией цвета и бликом под пальцем.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = Color.Unspecified,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalDesign.current == Design.LIQUID) {
        val accent = if (tint.isSpecified) tint else MaterialTheme.colorScheme.primary
        LiquidButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = if (shape == MaterialTheme.shapes.large) Liquid.ButtonShape else shape,
            tint = accent,
            contentPadding = contentPadding,
            content = content
        )
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = if (tint.isSpecified) {
                ButtonDefaults.buttonColors(containerColor = tint, contentColor = contentColorFor(tint))
            } else {
                ButtonDefaults.buttonColors()
            },
            contentPadding = contentPadding,
            content = content
        )
    }
}

/**
 * Прозрачная стеклянная кнопка (аналог `OutlinedButton`): в [Design.LIQUID] — чистое
 * преломляющее стекло `LiquidButton` без глухой обводки.
 */
@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalDesign.current == Design.LIQUID) {
        val dark = Liquid.isDark(MaterialTheme.colorScheme.background)
        LiquidButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = if (shape == MaterialTheme.shapes.large) Liquid.ButtonShape else shape,
            surfaceColor = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.34f),
            contentPadding = contentPadding,
            content = content
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            content = content
        )
    }
}

/**
 * Тональная кнопка (`FilledTonalButton`): в [Design.LIQUID] — приглушённое цветное стекло.
 */
@Composable
fun GlassTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalDesign.current == Design.LIQUID) {
        val scheme = MaterialTheme.colorScheme
        val dark = Liquid.isDark(scheme.background)
        LiquidButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = if (shape == MaterialTheme.shapes.large) Liquid.ButtonShape else shape,
            surfaceColor = scheme.secondaryContainer.copy(alpha = if (dark) 0.42f else 0.56f),
            contentColor = scheme.onSecondaryContainer,
            contentPadding = contentPadding,
            content = content
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            content = content
        )
    }
}

/**
 * Переключатель: в [Design.LIQUID] — `LiquidToggle` из каталога `Kyant0/AndroidLiquidGlass`
 * с физическим бегунком из преломляющего стекла.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalDesign.current == Design.LIQUID) {
        LiquidToggle(
            selected = checked,
            onSelect = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
    } else {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
    }
}

/**
 * Фильтр-чип: в [Design.LIQUID] — стеклянная капсула с акцентом на выбранном варианте.
 */
@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalDesign.current == Design.LIQUID) {
        val scheme = MaterialTheme.colorScheme
        val dark = Liquid.isDark(scheme.background)
        LiquidButton(
            onClick = onClick,
            modifier = modifier,
            height = 38.dp,
            tint = if (selected) scheme.primary else Color.Unspecified,
            surfaceColor = if (selected) Color.Unspecified else if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.30f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            contentColor = if (selected) Color.White else scheme.onSurface
        ) {
            label()
        }
    } else {
        FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier)
    }
}

/**
 * Круглая стеклянная кнопка с иконкой: в [Design.LIQUID] — стеклянная сфера `LiquidIconButton`.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    tonal: Boolean = true,
    size: Dp = 42.dp,
    content: @Composable () -> Unit
) {
    if (LocalDesign.current == Design.LIQUID) {
        val scheme = MaterialTheme.colorScheme
        val dark = Liquid.isDark(scheme.background)
        val surface = when {
            tint.isSpecified -> Color.Unspecified
            tonal -> scheme.secondaryContainer.copy(alpha = if (dark) 0.40f else 0.56f)
            else -> if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.34f)
        }
        LiquidIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            tint = tint,
            surfaceColor = surface,
            size = size,
            content = content
        )
    } else if (tint.isSpecified) {
        FilledIconButton(onClick = onClick, modifier = modifier.size(size), enabled = enabled, content = content)
    } else if (tonal) {
        FilledTonalIconButton(onClick = onClick, modifier = modifier.size(size), enabled = enabled, content = content)
    } else {
        OutlinedIconButton(onClick = onClick, modifier = modifier.size(size), enabled = enabled, content = content)
    }
}

/**
 * Карточка-панель. В [Design.LIQUID] — лёгкая панель «жидкого стекла»;
 * в [Design.PLAIN] (M3E) — обычная карточка Material 3 Expressive с цветом [tint].
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = Color.Unspecified,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (tint.isSpecified) tint else scheme.surface
    val container = if (tint.isSpecified) tint else scheme.surfaceContainerLow
    when (LocalDesign.current) {
        Design.LIQUID -> Card(
            modifier = modifier.glassPane(shape, fill),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent,
                contentColor = scheme.onSurface
            ),
            border = border,
            content = content
        )
        else -> Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = container,
                contentColor = scheme.onSurface
            ),
            border = border,
            content = content
        )
    }
}
