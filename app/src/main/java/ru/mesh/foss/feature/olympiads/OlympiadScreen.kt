@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.olympiads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.mesh.foss.core.UiLanguage
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.OlympiadDetail
import ru.mesh.foss.data.model.OlympiadDictionaries
import ru.mesh.foss.data.model.OlympiadOverviewItem
import ru.mesh.foss.data.model.OlympiadParticipant
import ru.mesh.foss.data.model.OlympiadPreviousStage
import ru.mesh.foss.data.model.OlympiadResult
import ru.mesh.foss.feature.cdz.CdzAttemptResultScreen
import ru.mesh.foss.ui.Design
import ru.mesh.foss.ui.GlassButton
import ru.mesh.foss.ui.GlassCard
import ru.mesh.foss.ui.GlassOutlinedButton
import ru.mesh.foss.ui.LiquidModalScope
import ru.mesh.foss.ui.LiquidSegmentedTabs
import ru.mesh.foss.ui.LocalDesign
import ru.mesh.foss.ui.LocalLiquidBottomPadding
import java.util.Locale

private const val DETAIL_ABOUT = "about"
private const val DETAIL_RESULTS = "results"

/** Вкладка «Олимпиады»: список «Мои/Все» и карточка олимпиады с результатами. */
@Composable
fun OlympiadScreen(
    client: MeshClient,
    onAttemptOpenChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: OlympiadViewModel = viewModel(
        key = "olympiads:${client.profileId.orEmpty()}",
        factory = OlympiadViewModel.factory(client, context),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.attempt != null) {
        onAttemptOpenChanged(state.attempt != null)
    }
    BackHandler(enabled = state.attempt != null || state.detail != null) {
        if (state.attempt != null) vm.closeAttempt() else vm.closeDetail()
    }

    if (state.attempt != null) {
        val attempt = state.attempt!!
        CdzAttemptResultScreen(
            info = attempt.info,
            tasks = attempt.tasks,
            loading = attempt.loading,
            error = attempt.error,
            onClose = vm::closeAttempt,
            onRetry = vm::retryAttempt,
            title = Words.OlympiadResults.text,
        )
    } else if (state.detail != null) {
        OlympiadDetailScreen(
            detail = state.detail!!,
            dictionaries = state.dictionaries,
            onBack = vm::closeDetail,
            onRetry = vm::retryDetail,
            onOpenDetails = vm::openDetails,
            modifier = modifier,
        )
    } else {
        OlympiadListScreen(
            state = state,
            onTab = vm::setTab,
            onSearch = vm::search,
            onRefresh = vm::refresh,
            onFilters = vm::applyFilters,
            onResetFilters = vm::resetFilters,
            onLoadMore = vm::loadMore,
            onOpen = vm::openContest,
            modifier = modifier,
        )
    }
}

@Composable
private fun OlympiadListScreen(
    state: OlympiadUiState,
    onTab: (OlympiadTab) -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onFilters: (OlympiadFilters) -> Unit,
    onResetFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by rememberSaveable { mutableStateOf(state.query) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.query) { search = state.query }
    val focus = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OlympiadTabs(state.tab, onTab)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(Words.OlympiadSearchHint.text) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focus.clearFocus()
                    onSearch(search)
                }
            ),
            leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = {
                        search = ""
                        focus.clearFocus()
                        onSearch("")
                    }) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.total > 0) "${state.items.size} / ${state.total}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { focus.clearFocus(); onSearch(search) }) {
                Text(Words.OlympiadSearch.text)
            }
            TextButton(onClick = { filtersOpen = true }) {
                Text(
                    if (state.filters.activeCount > 0) {
                        "${Words.OlympiadFilters.text} · ${state.filters.activeCount}"
                    } else {
                        Words.OlympiadFilters.text
                    }
                )
            }
        }
        if (state.filters.hasActive) {
            ActiveFilterChips(
                filters = state.filters,
                dictionaries = state.dictionaries,
                onFilters = onFilters,
                onReset = onResetFilters,
            )
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                state.loading && state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }

                state.error != null && state.items.isEmpty() -> OlympiadError(
                    message = state.error.orEmpty(),
                    onRetry = onRefresh,
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = 24.dp + LocalLiquidBottomPadding.current,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                    if (state.error != null) {
                        item {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        state.error.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(onClick = onRefresh) { Text(Words.Retry.text) }
                                }
                            }
                        }
                    }
                    if (state.items.isEmpty() && !state.loading) {
                        item {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("🏅", style = MaterialTheme.typography.displaySmall)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        Words.OlympiadEmpty.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    itemsIndexed(state.items) { _, item ->
                        OlympiadCard(
                            item = item,
                            dictionaries = state.dictionaries,
                            onClick = {
                                item.resolvedContestId?.let(onOpen)
                            },
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    } else if (state.items.isNotEmpty() && state.items.size < state.total) {
                        item {
                            GlassOutlinedButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(Words.OlympiadShowMore.text)
                            }
                        }
                    }
                        item { Spacer(Modifier.height(4.dp)) }
                    }
                }
            }
        }
    }
    if (filtersOpen) {
        OlympiadFilterSheet(
            filters = state.filters,
            dictionaries = state.dictionaries,
            onApply = {
                filtersOpen = false
                onFilters(it)
            },
            onDismiss = { filtersOpen = false },
        )
    }
}

