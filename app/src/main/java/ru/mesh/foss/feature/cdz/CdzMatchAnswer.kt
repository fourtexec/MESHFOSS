@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.Words

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class MatchDrag(val id: String, val finger: Offset)

/**
 * `answer/match`: две колонки и линии между ними.
 *
 * Верхняя/левая колонка — source, нижняя/правая — target. Точки стоят на
 * внутренних краях карточек. Работают оба способа: тап по двум элементам и
 * протягивание линии от карточки к карточке. Соединить элементы из одной
 * колонки нельзя — пара всегда source → target.
 *
 * Координаты точек берутся в root-системе и вычитаются из root-позиции
 * контейнера, поэтому линии остаются на месте и при прокрутке страницы.
 */
@Composable
fun CdzMatchAnswer(
    taskId: Long,
    options: List<CdzOption>,
    initialMatch: Map<String, String>,
    onChanged: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources = remember(options) { options.filter { it.source } }
    val targets = remember(options) { options.filterNot { it.source } }
    val sourceIds = remember(sources) { sources.map { it.id }.toSet() }
    val targetIds = remember(targets) { targets.map { it.id }.toSet() }
    val initial = remember(taskId, initialMatch) {
        initialMatch.filterKeys { it in sourceIds }.filterValues { it in targetIds }
    }

    var links by remember(taskId) { mutableStateOf(LinkedHashMap(initial)) }
    var selected by remember(taskId) { mutableStateOf<String?>(null) }
    var dragging by remember(taskId) { mutableStateOf<MatchDrag?>(null) }
    val undo = remember(taskId) { mutableStateListOf<Map<String, String>>() }
    val rootCenters = remember { mutableStateMapOf<String, Offset>() }
    val rootTops = remember { mutableStateMapOf<String, Offset>() }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }

    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
    )
    val dragColor = MaterialTheme.colorScheme.primary
    val hitRadius = with(LocalDensity.current) { 64.dp.toPx() }

    fun centerOf(id: String): Offset? = rootCenters[id]?.minus(containerOrigin)

    fun colorOf(sourceId: String): Color {
        val index = sources.indexOfFirst { it.id == sourceId }
        return palette[(if (index < 0) 0 else index) % palette.size]
    }

    fun colorForNode(id: String): Color? {
        val source = links.entries.firstOrNull { it.key == id || it.value == id } ?: return null
        return colorOf(source.key)
    }

    fun publish(next: Map<String, String>) {
        if (undo.size >= 30) undo.removeAt(0)
        undo.add(LinkedHashMap(links))
        links = LinkedHashMap(next)
        selected = null
        dragging = null
        onChanged(links)
    }

    fun undoLast() {
        if (undo.isEmpty()) return
        val previous = undo.removeAt(undo.lastIndex)
        links = LinkedHashMap(previous)
        selected = null
        dragging = null
        onChanged(links)
    }

    fun link(first: String, second: String) {
        val firstIsSource = first in sourceIds
        val sourceId = if (firstIsSource) first else second
        val targetId = if (firstIsSource) second else first
        if (sourceId !in sourceIds || targetId !in targetIds) return
        val next = LinkedHashMap(links)
        // один target может быть только в одной паре
        next.entries.removeIf { it.value == targetId }
        next[sourceId] = targetId
        publish(next)
    }

    fun tap(id: String) {
        val current = selected
        when {
            current == null -> selected = id
            current == id -> selected = null
            // тап внутри одной колонки только меняет выбранный элемент
            (current in sourceIds) == (id in sourceIds) -> selected = id
            else -> link(current, id)
        }
    }

    fun startDrag(id: String) {
        selected = id
        centerOf(id)?.let { dragging = MatchDrag(id, it) }
    }

    fun moveDrag(id: String, fingerInCard: Offset) {
        val top = rootTops[id] ?: return
        val fingerRoot = top + fingerInCard
        dragging = MatchDrag(id, fingerRoot - containerOrigin)
    }

    fun endDrag() {
        val current = dragging ?: return
        dragging = null
        val fromSource = current.id in sourceIds
        val candidates = if (fromSource) targets else sources
        val nearest = candidates.minByOrNull { option ->
            val center = centerOf(option.id) ?: return@minByOrNull Float.MAX_VALUE
            (center - current.finger).getDistance()
        }
        val center = nearest?.let { centerOf(it.id) }
        if (nearest != null && center != null &&
            (center - current.finger).getDistance() <= hitRadius
        ) {
            link(current.id, nearest.id)
        } else {
            selected = current.id
        }
    }

    val currentTap by rememberUpdatedState<(String) -> Unit> { id -> tap(id) }
    val currentStartDrag by rememberUpdatedState<(String) -> Unit> { id -> startDrag(id) }
    val currentMoveDrag by rememberUpdatedState<(String, Offset) -> Unit> { id, offset ->
        moveDrag(id, offset)
    }
    val currentEndDrag by rememberUpdatedState<() -> Unit> { endDrag() }
    val currentCancelDrag by rememberUpdatedState<() -> Unit> { dragging = null }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Words.CdzMatchLinked(links.size, minOf(sources.size, targets.size)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = ::undoLast, enabled = undo.isNotEmpty()) {
                Text(Words.Undo.text)
            }
            TextButton(
                onClick = { publish(emptyMap()) },
                enabled = links.isNotEmpty(),
            ) {
                Text(Words.Clear.text)
            }
        }
        Text(
            text = Words.CdzMatchHint.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    if (containerOrigin != position) containerOrigin = position
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                MatchColumn(
                    options = sources,
                    links = links,
                    selected = selected,
                    isSource = true,
                    rootCenters = rootCenters,
                    rootTops = rootTops,
                    colorForNode = { id -> colorForNode(id) },
                    currentTap = { currentTap(it) },
                    dragStart = { currentStartDrag(it) },
                    dragMove = { id, offset -> currentMoveDrag(id, offset) },
                    dragEnd = { currentEndDrag() },
                    dragCancel = { currentCancelDrag() },
                    modifier = Modifier.weight(1f),
                )
                MatchColumn(
                    options = targets,
                    links = links,
                    selected = selected,
                    isSource = false,
                    rootCenters = rootCenters,
                    rootTops = rootTops,
                    colorForNode = { id -> colorForNode(id) },
                    currentTap = { currentTap(it) },
                    dragStart = { currentStartDrag(it) },
                    dragMove = { id, offset -> currentMoveDrag(id, offset) },
                    dragEnd = { currentEndDrag() },
                    dragCancel = { currentCancelDrag() },
                    modifier = Modifier.weight(1f),
                )
            }
            // Линии рисуются поверх карточек: иначе короткий участок от точки
            // до края карточки прячется под Surface и связь выглядит разорванной.
            Canvas(Modifier.matchParentSize()) {
                links.forEach { (sourceId, targetId) ->
                    val from = centerOf(sourceId) ?: return@forEach
                    val to = centerOf(targetId) ?: return@forEach
                    drawConnector(from, to, colorOf(sourceId), 5.dp.toPx())
                }
                dragging?.let { current ->
                    val from = centerOf(current.id) ?: return@let
                    drawConnector(from, current.finger, dragColor, 4.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun MatchColumn(
    options: List<CdzOption>,
    links: Map<String, String>,
    selected: String?,
    isSource: Boolean,
    rootCenters: MutableMap<String, Offset>,
    rootTops: MutableMap<String, Offset>,
    colorForNode: (String) -> Color?,
    currentTap: (String) -> Unit,
    dragStart: (String) -> Unit,
    dragMove: (String, Offset) -> Unit,
    dragEnd: () -> Unit,
    dragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            val pairColor = colorForNode(option.id)
            val isSelected = selected == option.id
            val container by animateColorAsState(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    pairColor != null -> pairColor.copy(alpha = 0.14f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            val border by animateColorAsState(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    pairColor != null -> pairColor
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            )
            val dotColor = pairColor
                ?: if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = container,
                border = BorderStroke(if (isSelected || pairColor != null) 2.dp else 1.dp, border),
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        rootTops[option.id] = coordinates.positionInRoot()
                    }
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { currentTap(option.id) }
                    .pointerInput(option.id) {
                        // Только горизонтальный жест: вертикальная прокрутка страницы
                        // остаётся рабочей, а линия тянется в сторону второй колонки.
                        detectHorizontalDragGestures(
                            onDragStart = { start: Offset ->
                                dragStart(option.id)
                                dragMove(option.id, start)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                dragMove(option.id, change.position)
                            },
                            onDragEnd = { dragEnd() },
                            onDragCancel = { dragCancel() },
                        )
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!isSource) {
                        MatchDot(
                            color = dotColor,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                rootCenters[option.id] = coordinates.positionInRoot() +
                                    Offset(
                                        coordinates.size.width / 2f,
                                        coordinates.size.height / 2f,
                                    )
                            },
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CdzRemoteImage(
                            url = option.imageUrl,
                            description = option.description,
                        )
                        Text(
                            text = option.text.ifBlank {
                                option.description.ifBlank { Words.Option.text }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (isSource) {
                        MatchDot(
                            color = dotColor,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                rootCenters[option.id] = coordinates.positionInRoot() +
                                    Offset(
                                        coordinates.size.width / 2f,
                                        coordinates.size.height / 2f,
                                    )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(3.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

private fun DrawScope.drawConnector(
    from: Offset,
    to: Offset,
    color: Color,
    width: Float,
) {
    val middleX = (from.x + to.x) / 2f
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(middleX, from.y, middleX, to.y, to.x, to.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
    drawCircle(color = color, radius = width, center = from)
    drawCircle(color = color, radius = width, center = to)
}
