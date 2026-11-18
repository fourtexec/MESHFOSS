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

@Composable
fun GlassBackdrop(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background).drawWithCache {
        val light = Brush.radialGradient(
            listOf(scheme.primary.copy(alpha = .23f), Color.Transparent),
            Offset(size.width * .12f, size.height * .08f), size.maxDimension * .65f
        )
        val reflection = Brush.radialGradient(
            listOf(scheme.tertiary.copy(alpha = .16f), Color.Transparent),
            Offset(size.width, size.height * .83f), size.maxDimension * .56f
        )
        onDrawBehind {
            drawRect(light)
            drawRect(reflection)
        }
    }) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

@Composable
fun Modifier.glassPane(
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = MaterialTheme.colorScheme.surface
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val fill = Brush.linearGradient(listOf(
        tint.copy(alpha = tint.alpha * .92f),
        tint.copy(alpha = tint.alpha * .58f),
        tint.copy(alpha = tint.alpha * .82f)
    ))
    val edge = Brush.linearGradient(listOf(
        scheme.onSurface.copy(alpha = .32f),
        scheme.primary.copy(alpha = .09f),
        scheme.onSurface.copy(alpha = .18f)
    ))
    return clip(shape).background(fill).border(1.dp, edge, shape)
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
