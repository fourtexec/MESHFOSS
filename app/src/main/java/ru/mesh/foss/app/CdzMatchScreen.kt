package ru.mesh.foss.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private data class MatchNode(
    val id: String,
    val label: String,
    val source: Boolean,
    val order: Int,
)

private val PairColors = listOf(
    Color(0xFF4F8DF7), Color(0xFFEF6C7E), Color(0xFF39B98B),
    Color(0xFFB980F6), Color(0xFFF2A93B), Color(0xFF25B7C9)
)

/**
 * Красивое сопоставление: верхний и нижний ряды кругов, цветные кривые между
 * ними, drag от круга к кругу ИЛИ выбор «сначала верх, потом низ»/наоборот.
 * Совместимо со всеми дизайнами — цвета берутся из MaterialTheme.
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
            val node = MatchNode(
                id = option.optString("id"),
                label = option.optString("text").ifBlank {
                    option.optJSONArray("content")?.let { content ->
                        (0 until content.length()).mapNotNull { content.optJSONObject(it) }
                            .firstOrNull { it.optString("atomic_type") == "image" }
                            ?.optString("description")
                    }.orEmpty().ifBlank { "·" }
                },
                source = option.optString("type") == "option_type/match/source",
                order = i,
            )
            if (node.source) sources += node else targets += node
        }
    }

    var links by remember { mutableStateOf<LinkedHashMap<String, String>>(
        LinkedHashMap<String, String>().apply {
            savedMatch.keys().forEach { key ->
                val array = savedMatch.optJSONArray(key)
                if (array != null && array.length() > 0) put(key, array.optString(0))
            }
        }
    ) }
    var selected by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var drag by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    val nodeCenters = remember { mutableStateMapOf<String, Offset>() }
    val palette = PairColors
    val dragColor = MaterialTheme.colorScheme.primary

    fun commit(first: Pair<Boolean, String>, second: Pair<Boolean, String>) {
        val (sourceId, targetId) = if (first.first) first.second to second.second
        else second.second to first.second
        if (first.first == second.first) return
        // один target можно использовать один раз
        val updated = LinkedHashMap(links)
        updated.entries.removeIf { it.value == targetId }
        updated[sourceId] = targetId
        links = updated
        val json = JSONObject()
        updated.forEach { (key, value) -> json.put(key, JSONArray().put(value)) }
        onChanged(json)
        selected = null
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            text = "Соедините пары",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                links.entries.forEachIndexed { index, (sourceId, targetId) ->
                    val from = nodeCenters[sourceId] ?: return@forEachIndexed
                    val to = nodeCenters[targetId] ?: return@forEachIndexed
                    val midX = (from.x + to.x) / 2f
                    val path = Path().apply {
                        moveTo(from.x, from.y)
                        quadraticBezierTo(midX, from.y + (to.y - from.y) / 2f, to.x, to.y)
                    }
                    drawPath(path, palette[index % palette.size], style = Stroke(width = 6f))
                }
                drag?.let { (from, to) ->
                    val path = Path().apply {
                        moveTo(from.x, from.y)
                        quadraticBezierTo((from.x + to.x) / 2, (from.y + to.y) / 2, to.x, to.y)
                    }
                    drawPath(path, dragColor, style = Stroke(width = 4f))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MatchRow(
                    nodes = sources,
                    links = links,
                    selected = selected,
                    nodeCenters = nodeCenters,
                    onTap = { node ->
                        val current = selected
                        if (current == null) selected = true to node.id
                        else if (current.first) selected = true to node.id
                        else commit(current, true to node.id)
                    },
                    onDragStart = { node -> selected = true to node.id },
                    onDrag = { node, start, amount ->
                        drag = start to (start + amount)
                        val nearest = targets.minByOrNull {
                            val center = nodeCenters[it.id] ?: return@minByOrNull Float.MAX_VALUE
                            (center - (start + amount)).getDistance()
                        }
                        if (nearest != null) {
                            val center = nodeCenters[nearest.id]
                            if (center != null && (center - (start + amount)).getDistance() < 80f) {
                                commit(true to node.id, false to nearest.id)
                                drag = null
                            }
                        }
                    },
                    onDragEnd = { drag = null },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(32.dp))
                MatchRow(
                    nodes = targets,
                    links = links,
                    selected = selected,
                    nodeCenters = nodeCenters,
                    onTap = { node ->
                        val current = selected
                        if (current == null) selected = false to node.id
                        else commit(current, false to node.id)
                    },
                    onDragStart = { node -> selected = false to node.id },
                    onDrag = { node, start, amount -> drag = start to (start + amount) },
                    onDragEnd = { drag = null },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MatchRow(
    nodes: List<MatchNode>,
    links: Map<String, String>,
    selected: Pair<Boolean, String>?,
    nodeCenters: MutableMap<String, Offset>,
    onTap: (MatchNode) -> Unit,
    onDragStart: (MatchNode) -> Unit,
    onDrag: (MatchNode, Offset, Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        nodes.forEach { node ->
            val colorIndex = links.entries.indexOfFirst {
                if (node.source) it.key == node.id else it.value == node.id
            }
            val container by animateColorAsState(
                if (selected?.second == node.id) MaterialTheme.colorScheme.primaryContainer
                else if (colorIndex >= 0) PairColors[colorIndex % PairColors.size].copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            val elevation by animateDpAsState(if (selected?.second == node.id) 6.dp else 2.dp)
            Card(
                colors = CardDefaults.cardColors(containerColor = container),
                elevation = CardDefaults.cardElevation(elevation),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        nodeCenters[node.id] = Offset(p.x + coords.size.width / 2f, p.y + coords.size.height / 2f)
                    }
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart(node) },
                            onDrag = { change, amount -> change.consume(); onDrag(node, change.position - amount, amount) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
                    .padding(4.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .padding(12.dp)
                        .clickable { onTap(node) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (colorIndex >= 0) PairColors[colorIndex % PairColors.size]
                                else MaterialTheme.colorScheme.outline
                            )
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
}
