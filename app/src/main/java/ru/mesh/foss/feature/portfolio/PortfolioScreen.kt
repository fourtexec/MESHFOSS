@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.mesh.foss.core.UiLanguage
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.PortfolioAverageMark
import ru.mesh.foss.data.model.PortfolioContext
import ru.mesh.foss.data.model.PortfolioCulturalVisit
import ru.mesh.foss.data.model.PortfolioDiagnostic
import ru.mesh.foss.data.model.PortfolioDiagnosticYear
import ru.mesh.foss.data.model.PortfolioEmployment
import ru.mesh.foss.data.model.PortfolioEvent
import ru.mesh.foss.data.model.PortfolioFinalYear
import ru.mesh.foss.data.model.PortfolioReward
import ru.mesh.foss.data.model.PortfolioSubjectAverage
import ru.mesh.foss.data.model.PortfolioWidget
import ru.mesh.foss.ui.GlassButton
import ru.mesh.foss.ui.GlassCard
import ru.mesh.foss.ui.LocalLiquidBottomPadding

/** Read-only вкладка «Портфолио»: успеваемость, события, награды и диагностики. */
@Composable
fun PortfolioScreen(
    client: MeshClient,
    personId: String?,
    modifier: Modifier = Modifier,
) {
    val vm: PortfolioViewModel = viewModel(
        key = "portfolio:${client.profileId.orEmpty()}:${personId.orEmpty()}",
        factory = PortfolioViewModel.factory(client, personId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    PortfolioContent(
        state = state,
        onRefresh = vm::refresh,
        modifier = modifier,
    )
}

@Composable
private fun PortfolioContent(
    state: PortfolioUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading && state.context == null -> Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }

        state.error != null && state.context == null -> PortfolioError(
            message = state.error.orEmpty(),
            onRetry = onRefresh,
            modifier = modifier,
        )

        else -> PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp + LocalLiquidBottomPadding.current,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { PortfolioHeader(state.context, state) }

                if (state.widgets.isNotEmpty()) {
                    item { PortfolioWidgetsCard(state.widgets) }
                }

                if (state.averageMark != null || state.finalMarks.isNotEmpty()) {
                    item { PortfolioSectionTitle(Words.PortfolioPerformance.text) }
                    state.averageMark?.let { average ->
                        item { PortfolioAverageCard(average) }
                    }
                    if (state.finalMarks.isNotEmpty()) {
                        item { PortfolioFinalMarksCard(state.finalMarks) }
                    }
                }

                if (state.events.isNotEmpty()) {
                    item { PortfolioSectionTitle(Words.PortfolioEvents.text) }
                    item {
                        PortfolioEventsCard(
                            events = state.events,
                            rewards = state.rewards,
                        )
                    }
                }

                if (state.rewards.isNotEmpty()) {
                    item { PortfolioSectionTitle(Words.PortfolioRewards.text) }
                    item {
                        PortfolioRewardsCard(
                            rewards = state.rewards,
                            events = state.events,
                        )
                    }
                }

                if (state.diagnostics.isNotEmpty()) {
                    item { PortfolioSectionTitle(Words.PortfolioDiagnostics.text) }
                    item { PortfolioDiagnosticsCard(state.diagnostics) }
                }

                if (state.employments.isNotEmpty()) {
                    item { PortfolioSectionTitle(Words.PortfolioEmployments.text) }
                    item { PortfolioEmploymentsCard(state.employments) }
                }

                if (state.culturalVisits.isNotEmpty()) {
                    item { PortfolioSectionTitle(Words.PortfolioCultural.text) }
                    item { PortfolioCulturalCard(state.culturalVisits) }
                }

                if (state.partialError) {
                    item { PortfolioNotice(Words.PortfolioPartialError.text) }
                }

                if (!state.hasAnyData && !state.loading) {
                    item { PortfolioNotice(Words.PortfolioEmpty.text) }
                }

                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
private fun PortfolioHeader(context: PortfolioContext?, state: PortfolioUiState) {
    val person = context?.personMeshInfo
    val name = person?.displayName.orEmpty()
    val meta = listOfNotNull(
        person?.displayClass?.takeIf { it.isNotBlank() }?.let { "${Words.Class.text} $it" },
        person?.schoolName?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        PortfolioText.initials(name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name.ifBlank { Words.Student.text },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.hasAnyData) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortfolioStat(
                        value = PortfolioText.number(state.averageMark?.averageMarkFive),
                        label = Words.PortfolioAverageMark.text,
                        modifier = Modifier.weight(1f),
                    )
                    PortfolioStat(
                        value = state.events.size.toString(),
                        label = Words.PortfolioEvents.text,
                        modifier = Modifier.weight(1f),
                    )
                    PortfolioStat(
                        value = state.rewards.size.toString(),
                        label = Words.PortfolioRewards.text,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PortfolioSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun PortfolioWidgetsCard(widgets: List<PortfolioWidget>) {
    val shown = widgets.filter { it.data != null }.take(4)
    if (shown.isEmpty()) return
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                Words.PortfolioAchievements.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            shown.forEachIndexed { index, widget ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val data = widget.data ?: return@forEachIndexed
                Row(verticalAlignment = Alignment.Top) {
                    Text("🏅", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            data.name.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val meta = listOfNotNull(
                            data.subjectName?.takeIf { it.isNotBlank() },
                            data.creationDate?.let { PortfolioText.date(it, UiLanguage.locale) },
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    data.reward?.takeIf { it.isNotBlank() }?.let { reward ->
                        Spacer(Modifier.width(8.dp))
                        PortfolioBadge(reward)
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioAverageCard(average: PortfolioAverageMark) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val subjects = average.subjectsRank.orEmpty()
    val shown = if (showAll) subjects else subjects.take(6)
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Words.PortfolioAverageMark.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    PortfolioText.number(average.averageMarkFive),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                shown.forEach { subject ->
                    PortfolioSubjectBar(subject)
                }
                if (subjects.size > 6) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(
                            if (showAll) Words.PortfolioShowLess.text
                            else Words.PortfolioShowAll.text
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioSubjectBar(subject: PortfolioSubjectAverage) {
    val value = subject.averageMarkFiveSubject
    val fraction = ((value ?: 0.0) / 5.0).coerceIn(0.0, 1.0).toFloat()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                subject.subjectName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                PortfolioText.number(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun PortfolioFinalMarksCard(years: List<PortfolioFinalYear>) {
    var expandedYear by rememberSaveable { mutableStateOf<String?>(null) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                Words.PortfolioFinalMarks.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            years.forEachIndexed { index, year ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                val title = year.yearTitle.orEmpty()
                val open = expandedYear == title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedYear = if (open) null else title }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title.ifBlank { Words.Year.text },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        year.educationLevel?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        PortfolioText.number(year.averageAllSubjects),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (open) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium)
                }
                if (open) {
                    year.subjects.orEmpty().forEach { subject ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                subject.name.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                subject.yearValue
                                    ?: subject.yearFivePointValue?.toString()
                                    ?: "—",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioEventsCard(
    events: List<PortfolioEvent>,
    rewards: List<PortfolioReward>,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) events else events.take(5)
    val rewardsByEvent = remember(rewards) {
        rewards.groupBy { it.entityId.orEmpty() }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            shown.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 9.dp))
                PortfolioEventRow(event, rewardsByEvent[event.id?.toString().orEmpty()].orEmpty())
            }
            if (events.size > 5) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(
                        if (showAll) Words.PortfolioShowLess.text
                        else Words.PortfolioShowAll.text
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioEventRow(event: PortfolioEvent, rewards: List<PortfolioReward>) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    event.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val meta = listOfNotNull(
                    event.category?.value?.takeIf { it.isNotBlank() },
                    event.stageEvent?.takeIf { it.isNotBlank() },
                    event.startDate?.let { PortfolioText.date(it, UiLanguage.locale) },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PortfolioText.score(event.result, event.maxScore)?.let { score ->
                Spacer(Modifier.width(8.dp))
                Text(
                    score,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
        val subject = PortfolioText.subjectNames(event.subjects)
        if (subject.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                subject,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rewards.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rewards.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
                    .distinct()
                    .take(3)
                    .forEach { PortfolioBadge(it) }
            }
        }
    }
}

@Composable
private fun PortfolioRewardsCard(
    rewards: List<PortfolioReward>,
    events: List<PortfolioEvent>,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) rewards else rewards.take(6)
    val eventsById = remember(events) {
        events.mapNotNull { event ->
            event.id?.toString()?.let { id -> id to event }
        }.toMap()
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            shown.forEachIndexed { index, reward ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 7.dp))
                PortfolioRewardRow(
                    reward = reward,
                    event = eventsById[reward.entityId],
                )
            }
            if (rewards.size > 6) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(
                        if (showAll) Words.PortfolioShowLess.text
                        else Words.PortfolioShowAll.text
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioRewardRow(reward: PortfolioReward, event: PortfolioEvent?) {
    val title = PortfolioText.rewardTitle(reward, event)
    val meta = PortfolioText.rewardMeta(reward, event, UiLanguage.locale)
    val badge = (reward.name ?: reward.rewardType?.value)?.takeIf { it.isNotBlank() }
    val showBadge = event != null && badge != null && !badge.equals(title, ignoreCase = true)
    Row(verticalAlignment = Alignment.Top) {
        Text("🏆", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (showBadge && badge != null) {
                Spacer(Modifier.height(4.dp))
                PortfolioBadge(badge)
            }
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PortfolioText.score(event?.result, event?.maxScore)?.let { score ->
            Spacer(Modifier.width(8.dp))
            Text(
                score,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PortfolioDiagnosticsCard(years: List<PortfolioDiagnosticYear>) {
    val uriHandler = LocalUriHandler.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            years.forEachIndexed { yearIndex, year ->
                if (yearIndex > 0) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        year.learningYear.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    year.diagnostics?.totalDiagnostic?.total?.let { total ->
                        Text(
                            Words.PortfolioDiagnosticsCount(total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                year.diagnostics?.bestResult?.let { best ->
                    val bestText = listOfNotNull(
                        best.bestResultSubject?.takeIf { it.isNotBlank() },
                        best.bestResultPercent?.let { PortfolioText.percent(it) },
                    ).joinToString(" · ")
                    if (bestText.isNotBlank()) {
                        Text(
                            "${Words.PortfolioBestResult.text}: $bestText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                year.diagnostics?.diagnostic.orEmpty().forEach { diagnostic ->
                    PortfolioDiagnosticRow(diagnostic, uriHandler)
                }
            }
        }
    }
}

@Composable
private fun PortfolioDiagnosticRow(
    diagnostic: PortfolioDiagnostic,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    diagnostic.subject.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val meta = listOfNotNull(
                    diagnostic.eventDate?.let { PortfolioText.date(it, UiLanguage.locale) },
                    diagnostic.levelType?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                PortfolioText.percent(diagnostic.percentResult),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        diagnostic.note?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(3.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        diagnostic.url?.takeIf { it.isNotBlank() }?.let { url ->
            TextButton(
                onClick = { runCatching { uriHandler.openUri(url) } },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) {
                Text(Words.PortfolioOpenResult.text)
            }
        }
    }
}

@Composable
private fun PortfolioEmploymentsCard(employments: List<PortfolioEmployment>) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            employments.forEachIndexed { index, employment ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    employment.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val meta = listOfNotNull(
                    employment.category?.value?.takeIf { it.isNotBlank() },
                    employment.format?.value?.takeIf { it.isNotBlank() },
                    dateRange(employment.startDate, employment.endDate).takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                employment.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioCulturalCard(visits: List<PortfolioCulturalVisit>) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            visits.forEachIndexed { index, visit ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    visit.culturalInstitutionName
                        ?: visit.culturalInstitutionShortName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val meta = listOfNotNull(
                    visit.culturalInstitutionType?.takeIf { it.isNotBlank() },
                    visit.visitTime?.let { PortfolioText.date(it, UiLanguage.locale) },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = CircleShape,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun PortfolioNotice(message: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PortfolioError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            GlassButton(onClick = onRetry) { Text(Words.Retry.text) }
        }
    }
}

private fun dateRange(start: String?, end: String?): String {
    val locale = UiLanguage.locale
    val from = start?.takeIf { it.isNotBlank() }?.let { PortfolioText.date(it, locale) }
    val to = end?.takeIf { it.isNotBlank() }?.let { PortfolioText.date(it, locale) }
    return when {
        from != null && to != null -> "$from — $to"
        from != null -> from
        to != null -> to
        else -> ""
    }
}
