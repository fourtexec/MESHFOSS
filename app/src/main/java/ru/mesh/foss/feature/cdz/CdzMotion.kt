@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.cdz

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * M3E-переход между заданиями: лёгкий сдвиг в сторону свайпа и fade.
 *
 * Спеки берутся из [MotionScheme], а не из фиксированного tween, поэтому
 * переходы совпадают по темпу с остальным приложением.
 */
internal fun cdzTaskTransition(motion: MotionScheme, direction: Int): ContentTransform {
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val effects = motion.defaultEffectsSpec<Float>()
    val forward = direction >= 0
    val enter = slideInHorizontally(animationSpec = spatial) { width ->
        if (forward) width / 4 else -width / 4
    } + fadeIn(animationSpec = effects)
    val exit = slideOutHorizontally(animationSpec = spatial) { width ->
        if (forward) -width / 4 else width / 4
    } + fadeOut(animationSpec = effects)
    return enter togetherWith exit
}

/** M3E-переход между состояниями экрана: загрузка → выбор попытки → задания. */
internal fun cdzPhaseTransition(motion: MotionScheme): ContentTransform {
    val spatial = motion.defaultSpatialSpec<Float>()
    val effects = motion.defaultEffectsSpec<Float>()
    val enter = fadeIn(animationSpec = effects) +
        scaleIn(animationSpec = spatial, initialScale = 0.94f)
    val exit = fadeOut(animationSpec = effects) +
        scaleOut(animationSpec = spatial, targetScale = 0.98f)
    return enter togetherWith exit
}

/**
 * Горизонтальный свайп по телу задания: влево — следующее, вправо — предыдущее.
 *
 * Жест висит на контейнере с verticalScroll и срабатывает только после
 * горизонтального touch slop. Вложенные горизонтальные жесты (сопоставление,
 * перетаскивание линий) потребляют событие раньше и остаются нетронутыми.
 */
internal fun Modifier.swipeCdzTasks(
    onPrev: () -> Unit,
    onNext: () -> Unit,
): Modifier = composed {
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)
    pointerInput(Unit) {
        var total = 0f
        var fired = false
        val threshold = 56.dp.toPx()
        detectHorizontalDragGestures(
            onDragEnd = {
                total = 0f
                fired = false
            },
            onDragCancel = {
                total = 0f
                fired = false
            },
        ) { change, dragAmount ->
            change.consume()
            if (fired) return@detectHorizontalDragGestures
            total += dragAmount
            when {
                abs(total) < threshold -> Unit
                total < 0f -> {
                    fired = true
                    next()
                }
                else -> {
                    fired = true
                    prev()
                }
            }
        }
    }
}
