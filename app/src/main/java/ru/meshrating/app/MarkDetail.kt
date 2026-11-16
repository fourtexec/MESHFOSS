package ru.meshrating.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MarkDetailDialog(
    markId: String,
    personId: String?,
    client: MeshClient,
    store: SessionStore,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(markId) {
        loading = true
        error = null
        try {
            val pid = personId?.takeIf { it.isNotBlank() } ?: store.personId
            detail = withContext(Dispatchers.IO) { client.getMark(markId, pid) }
        } catch (e: Exception) {
            error = MeshCore.describeError(e)
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        title = {
            Text(detail?.optString("subject_name")?.takeIf { it.isNotBlank() } ?: "Оценка")
        },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            } else {
                val d = detail ?: return@AlertDialog
                val value = d.nullableDouble("value")
                val markText = d.optString("markText").takeIf { it.isNotBlank() }
                    ?: d.optString("value").ifBlank { formatMark(value) }
                val weight = d.nullableDouble("weight") ?: 1.0
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeightedGradeBadge(markText, value, weight)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            val controlForm = d.optString("control_form_name")
                            if (controlForm.isNotBlank() && controlForm != "—") {
                                Text(controlForm, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                MeshCore.dateShort(d.optString("date")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val teacher = d.optJSONObject("teacher")
                    val teacherName = buildString {
                        teacher?.optString("last_name")?.takeIf { it.isNotBlank() }?.let { append(it) }
                        teacher?.optString("first_name")?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(" "); append(it) }
                        teacher?.optString("middle_name")?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(" "); append(it) }
                    }
                    if (teacherName.isNotBlank()) InfoRow("Учитель", teacherName)

                    val activity = d.optJSONObject("activity")
                    val topic = activity?.optString("lesson_topic")
                    if (!topic.isNullOrBlank()) InfoRow("Тема", topic)

                    val comment = d.optString("comment")
                    if (comment.isNotBlank()) {
                        Text("Комментарий", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(comment, style = MaterialTheme.typography.bodyMedium)
                    }

                    val classResults = d.optJSONObject("class_results")
                    if (classResults != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Результаты по классу (${classResults.optInt("total_students")} учеников)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        val dist = classResults.optJSONArray("marks_distributions")?.toObjectList().orEmpty()
                        dist.forEach { bucket ->
                            val grade = bucket.optJSONObject("mark_value")?.opt("five")
                            val percent = bucket.optInt("percentage_of_students")
                            val count = bucket.optInt("number_of_students")
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    grade?.toString() ?: "—",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                LinearProgressIndicator(
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
        }
    )
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

private fun JSONArray.toObjectList(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.nullableDouble(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() || it.isInfinite() }