@Composable
private fun ActiveFilterChips(
    filters: OlympiadFilters,
    dictionaries: OlympiadDictionaries,
    onFilters: (OlympiadFilters) -> Unit,
    onReset: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(filters.years.sortedDescending()) { year ->
            ActiveFilterChip("${Words.Year.text}: $year") {
                onFilters(filters.withoutYear(year))
            }
        }
        items(filters.subjectIds.sorted()) { id ->
            ActiveFilterChip(dictionaries.subjectNames(listOf(id)).ifBlank { id }) {
                onFilters(filters.withoutSubject(id))
            }
        }
        items(filters.stageCodes.sorted()) { code ->
            ActiveFilterChip(dictionaries.stageName(code) ?: code) {
                onFilters(filters.withoutStage(code))
            }
        }
        items(filters.parallelIds.sorted()) { id ->
            ActiveFilterChip(dictionaries.parallelNames(listOf(id)).ifBlank { id }) {
                onFilters(filters.withoutParallel(id))
            }
        }
        if (!filters.includeCurrent) {
            item {
                ActiveFilterChip("− ${Words.OlympiadRunning.text}") {
                    onFilters(filters.copy(includeCurrent = true))
                }
            }
        }
        if (!filters.includeFuture) {
            item {
                ActiveFilterChip("− ${Words.OlympiadUpcoming.text}") {
                    onFilters(filters.copy(includeFuture = true))
                }
            }
        }
        if (!filters.includeCompleted) {
            item {
                ActiveFilterChip("− ${Words.OlympiadFinished.text}") {
                    onFilters(filters.copy(includeCompleted = true))
                }
            }
        }
        item {
            ActiveFilterChip(Words.OlympiadReset.text) { onReset() }
        }
    }
}

@Composable
private fun ActiveFilterChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { Text("×") },
        shape = CircleShape,
    )
}

