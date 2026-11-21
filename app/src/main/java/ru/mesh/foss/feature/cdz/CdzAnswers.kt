@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.Words

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Отрисовка ответа по типу задания. Каждый тип — отдельный небольшой
 * Composable; состояние ответа остаётся в [CdzNativeViewModel], сюда приходит
 * только текущий черновик и колбэк сохранения.
 */
@Composable
fun CdzAnswerContent(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (task.type) {
            "answer/single", "answer/multiple" ->
                ChoiceAnswer(task, answer, onChanged)
            "answer/match" -> {
                val options = remember(task.answer) { parseOptions(task.answer) }
                if (options.isEmpty()) {
                    Text(
                        text = Words.CdzMatchEmpty.text,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    CdzMatchAnswer(
                        taskId = task.id,
                        options = options,
                        initialMatch = parseMatch(answer),
                        onChanged = { pairs -> onChanged(matchAnswer(pairs)) },
                    )
                }
            }
            "answer/number" ->
                TextAnswer(task, answer, onChanged, KeyboardType.Decimal)
            "answer/number/interval" ->
                TextAnswer(task, answer, onChanged, KeyboardType.Text)
            "answer/string", "answer/string/multiple", "answer/free" ->
                TextAnswer(task, answer, onChanged, KeyboardType.Text)
            "answer/order" -> OrderAnswer(task, answer, onChanged)
            "answer/groups" -> GroupsAnswer(task, answer, onChanged)
            "answer/table" -> CdzTableAnswer(task, answer, onChanged)
            "answer/grid" -> GridAnswer(task, answer, onChanged)
            "answer/inline/choice/single" -> Text(
                text = Words.CdzChoose.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> UnsupportedAnswer(task, answer, onChanged)
        }
    }
}

@Composable
private fun ChoiceAnswer(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
) {
    val options = remember(task.answer) { parseOptions(task.answer) }
    val selected = selectedOptionIds(answer)
    val multiple = task.type == "answer/multiple"

    fun choose(id: String) {
        if (multiple) {
            val ids = selected.toMutableSet()
            if (!ids.add(id)) ids.remove(id)
            onChanged(
                if (ids.isEmpty()) JSONObject()
                else JSONObject()
                    .put("@answer_type", task.type)
                    .put("ids", ids.toJsonArray())
            )
        } else {
            onChanged(JSONObject().put("@answer_type", task.type).put("id", id))
        }
    }

    options.forEach { option ->
        val isSelected = option.id in selected
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (isSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { choose(option.id) },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (multiple) {
                    Checkbox(checked = isSelected, onCheckedChange = null)
                } else {
                    RadioButton(selected = isSelected, onClick = null)
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
                            spans = option.labelSpans(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextAnswer(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
    keyboardType: KeyboardType,
) {
    var value by remember(task.id) { mutableStateOf(textAnswer(answer)) }

    fun emit(next: String) {
        if (next.isBlank()) {
            onChanged(JSONObject())
            return
        }
        val body = when (task.type) {
            "answer/free" -> JSONObject()
                .put("@answer_type", task.type)
                .put("string", next)
                .put("files", JSONArray())
            "answer/number", "answer/number/interval" -> JSONObject()
                .put("@answer_type", task.type)
                .put(
                    "number",
                    if (task.type == "answer/number") {
                        // API ждёт массив чисел: number:[42], а не number:["42"].
                        val number = next.toDoubleOrNull()
                        JSONArray().put(
                            when {
                                number == null -> next
                                number % 1.0 == 0.0 -> number.toLong()
                                else -> number
                            }
                        )
                    } else {
                        next
                    }
                )
            else -> JSONObject()
                .put("@answer_type", task.type)
                .put("string", JSONArray().put(next))
                .put("caseSensitive", false)
        }
        onChanged(body)
    }

    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            value = next
            emit(next.trim())
        },
        label = { Text(Words.YourAnswer.text) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = false,
        minLines = 1,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OrderAnswer(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
) {
    val options = remember(task.answer) { parseOptions(task.answer) }
    var order by remember(task.id) { mutableStateOf<List<String>>(orderIds(answer, options)) }

    fun commit(next: List<String>) {
        order = next
        onChanged(
            JSONObject()
                .put("@answer_type", task.type)
                .put("ids_order", next.toJsonArray())
        )
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in order.indices) return
        val next = order.toMutableList()
        val swap = next[index]
        next[index] = next[target]
        next[target] = swap
        commit(next)
    }

    order.forEachIndexed { index, id ->
        val option = options.firstOrNull { it.id == id } ?: return@forEachIndexed
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CdzOptionMedia(option)
                    val label = option.label()
                    if (label.isNotBlank()) {
                        CdzRichText(
                            text = label,
                            spans = option.labelSpans(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                TextButton(onClick = { move(index, -1) }, enabled = index > 0) { Text("↑") }
                TextButton(
                    onClick = { move(index, 1) },
                    enabled = index < order.lastIndex,
                ) { Text("↓") }
            }
        }
    }
}

@Composable
private fun GroupsAnswer(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
) {
    val options = remember(task.answer) { parseOptions(task.answer) }
    val groupHeaders = remember(options) { options.filter { it.isGroup } }
    val itemOptions = remember(options) { options.filterNot { it.isGroup } }
    val groups = if (groupHeaders.isEmpty()) {
        listOf("" to "")
    } else {
        groupHeaders.map { it.id to it.label() }
    }

    val initial = remember(task.id) {
        val selected = LinkedHashMap<String, MutableSet<String>>()
        val groups = answer.optJSONArray("groups")
        if (groups != null) {
            for (i in 0 until groups.length()) {
                val group = groups.optJSONObject(i) ?: continue
                val id = group.optString("group_id")
                val ids = group.optJSONArray("options_ids") ?: continue
                selected[id] = (0 until ids.length())
                    .map { ids.optString(it) }
                    .filter { it.isNotBlank() }
                    .toMutableSet()
            }
        }
        selected
    }
    var selection by remember(task.id) {
        mutableStateOf<Map<String, MutableSet<String>>>(initial)
    }

    fun commit() {
        val out = JSONArray()
        selection.forEach { (groupId, ids) ->
            if (ids.isNotEmpty()) {
                out.put(
                    JSONObject()
                        .put("group_id", groupId)
                        .put("options_ids", ids.toJsonArray())
                )
            }
        }
        onChanged(
            if (out.length() == 0) JSONObject()
            else JSONObject()
                .put("@answer_type", task.type)
                .put("groups", out)
        )
    }

    groups.forEachIndexed { groupIndex, group ->
        val groupId = group.first
        val groupLabel = group.second
        if (itemOptions.isEmpty()) return@forEachIndexed
        Text(
            text = groupLabel.ifBlank { Words.CdzGroup(groupIndex + 1) },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = if (groupIndex == 0) 0.dp else 8.dp),
        )
        itemOptions.forEach { option ->
            val checked = selection[groupId]?.contains(option.id) == true
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (checked) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        val next = selection.mapValues { (_, ids) -> ids.toMutableSet() }
                            .toMutableMap()
                        // Элемент относится к одной группе: переносим его,
                        // а не дублируем в нескольких.
                        next.values.forEach { ids -> ids.remove(option.id) }
                        if (!checked) {
                            next.getOrPut(groupId) { mutableSetOf() }.add(option.id)
                        }
                        selection = next
                        commit()
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CdzOptionMedia(option)
                        val label = option.label()
                        if (label.isNotBlank()) {
                            CdzRichText(
                                text = label,
                                spans = option.labelSpans(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
        if (groupIndex < groups.lastIndex) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun GridAnswer(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
) {
    val cells = answer.optJSONArray("cells")
    val count = maxOf(3, cells?.length() ?: 0)
    var values by remember(task.id) {
        mutableStateOf(
            List(count) { index -> cells?.optJSONObject(index)?.optString("answer").orEmpty() }
        )
    }

    fun commit(next: List<String>) {
        values = next
        if (next.all { it.isBlank() }) {
            onChanged(JSONObject())
            return
        }
        val out = JSONArray()
        next.forEach { cell ->
            out.put(JSONObject().put("input_type", "MANUAL_INPUT").put("answer", cell))
        }
        onChanged(JSONObject().put("@answer_type", task.type).put("cells", out))
    }

    values.forEachIndexed { index, value ->
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                val copy = values.toMutableList()
                copy[index] = next
                commit(copy)
            },
            label = { Text(Words.CdzCell(index + 1)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UnsupportedAnswer(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
) {
    Text(
        text = Words.CdzUnsupportedType(task.type),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    var value by remember(task.id) { mutableStateOf(textAnswer(answer)) }
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            value = next
            if (next.isBlank()) {
                onChanged(JSONObject())
            } else {
                onChanged(
                    JSONObject()
                        .put("@answer_type", task.type)
                        .put("string", JSONArray().put(next.trim()))
                )
            }
        },
        label = { Text(Words.YourAnswer.text) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun CdzOptionMedia(option: CdzOption) {
    CdzRemoteImage(option.imageUrl, option.description)
    val video = option.video
    if (video != null) CdzRemoteVideo(video)
}

internal fun CdzOption.label(): String =
    text.ifBlank {
        description.ifBlank {
            if (imageUrl.isNotBlank() || video != null) "" else Words.Option.text
        }
    }

private fun CdzOption.labelSpans(): List<CdzTextSpan> =
    if (text.isNotBlank()) textSpans else emptyList()

private fun Collection<String>.toJsonArray(): JSONArray {
    val result = JSONArray()
    forEach { result.put(it) }
    return result
}
