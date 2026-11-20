package ru.mesh.foss.app

import ru.mesh.foss.core.Words

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private data class DragCallbacks(
    val start: () -> Unit,
    val drag: (Float) -> Unit,
    val end: () -> Unit,
)

/**
 * Редактор нижней панели: две группы (на панели / в «Ещё»). Перетаскивание
 * меняет порядок внутри группы, кнопки ± переносят раздел между группами.
 *
 * Ничего не масштабируется: при перетаскивании двигается только строка целиком,
 * поэтому поведение совместимо с liquid glass и не ломает линзу таб-бара.
 */
@Composable
internal fun NavEditor(
    layout: NavLayout,
    onChange: (NavLayout) -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val current by rememberUpdatedState(layout)
    var dragGroup by remember { mutableStateOf<String?>(null) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }

    fun moveWithin(group: String, from: Int, to: Int) {
        val currentLayout = current
        val list = if (group == "bottom") currentLayout.bottom else currentLayout.more
        if (from !in list.indices || to !in list.indices || from == to) return
        val next = list.toMutableList().apply { add(to, removeAt(from)) }
        onChange(
            if (group == "bottom") currentLayout.copy(bottom = next)
            else currentLayout.copy(more = next)
        )
    }

    fun toMore(key: String) {
        val currentLayout = current
        if (key !in currentLayout.bottom) return
        onChange(
            currentLayout.copy(
                bottom = currentLayout.bottom.filter { it != key },
                more = currentLayout.more + key,
            )
        )
    }

    fun toBottom(key: String) {
        val currentLayout = current
        if (currentLayout.bottom.size >= NavTabs.MAX_BOTTOM || key !in currentLayout.more) return
        onChange(
            currentLayout.copy(
                bottom = currentLayout.bottom + key,
                more = currentLayout.more.filter { it != key },
            )
        )
    }

    fun handlers(group: String, index: Int): DragCallbacks {
        fun start() {
            dragGroup = group
            dragIndex = index
            dragDy = 0f
        }

        fun drag(dy: Float) {
            dragDy += dy
            val step = with(density) { 56.dp.toPx() }
            while (abs(dragDy) >= step && dragIndex >= 0) {
                val list = if (group == "bottom") current.bottom else current.more
                if (dragIndex !in list.indices) break
                val dir = if (dragDy > 0f) 1 else -1
                val target = (dragIndex + dir).coerceIn(0, list.lastIndex)
                if (target == dragIndex) {
                    dragDy = 0f
                    break
                }
                moveWithin(group, dragIndex, target)
                dragIndex = target
                dragDy -= dir * step
            }
        }

        fun end() {
            dragGroup = null
            dragIndex = -1
            dragDy = 0f
        }

        return DragCallbacks({ start() }, { dy -> drag(dy) }, { end() })
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Words.EditTabs.text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) { Text(Words.Done.text) }
        }
        Text(
            Words.DragHint.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(Words.OnBar.text, style = MaterialTheme.typography.labelLarge)
        current.bottom.forEachIndexed { index, key ->
            val h = handlers("bottom", index)
            // key() сохраняет composition (и жест) за разделом при перестановке.
            key(key) {
                NavEditRow(
                    label = NavTabs.label(key),
                    iconRes = NavTabs.icon(key),
                    actionLabel = "−",
                    canAction = true,
                    offset = if (dragGroup == "bottom" && dragIndex == index) dragDy else 0f,
                    onDragStart = h.start,
                    onDrag = h.drag,
                    onDragEnd = h.end,
                    onAction = { toMore(key) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(Words.InMore.text, style = MaterialTheme.typography.labelLarge)
        current.more.forEachIndexed { index, key ->
            val h = handlers("more", index)
            // key() сохраняет composition (и жест) за разделом при перестановке.
            key(key) {
                NavEditRow(
                    label = NavTabs.label(key),
                    iconRes = NavTabs.icon(key),
                    actionLabel = "+",
                    canAction = current.bottom.size < NavTabs.MAX_BOTTOM,
                    offset = if (dragGroup == "more" && dragIndex == index) dragDy else 0f,
                    onDragStart = h.start,
                    onDrag = h.drag,
                    onDragEnd = h.end,
                    onAction = { toBottom(key) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NavEditRow(
    label: String,
    iconRes: Int,
    actionLabel: String,
    canAction: Boolean,
    offset: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { translationY = offset },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .pointerInput(label) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, delta ->
                            onDrag(delta.y)
                            change.consume()
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("≡", style = MaterialTheme.typography.titleLarge)
        }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onAction, enabled = canAction) {
            Text(actionLabel, style = MaterialTheme.typography.titleLarge)
        }
    }
}
