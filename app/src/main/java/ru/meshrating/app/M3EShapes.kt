@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.meshrating.app

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * M3E-морфинг форм: клип по анимируемой интерполяции двух полигонов [MaterialShapes].
 *
 * Прогресс 0..1 анимируется снаружи (`animateFloatAsState` со spatial-спекой
 * motionScheme): 0 — форма [start], 1 — форма [end]. Морфированный путь
 * пересчитывает собственные tight-границы на каждом кадре и масштабируется
 * ровно под размер компонента, поэтому фон всегда заполняет карточку целиком.
 */
class MaterialMorphShape(
    private val start: RoundedPolygon,
    private val end: RoundedPolygon,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        Morph(start, end).toPath(progress.coerceIn(0f, 1f), path)
        val bounds = path.getBounds()
        val sx = if (bounds.width > 0f) size.width / bounds.width else 1f
        val sy = if (bounds.height > 0f) size.height / bounds.height else 1f
        path.transform(Matrix().apply { scale(x = sx, y = sy) })
        path.translate(Offset(-bounds.left * sx, -bounds.top * sy))
        return Outline.Generic(path)
    }
}
