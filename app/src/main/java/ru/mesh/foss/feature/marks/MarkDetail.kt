@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.marks

import ru.mesh.foss.R
import ru.mesh.foss.core.*
import ru.mesh.foss.data.*
import ru.mesh.foss.data.model.MarkDetail
import ru.mesh.foss.ui.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun MarkDetailDialog(
    markId: String,
    personId: String?,
    client: MeshClient,
    store: SessionStore,
    onDismiss: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<MarkDetail?>(null) }

    LaunchedEffect(markId) {
        loading = true
        error = null
        detail = null
        try {
            val pid = personId?.takeIf { it.isNotBlank() } ?: store.personId
            detail = withContext(Dispatchers.IO) { client.getMarkDetail(markId, pid) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = MeshCore.describeError(e)
        } finally {
            if (isActive) loading = false
        }
    }

    AlertDialog(
        modifier = Modifier.dialogGlass(),
        containerColor = Color.Transparent,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(Words.Close.text) }
        },
        title = {
            Text(detail?.subjectName?.takeIf { it.isNotBlank() } ?: Words.Grade.text)
        },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            } else {
                detail?.let { MarkDetailBody(it) }
            }
        }
    )
}

@Composable
private fun MarkDetailBody(detail: MarkDetail) {
    val value = detail.value?.toDoubleOrNull()
    val markText = detail.value?.takeIf { it.isNotBlank() } ?: formatMark(value)
    val weight = detail.weight ?: 1.0
    val teacherName = listOfNotNull(
        detail.teacher?.lastName,
        detail.teacher?.firstName,
        detail.teacher?.middleName
    ).filter { it.isNotBlank() }.joinToString(" ")
    val hasFiles = detail.resultFiles.orEmpty().isNotEmpty() ||
        detail.studentWorkResultFiles.orEmpty().isNotEmpty()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeightedGradeBadge(markText, value, weight)
            Spacer(Modifier.width(12.dp))
            Column {
                val controlForm = detail.controlFormName.orEmpty()
                if (controlForm.isNotBlank() && controlForm != "—") {
                    Text(controlForm, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                val date = detail.date?.takeIf { it.isNotBlank() }
                    ?: detail.createdAt?.takeIf { it.isNotBlank() }
                if (!date.isNullOrBlank()) {
                    Text(
                        MeshCore.dateShort(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hasFiles) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_attachment),
                    contentDescription = Words.Files.text,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (teacherName.isNotBlank()) InfoRow(Words.Teacher.text, teacherName)

        val topic = detail.activity?.lessonTopic
        if (!topic.isNullOrBlank()) InfoRow(Words.Topic.text, topic)

        val comment = detail.comment.orEmpty()
        if (comment.isNotBlank()) {
            Text(
                Words.Comment.text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(comment, style = MaterialTheme.typography.bodyMedium)
        }

        MarkFilesSection(detail)

        val classResults = detail.classResults
        if (classResults != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                Words.ClassResultsStudents(classResults.totalStudents ?: 0),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            // Сортировка по возрастанию оценки (2,3,4,5) — сервер отдаёт в произвольном порядке.
            val dist = classResults.marksDistributions.orEmpty().sortedBy {
                it.markValue?.five ?: Double.MAX_VALUE
            }
            dist.forEach { bucket ->
                val grade = bucket.markValue?.five
                val percent = bucket.percentageOfStudents ?: 0
                val count = bucket.numberOfStudents ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        grade?.let(::formatMark) ?: "—",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp)
                    )
                    // M3E: «волосяной» индикатор — прогресс живёт волной.
                    LinearWavyProgressIndicator(
                        progress = { (percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(
                        "$percent% ($count)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