@Composable
private fun OlympiadFilterSheet(
    filters: OlympiadFilters,
    dictionaries: OlympiadDictionaries,
    onApply: (OlympiadFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    val liquid = LocalDesign.current == Design.LIQUID
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val years = remember(dictionaries.years) {
        (dictionaries.years.ifEmpty {
            ((currentOlympiadYear() - 5)..(currentOlympiadYear() + 1)).toList()
        }).distinct().sortedDescending()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = if (liquid) RoundedCornerShape(32.dp) else BottomSheetDefaults.ExpandedShape,
        containerColor = if (liquid) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentWindowInsets = if (liquid) {
            { WindowInsets(0, 0, 0, 0) }
        } else {
            { BottomSheetDefaults.modalWindowInsets }
        },
        dragHandle = if (liquid) null else { { BottomSheetDefaults.DragHandle() } },
    ) {
        val content: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    Words.OlympiadFilters.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                FilterChipRow(
                    title = Words.OlympiadSubject.text,
                    options = dictionaries.subjects.mapNotNull { item ->
                        item.id?.takeIf { it.isNotBlank() }?.let { it to (item.label ?: it) }
                    },
                    selected = draft.subjectIds,
                    onToggle = { id ->
                        draft = draft.copy(
                            subjectIds = draft.subjectIds.toggle(id)
                        )
                    },
                )
                FilterChipRow(
                    title = Words.OlympiadStage.text,
                    options = dictionaries.stages.mapNotNull { item ->
                        item.id?.takeIf { it.isNotBlank() }?.let { it to (item.label ?: it) }
                    },
                    selected = draft.stageCodes,
                    onToggle = { code ->
                        draft = draft.copy(
                            stageCodes = draft.stageCodes.toggle(code)
                        )
                    },
                )
                FilterChipRow(
                    title = Words.OlympiadParallel.text,
                    options = dictionaries.parallels.mapNotNull { item ->
                        item.id?.takeIf { it.isNotBlank() }?.let { it to (item.label ?: it) }
                    },
                    selected = draft.parallelIds,
                    onToggle = { id ->
                        draft = draft.copy(
                            parallelIds = draft.parallelIds.toggle(id)
                        )
                    },
                )
                YearFilterRow(
                    years = years,
                    selected = draft.years,
                    onToggle = { year ->
                        draft = draft.copy(years = draft.years.toggle(year))
                    },
                )
                FilterChipRow(
                    title = Words.OlympiadStatus.text,
                    options = listOf(
                        "current" to Words.OlympiadRunning.text,
                        "future" to Words.OlympiadUpcoming.text,
                        "completed" to Words.OlympiadFinished.text,
                    ),
                    selected = buildSet {
                        if (draft.includeCurrent) add("current")
                        if (draft.includeFuture) add("future")
                        if (draft.includeCompleted) add("completed")
                    },
                    onToggle = { key ->
                        draft = when (key) {
                            "current" -> draft.copy(includeCurrent = !draft.includeCurrent)
                            "future" -> draft.copy(includeFuture = !draft.includeFuture)
                            else -> draft.copy(includeCompleted = !draft.includeCompleted)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { draft = OlympiadFilters() }) {
                        Text(Words.OlympiadReset.text)
                    }
                    Spacer(Modifier.weight(1f))
                    GlassButton(onClick = { onApply(draft) }) {
                        Text(Words.OlympiadApply.text)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        LiquidModalScope {
            if (liquid) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) { content() }
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (options.isEmpty()) {
            Text(
                Words.NoData.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { (id, label) ->
                    FilterChip(
                        selected = id in selected,
                        onClick = { onToggle(id) },
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun YearFilterRow(
    years: List<Int>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            Words.Year.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(years) { year ->
                FilterChip(
                    selected = year in selected,
                    onClick = { onToggle(year) },
                    label = { Text(year.toString()) },
                )
            }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

@Composable
private fun OlympiadTabs(selected: OlympiadTab, onSelect: (OlympiadTab) -> Unit) {
    val options = listOf(
        OlympiadTab.MY to Words.MyOlympiads.text,
        OlympiadTab.ALL to Words.AllOlympiads.text,
    )
    if (LocalDesign.current == Design.LIQUID) {
        LiquidSegmentedTabs(
            options = options.map { (tab, label) -> tab.name to label },
            selected = selected.name,
            onSelect = { key -> onSelect(OlympiadTab.valueOf(key)) },
        )
    } else {
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (tab, label) ->
                toggleableItem(
                    checked = selected == tab,
                    label = label,
                    onCheckedChange = { onSelect(tab) },
                    weight = 1f,
                )
            }
        }
    }
}

@Composable
private fun OlympiadCard(
    item: OlympiadOverviewItem,
    dictionaries: OlympiadDictionaries,
    onClick: () -> Unit,
) {
    val title = item.contestName?.takeIf { it.isNotBlank() } ?: Words.OlympiadUnavailable.text
    val subjects = dictionaries.subjectNames(item.subjectIds.orEmpty())
    val stage = dictionaries.stageName(item.stageCode)
    val parallels = dictionaries.parallelNames(item.parallelIds.orEmpty())
    val meta = listOfNotNull(stage, parallels).filter { it.isNotBlank() }.joinToString(" · ")
    val dates = dateRange(item.startAt, item.finishAt)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        dates,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                OlympiadStatusBadge(item.statusCode)
            }
            if (subjects.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(subjects, style = MaterialTheme.typography.bodyMedium)
            }
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.teamContest == true || item.recommendationExists == true) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.teamContest == true) {
                        Text(
                            Words.OlympiadTeam.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (item.recommendationExists == true) {
                        Text(
                            "★ ${Words.OlympiadRecommendation.text}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OlympiadDetailScreen(
    detail: OlympiadDetailState,
    dictionaries: OlympiadDictionaries,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("‹ ${Words.OlympiadBack.text}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.weight(1f)) {
            val loaded = detail.detail
            when {
                detail.loading && loaded == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }

                loaded == null && detail.error != null -> OlympiadError(
                    message = detail.error.orEmpty(),
                    onRetry = onRetry,
                )

                loaded == null -> OlympiadError(
                    message = Words.OlympiadUnavailable.text,
                    onRetry = onRetry,
                )

                else -> PullToRefreshBox(
                    isRefreshing = detail.refreshing,
                    onRefresh = onRetry,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OlympiadDetailBody(
                        detail = loaded,
                        participant = detail.participant,
                        participation = detail.participation,
                        result = detail.result,
                        recommendation = detail.recommendation,
                        dictionaries = dictionaries,
                        error = detail.error,
                        onRetry = onRetry,
                        onOpenDetails = onOpenDetails,
                    )
                }
            }
        }
    }
}

@Composable
private fun OlympiadDetailBody(
    detail: OlympiadDetail,
    participant: OlympiadParticipant?,
    participation: OlympiadParticipation,
    result: OlympiadResult?,
    recommendation: String?,
    dictionaries: OlympiadDictionaries,
    error: String?,
    onRetry: () -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val subjects = dictionaries.subjectNames(detail.commonInfo?.subjectIds.orEmpty())
    val stage = dictionaries.stageName(detail.commonInfo?.stageCode)
    val level = dictionaries.levelName(detail.commonInfo?.levelCode)
    val team = detail.participationInfo?.teamContest ?: detail.teamContest
    val dates = dateRange(
        detail.participationInfo?.startAt,
        detail.participationInfo?.finishAt
    )
    val description = OlympiadText.plainDescription(detail.additionalInfo?.contestDescription)
    val recommendationText = OlympiadText.plainDescription(recommendation)
    val uriHandler = LocalUriHandler.current
    var section by rememberSaveable { mutableStateOf(DETAIL_ABOUT) }
    val hasResults = participation != OlympiadParticipation.NOT_PARTICIPATED &&
        result?.hasResult == true &&
        result.resultModel != null
    val effectiveSection = if (hasResults) section else DETAIL_ABOUT
    LaunchedEffect(hasResults) {
        if (!hasResults) section = DETAIL_ABOUT
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 24.dp + LocalLiquidBottomPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            detail.contestName?.takeIf { it.isNotBlank() }
                                ?: Words.OlympiadUnavailable.text,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OlympiadStatusBadge(detail.effectiveStatus)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        dates,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (error != null) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onRetry) { Text(Words.Retry.text) }
                    }
                }
            }
        }
        if (hasResults) {
            item {
                OlympiadDetailTabs(section) { section = it }
            }
        }
        if (effectiveSection == DETAIL_ABOUT) {
            if (recommendationText.isNotBlank()) {
                item {
                    OlympiadInfoCard(Words.OlympiadRecommendation.text) {
                        Text(recommendationText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                OlympiadInfoCard(Words.OlympiadMainInfo.text) {
                    DetailValue(Words.OlympiadSubject.text, subjects)
                    DetailValue(Words.OlympiadStage.text, stage)
                    DetailValue(Words.OlympiadLevel.text, level)
                    DetailValue(
                        Words.OlympiadParticipationType.text,
                        when (team) {
                            true -> Words.OlympiadTeam.text
                            false -> Words.OlympiadIndividual.text
                            null -> null
                        }
                    )
                }
            }
            item {
                OlympiadInfoCard(Words.OlympiadAdditionalInfo.text) {
                    Text(
                        Words.OlympiadDescription.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        description.ifBlank { Words.NoData.text },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val links = detail.additionalInfo?.testContests.orEmpty()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    if (links.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            Words.OlympiadTestContests.text,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        links.forEach { link ->
                            val url = if (link.startsWith("http://") || link.startsWith("https://")) {
                                link
                            } else {
                                "https://$link"
                            }
                            TextButton(onClick = {
                                runCatching { uriHandler.openUri(url) }
                            }) {
                                Text(link, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (detail.prevContestStages.orEmpty().isNotEmpty()) {
                item {
                    OlympiadInfoCard(Words.OlympiadPreviousStages.text) {
                        detail.prevContestStages.orEmpty().forEachIndexed { index, previous ->
                            if (index > 0) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }
                            PreviousStageRow(previous, dictionaries)
                        }
                    }
                }
            }
        } else {
            item {
                OlympiadResultsCard(
                    detail = detail,
                    participant = participant,
                    participation = participation,
                    result = result,
                    uriHandler = uriHandler,
                    onOpenDetails = onOpenDetails,
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun OlympiadDetailTabs(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        DETAIL_ABOUT to Words.OlympiadAbout.text,
        DETAIL_RESULTS to Words.OlympiadResults.text,
    )
    if (LocalDesign.current == Design.LIQUID) {
        LiquidSegmentedTabs(options = options, selected = selected, onSelect = onSelect)
    } else {
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (key, label) ->
                toggleableItem(
                    checked = selected == key,
                    label = label,
                    onCheckedChange = { onSelect(key) },
                    weight = 1f,
                )
            }
        }
    }
}

@Composable
private fun OlympiadInfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PreviousStageRow(stage: OlympiadPreviousStage, dictionaries: OlympiadDictionaries) {
    val subjectText = stage.subjects.orEmpty().mapNotNull { it.label }.distinct().joinToString(", ")
        .ifBlank { dictionaries.subjectNames(stage.subjectIds.orEmpty()) }
    val parallelText = stage.parallels.orEmpty().mapNotNull { it.label }.distinct().joinToString(", ")
        .ifBlank { dictionaries.parallelNames(stage.parallelIds.orEmpty()) }
    val stageText = stage.stage?.label ?: dictionaries.stageName(stage.stageCode)
    val rewards = stage.rewards.orEmpty().mapNotNull { it.label }.distinct().joinToString(", ")
    Text(
        stage.title ?: stageText ?: Words.OlympiadStage.text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (subjectText.isNotBlank()) {
        Text(
            subjectText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val meta = listOfNotNull(parallelText.takeIf { it.isNotBlank() }, stageText)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    if (meta.isNotBlank()) {
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val date = stage.startedAt?.let { OlympiadText.formatDate(it, UiLanguage.locale) }
    if (!date.isNullOrBlank() && date != "—") {
        Text(
            date,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (rewards.isNotBlank()) {
        Text(
            "${Words.OlympiadReward.text}: $rewards",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun OlympiadResultsCard(
    detail: OlympiadDetail,
    participant: OlympiadParticipant?,
    participation: OlympiadParticipation,
    result: OlympiadResult?,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onOpenDetails: (String) -> Unit,
) {
    val final = detail.effectiveStatus == "PUBLISHED_FINAL_RESULTS"
    OlympiadInfoCard(
        if (final) Words.OlympiadFinalResults.text else Words.OlympiadPreliminaryResults.text
    ) {
        val model = result?.resultModel
        if (result?.hasResult != true || model == null) {
            Text(
                olympiadNoResultsText(participant, participation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val checkResultsLink = participant?.checkResultsLink?.takeIf { it.isNotBlank() }
            if (
                checkResultsLink != null &&
                participant?.resultsPublicationInfoCode == "CONTEST_FINISHED_RESULTS_NOT_IN_MESH"
            ) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    runCatching { uriHandler.openUri(checkResultsLink) }
                }) {
                    Text(Words.OlympiadCheckResults.text)
                }
            }
        } else {
            DetailValue(
                Words.OlympiadDateTime.text,
                timeRange(model.startedAt, model.finishedAt)
            )
            val score = model.score
            if (score != null) {
                DetailValue(
                    Words.OlympiadScore.text,
                    "${numberText(score.mark)} / ${numberText(score.markScale)}"
                )
                ScoreRow(
                    label = Words.OlympiadPassed.text,
                    value = score.passedCount,
                    total = score.totalCount,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                ScoreRow(
                    label = Words.OlympiadPartial.text,
                    value = score.partialPassedCount,
                    total = score.totalCount,
                    color = MaterialTheme.colorScheme.primary,
                )
                ScoreRow(
                    label = Words.OlympiadWrong.text,
                    value = score.wrongCount,
                    total = score.totalCount,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            model.reward?.label?.takeIf { it.isNotBlank() }?.let {
                DetailValue(Words.OlympiadReward.text, it)
            }
            model.participationStatus?.let { status ->
                DetailValue(Words.OlympiadParticipationStatus.text, status.name)
                DetailValue(Words.OlympiadDescription.text, status.justification)
            }
            model.portfolioInfo?.takeIf { it.isNotBlank() }?.let {
                DetailValue(Words.OlympiadPortfolio.text, it)
            }
            val detailsUrl = model.detailsUrl?.takeIf { it.isNotBlank() }
            if (detailsUrl != null) {
                TextButton(onClick = { onOpenDetails(detailsUrl) }) {
                    Text(Words.OlympiadDetails.text)
                }
            }
        }
    }
}

private fun olympiadNoResultsText(
    participant: OlympiadParticipant?,
    participation: OlympiadParticipation,
): String = when {
    participation == OlympiadParticipation.NOT_PARTICIPATED -> Words.OlympiadNotParticipated.text
    participant?.resultsPublicationInfoCode == "CONTEST_FINISHED_RESULTS_NOT_EXIST" ->
        Words.OlympiadNotParticipated.text
    participant?.resultsPublicationInfoCode == "CONTEST_IN_PROGRESS_RESULTS_NOT_EXIST" ->
        Words.OlympiadResultsAfterParticipation.text
    participant?.resultsPublicationInfoCode == "CONTEST_IN_PROGRESS_RESULTS_EXIST" ||
        participant?.resultsPublicationInfoCode == "CONTEST_FINISHED_RESULTS_EXIST" ->
        Words.OlympiadResultsPending.text
    participant?.resultsPublicationInfoCode == "CONTEST_FINISHED_RESULTS_NOT_IN_MESH" ->
        Words.OlympiadResultsNotLoaded.text
    else -> Words.OlympiadNoResults.text
}

@Composable
private fun ScoreRow(label: String, value: Int?, total: Int?, color: Color) {
    if (value == null) return
    val max = total?.takeIf { it > 0 }
    val fraction = if (max != null) (value.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (max != null) "$value / $max" else value.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun OlympiadStatusBadge(statusCode: String?) {
    val label = when (statusCode) {
        "REGISTRATION" -> Words.OlympiadUpcoming.text
        "STARTED" -> Words.OlympiadRunning.text
        else -> Words.OlympiadFinished.text
    }
    val color = when (statusCode) {
        "REGISTRATION" -> MaterialTheme.colorScheme.tertiary
        "STARTED" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = CircleShape,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun OlympiadError(message: String, onRetry: () -> Unit) {
    Box(
        Modifier
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

private fun dateRange(start: String?, finish: String?): String {
    val locale = UiLanguage.locale
    val from = start?.takeIf { it.isNotBlank() }?.let { OlympiadText.formatDateTime(it, locale) }
    val to = finish?.takeIf { it.isNotBlank() }?.let { OlympiadText.formatDateTime(it, locale) }
    return when {
        from != null && to != null -> "$from — $to"
        from != null -> from
        to != null -> to
        else -> "—"
    }
}

private fun timeRange(start: String?, finish: String?): String {
    val locale = UiLanguage.locale
    val from = start?.takeIf { it.isNotBlank() }?.let { OlympiadText.formatDateTime(it, locale) }
    val to = finish?.takeIf { it.isNotBlank() }?.let { OlympiadText.formatDateTime(it, locale) }
    return when {
        from != null && to != null -> "$from — $to"
        from != null -> from
        to != null -> to
        else -> "—"
    }
}

private fun numberText(value: Double?): String {
    if (value == null) return "—"
    if (value % 1.0 == 0.0) return value.toInt().toString()
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}
