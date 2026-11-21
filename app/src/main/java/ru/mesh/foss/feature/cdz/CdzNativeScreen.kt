@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.Words
import ru.mesh.foss.ui.GlassCard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Нативное окно ЦДЗ на Compose/M3 Expressive.
 *
 * Экран не содержит сетевой логики: он рисует [CdzNativeViewModel] и вызывает
 * её команды. Так UI остаётся модульным, а прохождение попытки можно тестировать
 * и менять независимо от вёрстки.
 */
@Composable
fun CdzNativeScreen(
    viewModel: CdzNativeViewModel,
    onClose: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmStart by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.message) {
        val text = viewModel.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }

    fun requestClose() {
        if (viewModel.phase == CdzPhase.Game && viewModel.hasUnsaved) {
            confirmLeave = true
        } else {
            onClose()
        }
    }

    BackHandler { requestClose() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(Words.CdzNativeTitle.text) },
                subtitle = {
                    Text(
                        when {
                            viewModel.phase == CdzPhase.Game ->
                                Words.CdzTaskOf(viewModel.current + 1, viewModel.total)
                            viewModel.phase == CdzPhase.Choice ->
                                Words.CdzAttemptStatus(
                                    viewModel.attemptCount,
                                    viewModel.lastAttemptId ?: "—",
                                )
                            else -> Words.CdzAttemptLoading.text
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::requestClose) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (viewModel.phase == CdzPhase.Game) {
                GameActions(
                    busy = viewModel.busy,
                    onSave = viewModel::saveCurrent,
                    onFinish = { confirmFinish = true },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (viewModel.phase) {
                CdzPhase.Loading -> LoadingPanel(
                    error = viewModel.error,
                    onRetry = viewModel::openChallenge,
                )
                CdzPhase.Choice -> ChoicePanel(
                    viewModel = viewModel,
                    onContinue = viewModel::beginAttempt,
                    onStart = { confirmStart = true },
                    onHistory = viewModel::showHistory,
                )
                CdzPhase.Game -> GamePanel(viewModel)
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text(Words.CdzStartAttempt.text) },
            text = {
                Text(
                    if (viewModel.attemptCount > 0) {
                        Words.CdzNewAttemptWarning(viewModel.attemptCount)
                    } else {
                        Words.CdzFirstAttemptWarning.text
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStart = false
                    viewModel.beginAttempt()
                }) { Text(Words.CdzStartAnyway.text) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text(Words.Cancel.text) }
            },
        )
    }

    if (confirmFinish) {
        val done = viewModel.solvedCount
        val total = viewModel.total
        val message = buildString {
            append(Words.CdzFinishConfirm(done, total))
            if (done < total || viewModel.hasUnsaved) {
                append("\n\n")
                append(Words.CdzFinishWarning(done, total))
                append("\n")
                append(Words.CdzFinishUnsaved.text)
            }
        }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(Words.CdzFinishTitle.text) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    viewModel.finishAttempt()
                }) { Text(Words.CdzFinishAnyway.text) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text(Words.Cancel.text) }
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(Words.CdzLeaveTitle.text) },
            text = { Text(Words.CdzLeaveMessage.text) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onClose()
                }) { Text(Words.CdzLeaveAnyway.text) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(Words.Cancel.text) }
            },
        )
    }

    viewModel.result?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = { Text(Words.CdzResultTitle.text) },
            text = {
                Text(
                    if (result.score >= 0) {
                        Words.CdzResultScore(result.score, result.max)
                    } else {
                        Words.CdzResultDone.text
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissResult()
                    onClose()
                }) { Text(Words.Close.text) }
            },
        )
    }

    if (viewModel.historyVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHistory,
            title = { Text(Words.CdzAttemptHistory.text) },
            text = {
                if (viewModel.historyLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.Center,
                    ) { LoadingIndicator() }
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        viewModel.historyLines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissHistory) {
                    Text(Words.Close.text)
                }
            },
        )
    }
}

@Composable
private fun LoadingPanel(
    error: String?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (error == null) {
            LoadingIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = Words.CdzAttemptLoading.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
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
}

@Composable
private fun ChoicePanel(
    viewModel: CdzNativeViewModel,
    onContinue: () -> Unit,
    onStart: () -> Unit,
    onHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = Words.CdzAttemptTitle.text,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = Words.CdzAttemptStatus(
                        viewModel.attemptCount,
                        viewModel.lastAttemptId ?: "—",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = Words.CdzAttemptHint.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                viewModel.lastAttemptId?.let { last ->
                    Button(
                        onClick = onContinue,
                        enabled = !viewModel.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(Words.CdzContinueAttempt(last))
                    }
                }
                FilledTonalButton(
                    onClick = onStart,
                    enabled = !viewModel.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (viewModel.attemptCount > 0) {
                            Words.CdzStartNewAttempt.text
                        } else {
                            Words.CdzStartAttempt.text
                        }
                    )
                }
                TextButton(
                    onClick = onHistory,
                    enabled = !viewModel.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(Words.CdzAttemptHistory.text)
                }
                if (viewModel.busy) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            Words.CdzAttemptLoading.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        viewModel.error?.let { error ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::openChallenge) {
                        Text(Words.Retry.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun GamePanel(viewModel: CdzNativeViewModel) {
    val task = viewModel.currentTask
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Words.CdzTaskOf(viewModel.current + 1, viewModel.total),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Words.CdzSavedCount(viewModel.solvedCount, viewModel.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearWavyProgressIndicator(
                progress = {
                    if (viewModel.total == 0) 0f
                    else viewModel.solvedCount.toFloat() / viewModel.total
                },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(viewModel.tasks) { index, taskItem ->
                    TaskNumberChip(
                        number = index + 1,
                        current = index == viewModel.current,
                        done = viewModel.isDone(taskItem.id),
                        answered = viewModel.isAnswered(taskItem.id),
                        onClick = { viewModel.selectTask(index) },
                    )
                }
            }
            if (viewModel.hasUnsaved) {
                Text(
                    text = Words.CdzUnsavedShort.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (task == null) {
                Text(Words.CdzNativeError.text, color = MaterialTheme.colorScheme.error)
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = Words.CdzTaskOf(viewModel.current + 1, viewModel.total),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        CdzQuestionContent(task.question)
                    }
                }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = Words.CdzAnswerLabel.text,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        CdzAnswerContent(
                            task = task,
                            answer = viewModel.answerOf(task.id),
                            onChanged = { answer -> viewModel.updateAnswer(task.id, answer) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TaskNumberChip(
    number: Int,
    current: Boolean,
    done: Boolean,
    answered: Boolean,
    onClick: () -> Unit,
) {
    val container = when {
        current -> MaterialTheme.colorScheme.primary
        done -> MaterialTheme.colorScheme.primaryContainer
        answered -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        current -> MaterialTheme.colorScheme.onPrimary
        done -> MaterialTheme.colorScheme.onPrimaryContainer
        answered -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (current) BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary) else null,
        modifier = Modifier.size(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GameActions(
    busy: Boolean,
    onSave: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onSave,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text(Words.CdzNativeSave.text)
            }
            Button(
                onClick = onFinish,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text(Words.CdzNativeFinish.text)
            }
        }
    }
}
