@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.MeshCdz
import ru.mesh.foss.core.Words
import ru.mesh.foss.feature.olympiads.ProgrammingResultContent
import ru.mesh.foss.feature.olympiads.ProgrammingResultData
import ru.mesh.foss.ui.Design
import ru.mesh.foss.ui.GlassButton
import ru.mesh.foss.ui.GlassCard
import ru.mesh.foss.ui.LiquidTopBar
import ru.mesh.foss.ui.LocalDesign

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
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
    CdzAttemptResultScreen(
        info = viewModel.info,
        tasks = viewModel.tasks,
        loading = viewModel.loading,
        error = viewModel.error,
        onClose = onClose,
        onRetry = viewModel::load,
    )
}

/**
 * Общий рендер результата попытки. Используется и ЦДЗ, и олимпиадой:
 * у обоих `webtests`-контуров совпадает структура `attempt_info`/`results`.
 */
@Composable
fun CdzAttemptResultScreen(
    info: JSONObject?,
    tasks: List<CdzResultTask>,
    loading: Boolean,
    error: String?,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    title: String = Words.CdzResult.text,
    programming: ProgrammingResultData? = null,
) {
    BackHandler(onBack = onClose)

    val liquid = LocalDesign.current == Design.LIQUID
    Scaffold(
        containerColor = if (LocalDesign.current == Design.PLAIN) MaterialTheme.colorScheme.background else androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            if (liquid) {
                LiquidTopBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val motion = MaterialTheme.motionScheme
            val phase = when {
                loading && info == null && programming == null -> ResultPhase.Loading
                error != null && info == null && programming == null -> ResultPhase.Error
                else -> ResultPhase.Content
            }
            AnimatedContent(
                targetState = phase,
                transitionSpec = { cdzPhaseTransition(motion) },
                label = "cdzResultPhase",
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                when (shown) {
                    ResultPhase.Loading -> ResultLoading()
                    ResultPhase.Error -> ResultError(
                        error = error.orEmpty(),
                        onRetry = onRetry,
                    )
                    ResultPhase.Content -> if (programming != null) {
                        ProgrammingResultContent(data = programming)
                    } else {
                        ResultContent(info = info, tasks = tasks)
                    }
                }
            }
        }
    }
}

private enum class ResultPhase { Loading, Error, Content }

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
        GlassButton(onClick = onRetry) { Text(Words.Retry.text) }
    }
}

