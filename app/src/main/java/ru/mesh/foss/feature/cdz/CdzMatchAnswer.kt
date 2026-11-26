@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.Words

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    LaunchedEffect(initial) {
        if (links != initial) links = LinkedHashMap(initial)
    }
    var selected by remember(taskId) { mutableStateOf<String?>(null) }
    var dragging by remember(taskId) { mutableStateOf<MatchDrag?>(null) }
    val rootCenters = remember(taskId) { mutableStateMapOf<String, Offset>() }
    val rootTops = remember(taskId) { mutableStateMapOf<String, Offset>() }
    var containerOrigin by remember(taskId) { mutableStateOf(Offset.Zero) }

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

    fun applyLinks(next: Map<String, String>) {
        links = LinkedHashMap(next)
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
        applyLinks(next)
    }

    /** Крестик на правом кружке связанной пары удаляет эту связь. */
    fun removeLink(nodeId: String) {
        val sourceId = links.entries
            .firstOrNull { it.key == nodeId || it.value == nodeId }
            ?.key
            ?: return
        val next = LinkedHashMap(links)
        next.remove(sourceId)
        applyLinks(next)
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
        // Не меняем выбранный элемент в момент начала протягивания: иначе
        // карточка/линия дёргаются при старте жеста после обычного тапа.
        dragging = centerOf(id)?.let { MatchDrag(id, it) }
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
                    onRemove = { id -> removeLink(id) },
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
                    onRemove = { id -> removeLink(id) },
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
    onRemove: (String) -> Unit,
    dragStart: (String) -> Unit,
    dragMove: (String, Offset) -> Unit,
    dragEnd: () -> Unit,
    dragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            val pairColor = colorForNode(option.id)
            val isSelected = selected == option.id
            val linked = isSelected || pairColor != null
            val corner by animateDpAsState(
                targetValue = if (linked) 22.dp else 16.dp,
                animationSpec = motion.defaultSpatialSpec(),
                label = "matchCorner",
            )
            val shape = RoundedCornerShape(corner)
            val container by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    pairColor != null -> pairColor.copy(alpha = 0.14f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = motion.defaultEffectsSpec(),
                label = "matchContainer",
            )
            val border by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    pairColor != null -> pairColor
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                animationSpec = motion.defaultEffectsSpec(),
                label = "matchBorder",
            )
            val borderWidth by animateDpAsState(
                targetValue = if (linked) 2.dp else 1.dp,
                animationSpec = motion.defaultSpatialSpec(),
                label = "matchBorderWidth",
            )
            val dotColor = pairColor
                ?: if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            // Крестик только у правой колонки: так связь можно снять, не трогая
            // карточку и не используя отдельные кнопки «Отменить/Очистить».
            val removable = !isSource && pairColor != null

            Surface(
                shape = shape,
                color = container,
                border = BorderStroke(borderWidth, border),
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        rootTops[option.id] = coordinates.positionInRoot()
                    }
                    .clip(shape)
                    .clickable { currentTap(option.id) }
                    .pointerInput(option.id) {
                        // Только горизонтальный жест: вертикальная прокрутка страницы
                        // остаётся рабочей, а линия тянется в сторону второй колонки.
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragStart(option.id)
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
                            removable = removable,
                            onClick = {
                                if (removable) onRemove(option.id) else currentTap(option.id)
                            },
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
                        CdzOptionMedia(option)
                        val label = option.label()
                        if (label.isNotBlank()) {
                            CdzRichText(
                                text = label,
                                spans = if (option.text.isNotBlank()) {
                                    option.textSpans
                                } else {
                                    emptyList()
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (isSource) {
                        MatchDot(
                            color = dotColor,
                            onClick = { currentTap(option.id) },
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
    removable: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(3.dp, color, CircleShape)
            .clickable(
                onClickLabel = if (removable) Words.CdzRemoveLink.text else null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (removable) {
            Canvas(Modifier.size(12.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(
                    color = color,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
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
