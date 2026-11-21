package ru.mesh.foss.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private data class MatchNode(val id: String, val label: String, val source: Boolean)

private val PairColors = listOf(
    Color(0xFF4F8DF7), Color(0xFFEF6C7E), Color(0xFF39B98B),
    Color(0xFFB980F6), Color(0xFFF2A93B), Color(0xFF25B7C9)
)

/**
 * Сопоставление «верх → низ» по мотивам рабочего графового примера
 * Android By Example (startLine/endLine, findAt, линия рисуется в Canvas).
 *
 * Жест начинается на КРУГЕ верхнего ряда; палец тянет ЛИНИЮ к кругу нижнего
 * ряда; на отпускании ищется ближайший кружок радиуса [hitRadius]. Тап по
 * уже существующей линии (или по её кругу) удаляет пару.
 */
@Composable
fun CdzMatchScreen(
    answer: JSONObject,
    savedMatch: JSONObject,
    onChanged: (JSONObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = answer.optJSONArray("options")
    val sources = mutableListOf<MatchNode>()
    val targets = mutableListOf<MatchNode>()
    if (options != null) {
        for (i in 0 until options.length()) {
            val option = options.optJSONObject(i) ?: continue
            val label = option.optString("text").ifBlank {
                option.optJSONArray("content")?.let { content ->
                    (0 until content.length()).mapNotNull { content.optJSONObject(it) }
                        .firstOrNull { it.optString("atomic_type") == "image" }
                        ?.optString("description")
                }.orEmpty().ifBlank { "·" }
            }
            val node = MatchNode(option.optString("id"), label, option.optString("type") == "option_type/match/source")
            if (node.source) sources += node else targets += node
        }
    }

    var links by remember {
        mutableStateOf<LinkedHashMap<String, String>>(LinkedHashMap<String, String>().apply {
            savedMatch.keys().forEach { key ->
                val array = savedMatch.optJSONArray(key)
                if (array != null && array.length() > 0) put(key, array.optString(0))
            }
        })
    }
    // линия, которую тянет пользователь: верхний id + текущая точка пальца
    var pending by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    val centers = remember { mutableStateMapOf<String, Offset>() }
    val dragColor = MaterialTheme.colorScheme.primary
    val hitRadius = 90f

    fun publish(map: LinkedHashMap<String, String>) {
        links = map
        val json = JSONObject()
        map.forEach { (key, value) -> json.put(key, JSONArray().put(value)) }
        onChanged(json)
    }

    fun cancel() { pending = null }

    fun finish(sourceId: String, finger: Offset) {
        val target = targets.minByOrNull { id ->
            centers[id]?.let { (it - finger).getDistance() } ?: Float.MAX_VALUE
        }
        val center = target?.let { centers[it] }
        if (target != null && center != null && (center - finger).getDistance() <= hitRadius) {
            val map = LinkedHashMap(links)
            map.entries.removeIf { it.value == target.id }  // один target — одна пара
            map[sourceId] = target.id
            publish(map)
        }
        cancel()
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Соедините пары",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val map = LinkedHashMap(links)
                    if (map.isNotEmpty()) map.remove(map.keys.last())
                    publish(map); cancel()
                },
                enabled = links.isNotEmpty()
            ) { Text("Отменить") }
            TextButton(onClick = { publish(linkedMapOf()); cancel() }, enabled = links.isNotEmpty()) {
                Text("Очистить")
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 420.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            Canvas(Modifier.matchParentSize()) {
                links.entries.forEachIndexed { index, (sourceId, targetId) ->
                    val from = centers[sourceId] ?: return@forEachIndexed
                    val to = centers[targetId] ?: return@forEachIndexed
                    val path = Path().apply {
                        moveTo(from.x, from.y)
                        quadraticBezierTo((from.x + to.x) / 2, from.y + (to.y - from.y) / 2, to.x, to.y)
                    }
                    drawPath(path, PairColors[index % PairColors.size], style = Stroke(width = 7f))
                }
                pending?.let { (sourceId, finger) ->
                    val from = centers[sourceId] ?: return@let
                    val path = Path().apply {
                        moveTo(from.x, from.y)
                        quadraticBezierTo((from.x + finger.x) / 2, from.y, finger.x, finger.y)
                    }
                    drawPath(path, dragColor, style = Stroke(width = 5f))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MatchColumn(
                    nodes = sources,
                    links = links,
                    centers = centers,
                    onStart = { node, pos -> pending = node.id to pos },
                    onDrag = { pos -> pending?.let { pending = it.first to pos } },
                    onEnd = { pending?.let { finish(it.first, it.second) } },
                    onCancel = { cancel() },
                    onRemove = { node ->
                        val map = LinkedHashMap(links); map.remove(node.id); publish(map)
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(36.dp))
                MatchColumn(
                    nodes = targets,
                    links = links,
                    centers = centers,
                    onStart = { _, _ -> },
                    onDrag = { },
                    onEnd = { },
                    onCancel = { cancel() },
                    onRemove = { node ->
                        val map = LinkedHashMap(links)
                        map.entries.removeIf { it.value == node.id }
                        publish(map)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MatchColumn(
    nodes: List<MatchNode>,
    links: Map<String, String>,
    centers: MutableMap<String, Offset>,
    onStart: (MatchNode, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    onRemove: (MatchNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.forEach { node ->
            val index = links.entries.indexOfFirst { if (node.source) it.key == node.id else it.value == node.id }
            val tint by animateColorAsState(
                if (index >= 0) PairColors[index % PairColors.size]
                else MaterialTheme.colorScheme.outline
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.medium)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(tint)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .onGloballyPositioned { coords ->
                            val p = coords.positionInParent()
                            centers[node.id] = Offset(p.x + coords.size.width / 2f, p.y + coords.size.height / 2f)
                        }
                        .pointerInput(node.id) {
                            detectTapGestures(onTap = { if (index >= 0) onRemove(node) })
                        }
                        .pointerInput(node.id) {
                            detectDragGestures(
                                onDragStart = { },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (node.source) onStart(node, change.position - Offset.Zero)
                                    else onDrag(change.position)
                                },
                                onDragEnd = { onEnd() },
                                onDragCancel = { onCancel() }
                            )
                        }
                )
                Text(
                    node.label,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
