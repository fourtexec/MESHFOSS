package ru.mesh.foss.feature.olympiads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.OlympiadDetail
import ru.mesh.foss.data.model.OlympiadDictionaries
import ru.mesh.foss.data.model.OlympiadOverviewItem
import ru.mesh.foss.data.model.OlympiadResult

enum class OlympiadTab { MY, ALL }

data class OlympiadDetailState(
    val contestId: String,
    val loading: Boolean = true,
    val detail: OlympiadDetail? = null,
    val result: OlympiadResult? = null,
    val recommendation: String? = null,
    val error: String? = null,
)

data class OlympiadUiState(
    val tab: OlympiadTab = OlympiadTab.MY,
    val query: String = "",
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val items: List<OlympiadOverviewItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val dictionaries: OlympiadDictionaries = OlympiadDictionaries(),
    val error: String? = null,
    val detail: OlympiadDetailState? = null,
)

/** Состояние списка олимпиад, поиска и карточки конкретной олимпиады. */
class OlympiadViewModel(
    private val repository: OlympiadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OlympiadUiState())
    val state: StateFlow<OlympiadUiState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null

    init {
        loadDictionaries()
        load(reset = true)
    }

    fun setTab(tab: OlympiadTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab, query = "", items = emptyList(), page = 0, total = 0) }
        load(reset = true)
    }

    fun search(query: String) {
        val clean = query.trim()
        _state.update { it.copy(query = clean, items = emptyList(), page = 0, total = 0) }
        load(reset = true)
    }

    fun refresh() {
        load(reset = true, refreshing = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.items.size >= current.total) return
        load(reset = false, more = true)
    }

    fun openContest(contestId: String) {
        val id = contestId.trim()
        if (id.isBlank()) return
        detailJob?.cancel()
        _state.update { it.copy(detail = OlympiadDetailState(contestId = id)) }
        detailJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val detail = async { repository.detail(id) }
                    val result = async { optional { repository.result(id) } }
                    val recommendation = async { optional { repository.recommendation(id) } }
                    val loaded = detail.await()
                    val resultValue = result.await()
                    val recommendationText = recommendation.await()?.recommendation
                    updateDetail(id) {
                        it.copy(
                            loading = false,
                            detail = loaded,
                            result = resultValue,
                            recommendation = recommendationText,
                            error = null,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateDetail(id) {
                    it.copy(loading = false, error = MeshCore.describeError(error))
                }
            }
        }
    }

    fun retryDetail() {
        _state.value.detail?.contestId?.let(::openContest)
    }

    fun closeDetail() {
        detailJob?.cancel()
        _state.update { it.copy(detail = null) }
    }

    private fun loadDictionaries() {
        viewModelScope.launch {
            val dictionaries = optional { repository.dictionaries() } ?: return@launch
            _state.update { it.copy(dictionaries = dictionaries) }
        }
    }

    private fun load(reset: Boolean, refreshing: Boolean = false, more: Boolean = false) {
        val snapshot = _state.value
        val tab = snapshot.tab
        val query = snapshot.query
        val page = if (reset) 0 else snapshot.page + 1
        _state.update {
            it.copy(
                loading = reset && !refreshing,
                refreshing = refreshing,
                loadingMore = more,
                error = null,
            )
        }
        listJob?.cancel()
        listJob = viewModelScope.launch {
            try {
                val response = repository.overview(
                    showParticipantContests = tab == OlympiadTab.MY,
                    searchString = query,
                    page = page,
                    size = PAGE_SIZE,
                )
                _state.update { current ->
                    current.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        items = if (reset) response.items else current.items + response.items,
                        total = response.total,
                        page = page,
                        error = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = MeshCore.describeError(error),
                    )
                }
            }
        }
    }

    private fun updateDetail(contestId: String, transform: (OlympiadDetailState) -> OlympiadDetailState) {
        _state.update { current ->
            val detail = current.detail
            if (detail == null || detail.contestId != contestId) {
                current
            } else {
                current.copy(detail = transform(detail))
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 20

        fun factory(client: MeshClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OlympiadViewModel(MeshOlympiadRepository(client)) as T
                }
            }
    }
}

/** Необязательный запрос: 404 и сетевые ошибки не должны ломать основную карточку. */
private suspend fun <T> optional(block: suspend () -> T): T? =
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
