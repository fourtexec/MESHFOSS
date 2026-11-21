@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.MeshCdz
import ru.mesh.foss.core.Words
import ru.mesh.foss.ui.GlassCard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Просмотр результата попытки без браузера и без создания новых попыток. */
@Composable
fun CdzResultScreen(
    viewModel: CdzResultViewModel,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(Words.CdzResult.text) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                viewModel.loading && viewModel.info == null -> ResultLoading()
                viewModel.error != null && viewModel.info == null -> ResultError(
                    error = viewModel.error.orEmpty(),
                    onRetry = viewModel::load,
                )
                else -> ResultContent(viewModel)
            }
        }
    }
}

@Composable
private fun ResultLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                Words.CdzAttemptLoading.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultError(
    error: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(Words.Retry.text) }
    }
}

@Composable
private fun ResultContent(viewModel: CdzResultViewModel) {
    val info = viewModel.info ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ResultSummary(info) }
        itemsIndexed(viewModel.tasks) { index, task ->
            ResultTaskCard(number = index + 1, task = task)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ResultSummary(info: JSONObject) {
    val got = info.optDouble("got_score", -1.0)
    val max = info.optDouble("max_score", -1.0)
    val duration = (info.optLong("time_duration", 0L) / 1000L).toInt()
    val completed = info.optString("complete_attempt")
        .take(19)
        .replace('T', ' ')
        .trim()
    val attemptNum = info.optInt("attempt_num", 0)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Words.CdzAttemptNumber(attemptNum),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (completed.isNotBlank()) {
                    Text(
                        text = completed,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (got >= 0 && max >= 0) {
                    Words.CdzScore(formatScore(got), formatScore(max))
                } else {
                    Words.CdzResultDone.text
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (duration > 0) {
                Text(
                    text = Words.CdzInDuration(MeshCdz.formatDuration(duration)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResultMetric(
                    value = info.optInt("amount_correct_answer", 0),
                    label = Words.CdzCorrect.text,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                ResultMetric(
                    value = info.optInt("amount_partially_correct_answer", 0),
                    label = Words.CdzPartiallyCorrect.text,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResultMetric(
                    value = info.optInt("amount_wrong_answer", 0),
                    label = Words.CdzIncorrect.text,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                ResultMetric(
                    value = info.optInt("amount_missed_answer", 0),
                    label = Words.CdzMissed.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ResultMetric(
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
private fun ResultTaskCard(
    number: Int,
    task: CdzResultTask,
) {
    val answerText = remember(task.answer, task.userAnswer) { formatUserAnswer(task) }
    val correctness = task.feedback?.optString("correctness").orEmpty()
    val verdict = correctnessLabel(correctness)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${Words.CdzTask.text} $number",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (task.maxScore >= 0) {
                    Text(
                        text = Words.CdzScore(
                            formatScore(task.score.coerceAtLeast(0.0)),
                            formatScore(task.maxScore),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (task.question.length() > 0) {
                CdzQuestionContent(task.question)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = Words.YourAnswer.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = answerText.ifBlank { Words.CdzNoAnswer.text },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (verdict != null) {
                Surface(
                    shape = CircleShape,
                    color = correctnessColor(correctness).copy(alpha = 0.14f),
                    contentColor = correctnessColor(correctness),
                ) {
                    Text(
                        text = verdict,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private fun correctnessLabel(value: String): String? = when (value) {
    "CORRECT" -> Words.CdzCorrect.text
    "PARTIALLY_CORRECT" -> Words.CdzPartiallyCorrect.text
    "INCORRECT" -> Words.CdzIncorrect.text
    else -> null
}

@Composable
private fun correctnessColor(value: String): Color = when (value) {
    "CORRECT" -> MaterialTheme.colorScheme.tertiary
    "PARTIALLY_CORRECT" -> MaterialTheme.colorScheme.primary
    "INCORRECT" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Человекочитаемый ответ ученика по данным `attempt/{id}/result` (§9, §16.3). */
private fun formatUserAnswer(task: CdzResultTask): String {
    val userAnswer = task.userAnswer ?: return ""
    val type = answerType(task.answer).ifBlank { userAnswer.optString("@answer_type") }
    val options = parseOptions(task.answer)
    val names = options.associate { option ->
        option.id to option.text.ifBlank {
            option.description.ifBlank { option.id }
        }
    }
    fun name(id: String): String = names[id] ?: id

    return when (type) {
        "answer/single", "answer/multiple" ->
            selectedOptionIds(userAnswer).joinToString("\n") { name(it) }
        "answer/match" ->
            parseMatch(userAnswer).entries.joinToString("\n") {
                "${name(it.key)} → ${name(it.value)}"
            }
        "answer/number", "answer/number/interval", "answer/string", "answer/free" ->
            textAnswer(userAnswer).trim()
        "answer/order" ->
            idsText(userAnswer.optJSONArray("ids_order"), ::name)
        "answer/order/multiple" -> {
            val groups = userAnswer.optJSONArray("grouped_options") ?: JSONArray()
            (0 until groups.length()).mapNotNull { index ->
                val group = groups.optJSONObject(index) ?: return@mapNotNull null
                idsText(group.optJSONArray("ids_order"), ::name)
            }.joinToString("\n")
        }
        "answer/groups" -> {
            val groups = userAnswer.optJSONArray("groups") ?: JSONArray()
            (0 until groups.length()).mapNotNull { index ->
                val group = groups.optJSONObject(index) ?: return@mapNotNull null
                idsText(group.optJSONArray("options_ids"), ::name)
            }.joinToString("\n")
        }
        "answer/string/multiple" -> {
            val answers = userAnswer.optJSONArray("answers") ?: JSONArray()
            (0 until answers.length()).mapNotNull { index ->
                val answer = answers.optJSONObject(index) ?: return@mapNotNull null
                idsText(answer.optJSONArray("strings")) { it }
            }.joinToString("\n")
        }
        else -> {
            val copy = JSONObject(userAnswer.toString())
            copy.remove("@answer_type")
            copy.toString()
        }
    }
}

private fun idsText(ids: JSONArray?, name: (String) -> String): String {
    if (ids == null) return ""
    return (0 until ids.length())
        .mapNotNull { ids.optString(it).takeIf { id -> id.isNotBlank() } }
        .joinToString("\n", transform = name)
}

private fun formatScore(value: Double): String =
    if (value.isFinite() && value % 1.0 == 0.0) value.toInt().toString() else value.toString()
