package ru.mesh.foss

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private class GlassLight {
    var touch by mutableStateOf<Offset?>(null)
}

private val LocalGlassLight = staticCompositionLocalOf<GlassLight?> { null }

@Composable
fun GlassBackdrop(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val lightSource = remember { GlassLight() }
    Box(Modifier.fillMaxSize().pointerInput(lightSource) {
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
    }.background(scheme.background).drawWithCache {
        val light = Brush.radialGradient(
            listOf(scheme.primary.copy(alpha = .36f), Color.Transparent),
            Offset(size.width * .12f, size.height * .08f), size.maxDimension * .65f
        )
        val reflection = Brush.radialGradient(
            listOf(scheme.tertiary.copy(alpha = .24f), Color.Transparent),
            Offset(size.width, size.height * .83f), size.maxDimension * .56f
        )
        onDrawBehind {
            drawRect(light)
            drawRect(reflection)
        }
    }) {
        CompositionLocalProvider(LocalGlassLight provides lightSource, LocalContentColor provides scheme.onBackground, content = content)
    }
}

@Composable
fun Modifier.glassPane(
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = MaterialTheme.colorScheme.surface
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val fill = Brush.linearGradient(listOf(
        tint.copy(alpha = tint.alpha * .85f),
        tint.copy(alpha = tint.alpha * .48f),
        tint.copy(alpha = tint.alpha * .76f)
    ))
    val edge = Brush.linearGradient(
        0f to Color.White.copy(alpha = .62f),
        .32f to scheme.primary.copy(alpha = .12f),
        .64f to scheme.primary.copy(alpha = .06f),
        1f to scheme.primary.copy(alpha = .48f)
    )
    val lightSource = LocalGlassLight.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInRoot() }.clip(shape).background(fill).drawWithCache {
        val sheen = Brush.radialGradient(
            listOf(Color.White.copy(alpha = .13f), Color.Transparent),
            center = Offset(size.width * .16f, -size.height * .3f),
            radius = (size.width * .78f).coerceAtLeast(1f)
        )
        // The same light passes across adjacent panes without intercepting taps or scrolls.
        val radius = 112.dp.toPx()
        val glint = Brush.radialGradient(
            listOf(Color.White.copy(alpha = .16f), Color.Transparent), Offset.Zero, radius
        )
        onDrawBehind {
            drawRect(sheen)
            lightSource?.touch?.let { touch ->
                val center = touch - origin
                drawContext.transform.translate(center.x, center.y)
                drawCircle(glint, radius, Offset.Zero)
                drawContext.transform.translate(-center.x, -center.y)
            }
        }
    }.border(1.dp, edge, shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.glassPane(shape, tint),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface),
        border = border,
        content = content
    )
}
