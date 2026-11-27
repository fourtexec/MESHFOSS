@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.marks

import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.model.MarkHistoryEntry
import ru.mesh.foss.ui.GradeBadge
import ru.mesh.foss.ui.formatMark
import ru.mesh.foss.ui.formatWeight

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val MARK_DATE_TIME = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?""")

/** Дата и время из `created_at` / `updated_at` / `change_date`. */
internal fun markDateTime(value: String?): String {
    val text = value?.trim().orEmpty()
    if (text.isBlank()) return ""
    val match = MARK_DATE_TIME.find(text) ?: return MeshCore.dateShort(text)
    val date = MeshCore.dateShort(
        "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}"
    )
    val time = buildString {
        append(match.groupValues[4])
        append(':')
        append(match.groupValues[5])
        if (match.groupValues[6].isNotBlank()) {
            append(':')
            append(match.groupValues[6])
        }
    }
    return "$date, $time"
}

/** Показывать ли отдельную строку «Обновлено». */
internal fun markTimesDiffer(createdAt: String?, updatedAt: String?): Boolean {
    val created = markDateTime(createdAt)
    val updated = markDateTime(updatedAt)
    return updated.isNotBlank() && updated != created
}

internal fun markValueLabel(value: String?): String {
    val text = value?.trim().orEmpty()
    if (text.isBlank()) return "—"
    val number = text.toDoubleOrNull() ?: return text
    return formatMark(number)
}

/**
 * Хронологическая цепочка оценок: старые значения идут серыми, последнее —
 * актуальное. Дубли подряд убираем, иначе `before`/`after` дают «5 → 5».
 */
internal fun markHistoryChain(
    history: List<MarkHistoryEntry>?,
    currentValue: String?,
): List<String> {
    val values = mutableListOf<String>()
    fun add(raw: String?) {
        val label = markValueLabel(raw)
        if (label.isNotBlank() && label != "—" && values.lastOrNull() != label) {
            values += label
        }
    }
    history.orEmpty()
        .sortedBy { it.changeDate ?: it.date ?: "" }
        .forEach { entry ->
            add(entry.before?.value)
            add(entry.after?.value)
        }
    add(currentValue)
    return values
}

@Composable
internal fun MarkHistorySection(history: List<MarkHistoryEntry>?, currentValue: String?) {
    val entries = remember(history) {
        history.orEmpty().sortedBy { it.changeDate ?: it.date ?: "" }
    }
    if (entries.isEmpty()) return

    val chain = remember(entries, currentValue) {
        markHistoryChain(entries, currentValue)
    }

    Spacer(Modifier.size(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            Words.MarkHistory.text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )

        if (chain.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chain.forEachIndexed { index, value ->
                    if (index > 0) {
                        Text(
                            "→",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index == chain.lastIndex) {
                        GradeBadge(value, value.toDoubleOrNull())
                    } else {
                        HistoryGradeBadge(value)
                    }
                }
            }
        }

        entries.forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val time = markDateTime(entry.changeDate ?: entry.date)
                if (time.isNotBlank()) {
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val changes = markHistoryChanges(entry)
                if (changes.isEmpty()) {
                    Text(
                        Words.MarkHistoryUpdated.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    changes.forEach { change -> HistoryChangeRow(change) }
                }
            }
        }
    }
}

@Composable
private fun HistoryGradeBadge(value: String) {
    Box(
        modifier = Modifier
            .widthIn(min = 48.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private data class HistoryChange(
    val label: String,
    val before: String,
    val after: String,
)

private fun markHistoryChanges(entry: MarkHistoryEntry): List<HistoryChange> {
    val before = entry.before ?: return emptyList()
    val after = entry.after ?: return emptyList()
    val changes = mutableListOf<HistoryChange>()

    val beforeValue = markValueLabel(before.value)
    val afterValue = markValueLabel(after.value)
    if (beforeValue != afterValue) {
        changes += HistoryChange(Words.Grade.text, beforeValue, afterValue)
    }
    if (before.weight != after.weight) {
        changes += HistoryChange(
            Words.Weight.text,
            before.weight?.let(::formatWeight) ?: "—",
            after.weight?.let(::formatWeight) ?: "—"
        )
    }
    if (before.controlFormName != after.controlFormName) {
        changes += HistoryChange(
            Words.ControlForm.text,
            before.controlFormName?.takeIf { it.isNotBlank() } ?: "—",
            after.controlFormName?.takeIf { it.isNotBlank() } ?: "—"
        )
    }
    if (before.commentExists != after.commentExists) {
        changes += HistoryChange(
            Words.Comment.text,
            yesNo(before.commentExists),
            yesNo(after.commentExists)
        )
    }
    if (before.isPoint != after.isPoint) {
        changes += HistoryChange(
            Words.PointMark.text,
            yesNo(before.isPoint),
            yesNo(after.isPoint)
        )
    }
    if (before.isExam != after.isExam) {
        changes += HistoryChange(
            Words.Exam.text,
            yesNo(before.isExam),
            yesNo(after.isExam)
        )
    }
    return changes
}

private fun yesNo(value: Boolean?): String = when (value) {
    true -> Words.Yes.text
    false -> Words.No.text
    null -> "—"
}

@Composable
private fun HistoryChangeRow(change: HistoryChange) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            change.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            change.before,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            " → ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            change.after,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
