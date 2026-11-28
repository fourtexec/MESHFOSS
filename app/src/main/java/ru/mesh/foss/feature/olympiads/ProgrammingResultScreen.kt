@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.olympiads

import ru.mesh.foss.core.Words
import ru.mesh.foss.feature.cdz.CdzHtmlContent
import ru.mesh.foss.ui.GlassCard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Нативный результат программистской олимпиады. */
@Composable
fun ProgrammingResultContent(
    data: ProgrammingResultData,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ProgrammingSummary(data) }
        if (!data.description.isNullOrBlank()) {
            item {
                ProgrammingHtmlCard(
                    title = Words.ProgrammingDescription.text,
                    html = data.description,
                )
            }
        }
        if (!data.instruction.isNullOrBlank()) {
            item {
                ProgrammingHtmlCard(
                    title = Words.ProgrammingInstruction.text,
                    html = data.instruction,
                )
            }
        }
        itemsIndexed(data.tasks) { index, task ->
            ProgrammingTaskCard(number = index + 1, task = task)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ProgrammingSummary(data: ProgrammingResultData) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = data.title.ifBlank { Words.OlympiadResults.text },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (data.score != null && data.maxScore != null) {
                Text(
                    text = Words.CdzScore(
                        formatProgrammingScore(data.score),
                        formatProgrammingScore(data.maxScore),
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val completed = data.completedAt.orEmpty()
                .take(19)
                .replace('T', ' ')
                .trim()
            if (completed.isNotBlank()) {
                Text(
                    text = completed,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!data.status.isNullOrBlank()) {
                Text(
                    text = Words.ProgrammingStatus(programmingStatus(data.status.orEmpty())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (data.timeLimitSeconds != null && data.timeLimitSeconds > 0) {
                Text(
                    text = Words.ProgrammingTimeLimit(
                        formatProgrammingDuration(data.timeLimitSeconds)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgrammingMetric(
                    value = data.correct,
                    label = Words.CdzCorrect.text,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                ProgrammingMetric(
                    value = data.partial,
                    label = Words.CdzPartiallyCorrect.text,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ProgrammingMetric(
                    value = data.incorrect,
                    label = Words.CdzIncorrect.text,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProgrammingMetric(
    value: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProgrammingHtmlCard(
    title: String,
    html: String?,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            CdzHtmlContent(html = html)
        }
    }
}

@Composable
private fun ProgrammingTaskCard(
    number: Int,
    task: ProgrammingResultTask,
) {
    val verdict = programmingVerdict(task.checkResult)
    val verdictColor = programmingVerdictColor(task.checkResult)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${Words.CdzTask.text} $number",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (task.name.isNotBlank()) {
                        Text(
                            text = task.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (task.maxScore != null) {
                    Text(
                        text = Words.CdzScore(
                            formatProgrammingScore(task.score ?: 0.0),
                            formatProgrammingScore(task.maxScore),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (verdict != null) {
                Surface(
                    shape = CircleShape,
                    color = verdictColor.copy(alpha = 0.14f),
                    contentColor = verdictColor,
                ) {
                    Text(
                        text = verdict,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            if (!task.description.isNullOrBlank()) {
                CdzHtmlContent(html = task.description)
            }
            if (!task.inputFormat.isNullOrBlank()) {
                ProgrammingHtmlSection(
                    title = Words.ProgrammingInput.text,
                    html = task.inputFormat,
                )
            }
            if (!task.outputFormat.isNullOrBlank()) {
                ProgrammingHtmlSection(
                    title = Words.ProgrammingOutput.text,
                    html = task.outputFormat,
                )
            }
            if (!task.notes.isNullOrBlank()) {
                ProgrammingHtmlSection(
                    title = Words.ProgrammingDescription.text,
                    html = task.notes,
                )
            }
            if (!task.theory.isNullOrBlank()) {
                ProgrammingHtmlSection(
                    title = Words.ProgrammingInstruction.text,
                    html = task.theory,
                )
            }
            if (task.examples.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = Words.ProgrammingExamples.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                task.examples.forEachIndexed { index, example ->
                    ProgrammingExampleCard(number = index + 1, example = example)
                }
            }
            if (!task.answer.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProgrammingPlainSection(
                    title = Words.ProgrammingAnswer.text,
                    value = task.answer.orEmpty(),
                )
            }
            if (!task.sourceCode.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProgrammingPlainSection(
                    title = Words.ProgrammingSourceCode.text,
                    value = task.sourceCode.orEmpty(),
                    monospace = true,
                )
            }
            if (!task.teacherComment.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProgrammingPlainSection(
                    title = Words.ProgrammingTeacherComment.text,
                    value = task.teacherComment.orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun ProgrammingHtmlSection(title: String, html: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CdzHtmlContent(html = html)
    }
}

@Composable
private fun ProgrammingExampleCard(
    number: Int,
    example: ProgrammingResultExample,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${Words.ProgrammingExamples.text} $number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            ProgrammingCodeBlock(
                title = Words.ProgrammingExampleInput.text,
                value = example.input,
            )
            ProgrammingCodeBlock(
                title = Words.ProgrammingExampleOutput.text,
                value = example.output,
            )
            if (!example.comment.isNullOrBlank()) {
                CdzHtmlContent(html = example.comment)
            }
        }
    }
}

@Composable
private fun ProgrammingCodeBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = value.ifBlank { "—" },
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ProgrammingPlainSection(
    title: String,
    value: String,
    monospace: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = if (monospace) {
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            },
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun programmingVerdict(value: String?): String? = when (value?.uppercase()) {
    "CORRECT" -> Words.CdzCorrect.text
    "PART_CORRECT", "PARTIALLY_CORRECT" -> Words.CdzPartiallyCorrect.text
    "INCORRECT", "NOT_CORRECT" -> Words.CdzIncorrect.text
    else -> null
}

@Composable
private fun programmingVerdictColor(value: String?): Color = when (value?.uppercase()) {
    "CORRECT" -> MaterialTheme.colorScheme.tertiary
    "PART_CORRECT", "PARTIALLY_CORRECT" -> MaterialTheme.colorScheme.primary
    "INCORRECT", "NOT_CORRECT" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun programmingStatus(value: String): String = when (value.uppercase()) {
    "ACCEPTED", "COMPLETED", "DONE", "CHECKED" -> Words.CdzCompleted.text
    "IN_PROGRESS", "STARTED" -> Words.CdzInProgress.text
    "NOT_STARTED", "PENDING", "NEW" -> Words.CdzNotCompleted.text
    "CHECKING", "ON_REVIEW", "WAITING_REVIEW" -> Words.CdzAwaitingReview.text
    else -> value
}

private fun formatProgrammingScore(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun formatProgrammingDuration(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes > 0) "$minutes мин ${rest.toString().padStart(2, '0')} с" else "$rest с"
}
