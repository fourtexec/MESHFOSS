@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.Words

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private val CdzTableCellWidth = 132.dp

/** `answer/table`: заголовки и ручной ввод в ячейки. */
@Composable
internal fun CdzTableAnswer(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
) {
    val table = remember(task.answer) { parseTable(task.answer) }
    if (table == null) {
        Text(
            text = Words.CdzUnsupportedType(task.type),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    var values by remember(task.id) { mutableStateOf(parseTableValues(answer)) }

    fun commit(next: Map<Pair<Int, Int>, String>) {
        values = next
        onChanged(tableAnswer(task.type, next))
    }

    CdzTableContent(
        table = table,
        values = values,
        editable = true,
        onValueChange = { row, column, value ->
            val next = values.toMutableMap()
            if (value.isBlank()) next.remove(row to column) else next[row to column] = value
            commit(next)
        },
    )
}

/** Таблица в просмотре результата: без полей ввода и с ответом ученика. */
@Composable
internal fun CdzTableResult(
    table: CdzTable?,
    values: Map<Pair<Int, Int>, String>,
    modifier: Modifier = Modifier,
) {
    if (table == null) {
        Text(
            text = Words.CdzAnswerUnavailable.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    CdzTableContent(
        table = table,
        values = values,
        editable = false,
        modifier = modifier,
    )
}

@Composable
private fun CdzTableContent(
    table: CdzTable,
    values: Map<Pair<Int, Int>, String>,
    editable: Boolean,
    onValueChange: ((Int, Int, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val tableWidth = CdzTableCellWidth * table.columns.toFloat()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
    ) {
        Column(modifier = Modifier.width(tableWidth)) {
            for (row in 0 until table.rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (column in 0 until table.columns) {
                        val header = row < table.headerRows || column < table.headerColumns
                        val text = table.value(row, column)
                        val input = editable && !header && table.isInput(row, column)
                        CdzTableCell(
                            text = values[row to column] ?: text,
                            header = header,
                            input = input,
                            onValueChange = { value ->
                                onValueChange?.invoke(row, column, value)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CdzTableCell(
    text: String,
    header: Boolean,
    input: Boolean,
    onValueChange: (String) -> Unit,
) {
    val background = if (header) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        color = background,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(CdzTableCellWidth)
            .heightIn(min = 52.dp),
    ) {
        if (input) {
            OutlinedTextField(
                value = text,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = text.ifBlank { if (header) "" else "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (header) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
