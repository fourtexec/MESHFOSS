@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.Words

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private val CdzGridCellWidth = 132.dp
private val CdzGridCellHeight = 64.dp

/** `answer/grid`: таблица V2 с заголовками и выбором варианта в ячейках. */
@Composable
internal fun CdzGridAnswer(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
) {
    val grid = remember(task.answer) { parseGrid(task.answer) }
    if (grid == null) {
        Text(
            text = Words.CdzUnsupportedType(task.type),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    var values by remember(task.id) { mutableStateOf(parseGridValues(answer)) }

    fun commit(next: Map<Pair<Int, Int>, CdzGridValue>) {
        values = next
        onChanged(gridAnswer(task.type, next, grid))
    }

    CdzGridContent(
        grid = grid,
        values = values,
        editable = true,
    ) { key, value ->
        val next = values.toMutableMap()
        if (value.optionIds.isEmpty() && value.manual.isBlank()) {
            next.remove(key)
        } else {
            next[key] = value
        }
        commit(next)
    }
}

/** Сетка в просмотре результата: заголовки и выбранные учеником значения. */
@Composable
internal fun CdzGridResult(
    grid: CdzGrid?,
    values: Map<Pair<Int, Int>, CdzGridValue>,
    modifier: Modifier = Modifier,
) {
    if (grid == null) {
        Text(
            text = Words.CdzAnswerUnavailable.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    CdzGridContent(
        grid = grid,
        values = values,
        editable = false,
        modifier = modifier,
    )
}

@Composable
private fun CdzGridContent(
    grid: CdzGrid,
    values: Map<Pair<Int, Int>, CdzGridValue>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    onChanged: ((Pair<Int, Int>, CdzGridValue) -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columnCount = grid.columns.size.coerceAtLeast(1)
        val cellWidth = maxOf(CdzGridCellWidth, maxWidth / columnCount.toFloat())
        val tableWidth = cellWidth * columnCount.toFloat()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
        ) {
            Column(
                modifier = Modifier
                    .width(tableWidth)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                grid.rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        grid.columns.forEach { column ->
                            val cell = grid.cell(row, column)
                            CdzGridCellView(
                                cell = cell,
                                value = values[row to column],
                                editable = editable,
                                width = cellWidth,
                                onChanged = { value ->
                                    onChanged?.invoke(row to column, value)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CdzGridCellView(
    cell: CdzGridCell?,
    value: CdzGridValue?,
    editable: Boolean,
    width: Dp,
    onChanged: (CdzGridValue) -> Unit,
) {
    val input = editable && cell?.input == true
    val header = cell?.header == true
    val background = when {
        header -> MaterialTheme.colorScheme.surfaceContainerHigh
        input -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        color = background,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(width)
            .height(CdzGridCellHeight),
    ) {
        when {
            input && cell != null && cell.options.isNotEmpty() ->
                CdzGridChoiceCell(cell = cell, value = value, onChanged = onChanged)
            input && cell != null ->
                CdzGridManualCell(value = value, onChanged = onChanged)
            else ->
                CdzGridReadOnlyCell(cell = cell, value = value)
        }
    }
}

@Composable
private fun CdzGridChoiceCell(
    cell: CdzGridCell,
    value: CdzGridValue?,
    onChanged: (CdzGridValue) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIds = value?.optionIds.orEmpty()
    val selected = cell.options.filter { it.id in selectedIds }
    val label = selected.map { it.label() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { expanded = true }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label.ifBlank { Words.CdzChoose.text },
                style = MaterialTheme.typography.bodyMedium,
                color = if (label.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾", style = MaterialTheme.typography.labelSmall)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            cell.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (cell.multiple) {
                                Checkbox(
                                    checked = option.id in selectedIds,
                                    onCheckedChange = null,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CdzOptionMedia(option)
                                val optionLabel = option.label()
                                if (optionLabel.isNotBlank()) {
                                    CdzRichText(
                                        text = optionLabel,
                                        spans = option.textSpans,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (cell.multiple) {
                            val ids = selectedIds.toMutableList()
                            if (!ids.remove(option.id)) ids += option.id
                            onChanged(CdzGridValue(optionIds = ids))
                        } else {
                            onChanged(CdzGridValue(optionIds = listOf(option.id)))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CdzGridManualCell(
    value: CdzGridValue?,
    onChanged: (CdzGridValue) -> Unit,
) {
    val text = value?.manual.orEmpty()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { next -> onChanged(CdzGridValue(manual = next)) },
            singleLine = false,
            maxLines = 2,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (text.isBlank()) {
                        Text(
                            text = Words.YourAnswer.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun CdzGridReadOnlyCell(
    cell: CdzGridCell?,
    value: CdzGridValue?,
) {
    if (cell == null) return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (cell.imageUrl.isNotBlank()) {
            CdzRemoteImage(
                url = cell.imageUrl,
                description = cell.imageDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            )
        }

        if (cell.header) {
            if (cell.text.isNotBlank()) {
                CdzRichText(
                    text = cell.text,
                    spans = cell.textSpans,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (value != null) {
            val selected = cell.options.filter { it.id in value.optionIds }
            if (selected.isNotEmpty()) {
                selected.forEach { option ->
                    val label = option.label()
                    if (label.isNotBlank()) {
                        CdzRichText(
                            text = label,
                            spans = option.textSpans,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else if (value.manual.isNotBlank()) {
                Text(
                    text = value.manual,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (value.optionIds.isNotEmpty()) {
                Text(
                    text = Words.CdzAnswerUnavailable.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