@Composable
private fun ResultContent(
    info: JSONObject?,
    tasks: List<CdzResultTask>,
) {
    val summary = info ?: return
    val contexts = remember(summary) { parseContextInfo(summary) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ResultSummary(summary) }
        itemsIndexed(contexts) { index, context ->
            ResultContextCard(number = index + 1, context = context)
        }
        itemsIndexed(tasks) { index, task ->
            ResultTaskCard(number = index + 1, task = task)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ResultContextCard(
    number: Int,
    context: CdzContextInfo,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = context.caption.ifBlank { Words.OlympiadCommonInfo.text },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            CdzQuestionContent(context.elements)
        }
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
            val waiting = info.optInt("amount_waiting_review_answer", 0)
            if (waiting > 0) {
                ResultMetric(
                    value = waiting,
                    label = Words.CdzAwaitingReview.text,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
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
    val correctness = task.feedback?.optString("correctness").orEmpty()
    val awaitingReview = task.status == "AWAIT_FOR_REVIEW"
    val verdict = if (awaitingReview) Words.CdzAwaitingReview.text else correctnessLabel(correctness)
    val inlineGapAnswers = remember(task.userAnswer, task.answer) {
        if (answerType(task.answer) == "answer/gap/text/input") {
            gapAnswers(task.userAnswer, task.answer.optJSONArray("text_position"))
        } else {
            emptyList()
        }
    }
    val answersInText = inlineGapAnswers.any { it.position >= 0 }
    val motion = MaterialTheme.motionScheme
    val verdictColor by animateColorAsState(
        targetValue = if (awaitingReview) {
            MaterialTheme.colorScheme.secondary
        } else {
            correctnessColor(correctness)
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "resultVerdictColor",
    )

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
                        text = if (awaitingReview) {
                            Words.CdzAwaitingReviewScore(task.maxScore.toInt())
                        } else {
                            Words.CdzScore(
                                formatScore(task.score.coerceAtLeast(0.0)),
                                formatScore(task.maxScore),
                            )
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (task.question.length() > 0) {
                CdzQuestionContent(task.question, inlineAnswers = inlineGapAnswers)
            }
            if (answerType(task.answer) == "answer/gap/match/background/image/selection") {
                val background = task.answer.optJSONObject("background_image")
                val imageUrl = cdzAbsoluteUrl(
                    background?.optString("relative_url").orEmpty()
                        .ifBlank { background?.optString("preview_url").orEmpty() }
                )
                CdzRemoteImage(
                    url = imageUrl,
                    description = background?.optString("description").orEmpty(),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = Words.YourAnswer.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
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
                }
                if (answersInText) {
                    Text(
                        text = Words.CdzAnswersInText.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ResultUserAnswer(task)
                }
            }
            if (!awaitingReview &&
                (correctness == "INCORRECT" || correctness == "PARTIALLY_CORRECT")
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Words.CdzCorrectAnswer.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val correct = visibleCorrectAnswer(task)
                    if (correct != null) {
                        ResultAnswerPayload(task = task, payload = correct)
                    } else {
                        Text(
                            text = Words.CdzCorrectAnswerHidden.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Ответ ученика с теми же `text_styles`, что и формулы в задании. */
@Composable
private fun ResultUserAnswer(task: CdzResultTask) {
    val userAnswer = task.userAnswer
    if (userAnswer == null || userAnswer.length() == 0) {
        NoAnswer()
        return
    }
    ResultAnswerPayload(task = task, payload = userAnswer)
}

/** Общий рендер ответа: и ответ ученика, и раскрытый правильный ответ. */
@Composable
private fun ResultAnswerPayload(
    task: CdzResultTask,
    payload: JSONObject,
) {
    val type = answerType(task.answer).ifBlank { payload.optString("@answer_type") }
    val options = remember(task.answer) { parseOptions(task.answer) }
    val byId = remember(options) { options.associateBy { it.id } }
    val optionOrder = remember(options) {
        options.withIndex().associate { (index, option) -> option.id to index }
    }

    when (type) {
        "answer/single", "answer/multiple" -> {
            val ids = selectedOptionIds(payload)
                .sortedBy { optionOrder[it] ?: Int.MAX_VALUE }
            if (ids.isEmpty()) NoAnswer() else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ids.forEach { id -> RichOptionLine(byId[id]) }
            }
        }
        "answer/order" -> {
            val ids = stringIds(payload.optJSONArray("ids_order"))
            if (ids.isEmpty()) NoAnswer() else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ids.forEachIndexed { index, id ->
                    RichOptionLine(byId[id], prefix = "${index + 1}. ")
                }
            }
        }
        "answer/match" -> {
            val pairs = parseMatch(payload).toList()
                .sortedWith(
                    compareBy<Pair<String, String>>(
                        { optionOrder[it.first] ?: Int.MAX_VALUE },
                        { optionOrder[it.second] ?: Int.MAX_VALUE },
                    )
                )
            if (pairs.isEmpty()) NoAnswer() else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pairs.forEach { (sourceId, targetId) ->
                    RichMatchLine(source = byId[sourceId], target = byId[targetId])
                }
            }
        }
        "answer/groups" -> {
            val groups = payload.optJSONArray("groups") ?: JSONArray()
            val lines = (0 until groups.length()).mapNotNull { index ->
                val group = groups.optJSONObject(index) ?: return@mapNotNull null
                val ids = stringIds(group.optJSONArray("options_ids"))
                    .sortedBy { optionOrder[it] ?: Int.MAX_VALUE }
                if (ids.isEmpty()) null else Triple(index, group.optString("group_id"), ids)
            }
            if (lines.isEmpty()) NoAnswer() else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { (groupIndex, groupId, ids) ->
                    Text(
                        text = byId[groupId]?.label().orEmpty().ifBlank {
                            Words.CdzGroup(groupIndex + 1)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    ids.forEach { id -> RichOptionLine(byId[id]) }
                }
            }
        }
        "answer/table" -> {
            val table = remember(task.answer) { parseTable(task.answer) }
            val values = remember(payload) { parseTableValues(payload) }
            if (values.isEmpty()) NoAnswer() else CdzTableResult(table = table, values = values)
        }
        "answer/grid" -> {
            val grid = remember(task.answer) { parseGrid(task.answer) }
            val values = remember(payload) { parseGridValues(payload) }
            if (values.isEmpty()) NoAnswer() else CdzGridResult(grid = grid, values = values)
        }
        "answer/inline/choice/single", "answer/gap/match/text" -> {
            val choices = remember(task.answer) { parseInlineChoices(task.answer) }
            val selections = inlineSelections(payload)
            val picked = choices.mapIndexedNotNull { index, choice ->
                choice.options
                    .firstOrNull { it.id == selections[choice.positionId] }
                    ?.let { (index + 1) to it }
            }
            if (picked.isEmpty()) NoAnswer() else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                picked.forEach { (number, option) ->
                    RichOptionLine(option, prefix = "$number. ")
                }
            }
        }
        "answer/gap/match/background/image/selection" -> {
            val positions = remember(task.answer) { imageGapPositions(task.answer) }
            val selected = imageGapSelection(payload, positions)
            if (selected.isEmpty()) NoAnswer() else Text(
                text = Words.CdzSelectedPositions(selected.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        "answer/number", "answer/number/interval", "answer/string", "answer/free" -> {
            val values = textAnswers(payload)
            if (values.isEmpty()) NoAnswer() else Text(
                text = values.joinToString(" / "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        "answer/gap/text/input" -> {
            val gaps = gapAnswers(payload, task.answer.optJSONArray("text_position"))
            if (gaps.isEmpty() || gaps.all { it.values.isEmpty() }) NoAnswer() else Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gaps.forEach { gap ->
                    val value = gap.values.joinToString(" / ").ifBlank { "—" }
                    Text(
                        text = "${gap.index + 1}. $value",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        "answer/string/multiple" -> {
            val answers = payload.optJSONArray("answers") ?: JSONArray()
            val lines = (0 until answers.length()).mapNotNull { index ->
                val answer = answers.optJSONObject(index) ?: return@mapNotNull null
                idsText(answer.optJSONArray("strings")) { it }
            }.filter { it.isNotBlank() }
            if (lines.isEmpty()) NoAnswer() else Text(
                text = lines.joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        else -> {
            val values = textAnswers(payload)
            if (values.isNotEmpty()) {
                Text(
                    text = values.joinToString(" / "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = Words.CdzAnswerUnavailable.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RichOptionLine(
    option: CdzOption?,
    prefix: String = "",
) {
    RichOptionValue(option = option, prefix = prefix)
}

@Composable
private fun RichOptionValue(
    option: CdzOption?,
    prefix: String = "",
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (prefix.isNotBlank()) {
            Text(
                text = prefix,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (option == null) {
            Text(
                text = Words.CdzAnswerUnavailable.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CdzOptionMedia(option)
                val label = option.label()
                if (label.isNotBlank()) {
                    CdzRichText(
                        text = label,
                        spans = if (option.text.isNotBlank()) option.textSpans else emptyList(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun RichMatchLine(
    source: CdzOption?,
    target: CdzOption?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        RichOptionLine(source)
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RichOptionValue(target)
        }
    }
}

@Composable
private fun NoAnswer() {
    Text(
        text = Words.CdzNoAnswer.text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun stringIds(ids: JSONArray?): List<String> {
    if (ids == null) return emptyList()
    return (0 until ids.length())
        .mapNotNull { ids.optString(it).takeIf { id -> id.isNotBlank() } }
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

/**
 * Правильный ответ из фидбека или result-ответа.
 *
 * В подавляющем большинстве `JUST_CORRECTNESS` сервер оставляет только
 * заглушки (`id=null`, `ids=[]`), поэтому `null` здесь — нормальный случай.
 */
private fun visibleCorrectAnswer(task: CdzResultTask): JSONObject? {
    if (task.feedback?.optBoolean("hideCorrectAnswer", false) == true) return null
    if (task.feedback?.optBoolean("hide_correct_answer", false) == true) return null
    task.feedback?.optJSONObject("correct_answer")?.let {
        if (hasVisibleAnswer(it)) return it
    }
    task.feedback?.optJSONObject("correctAnswer")?.let {
        if (hasVisibleAnswer(it)) return it
    }
    task.answer.optJSONObject("reference_right_answer")?.let {
        if (hasVisibleAnswer(it)) return it
    }
    task.answer.optJSONObject("right_answer")?.let {
        if (hasVisibleAnswer(it)) return it
    }
    return null
}

private fun idsText(ids: JSONArray?, name: (String) -> String): String {
    if (ids == null) return ""
    return (0 until ids.length())
        .mapNotNull { ids.optString(it).takeIf { id -> id.isNotBlank() } }
        .joinToString("\n", transform = name)
}

private fun formatScore(value: Double): String =
    if (value.isFinite() && value % 1.0 == 0.0) value.toInt().toString() else value.toString()
