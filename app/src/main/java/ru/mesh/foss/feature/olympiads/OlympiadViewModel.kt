package ru.mesh.foss.feature.olympiads

import android.content.Context
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
import org.json.JSONObject
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.OlympiadDetail
import ru.mesh.foss.data.model.OlympiadDictionaries
import ru.mesh.foss.data.model.OlympiadOverviewItem
import ru.mesh.foss.data.model.OlympiadParticipant
import ru.mesh.foss.data.model.OlympiadResult
import ru.mesh.foss.feature.cdz.CdzResultTask

enum class OlympiadTab { MY, ALL }

enum class OlympiadParticipation { UNKNOWN, NOT_PARTICIPATED, PARTICIPATED }

data class OlympiadDetailState(
    val contestId: String,
    val loading: Boolean = true,
    val detail: OlympiadDetail? = null,
    val participant: OlympiadParticipant? = null,
    val participation: OlympiadParticipation = OlympiadParticipation.UNKNOWN,
    val result: OlympiadResult? = null,
    val recommendation: String? = null,
    val error: String? = null,
)

data class OlympiadAttemptState(
    val detailsUrl: String,
    val loading: Boolean = true,
    val info: JSONObject? = null,
    val tasks: List<CdzResultTask> = emptyList(),
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
    val attempt: OlympiadAttemptState? = null,
)

private data class ParticipantLookup(
    val participant: OlympiadParticipant?,
    val participation: OlympiadParticipation,
)

/** Состояние списка олимпиад, поиска и карточки конкретной олимпиады. */
class OlympiadViewModel(
    private val repository: OlympiadRepository,
    private val attemptRepository: OlympiadAttemptRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OlympiadUiState())
    val state: StateFlow<OlympiadUiState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null
    private var attemptJob: Job? = null

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
                    val participant = async { loadParticipant(id) }
                    val loaded = detail.await()
                    val participantLookup = participant.await()

                    // 404 от `/participant` — это «не участвовал», но результат
                    // всё равно проверяем: если он есть, показываем его, а не
                    // подменяем состояние участия. Если результата нет, UI
                    // скажет «не участвовал», а не «результатов пока нет».
                    val result = async { optional { repository.result(id) } }
                    val recommendation = async { optional { repository.recommendation(id) } }
                    val resultValue = result.await()
                    val recommendationText = recommendation.await()?.recommendation

                    updateDetail(id) {
                        it.copy(
                            loading = false,
                            detail = loaded,
                            participant = participantLookup.participant,
                            participation = participantLookup.participation,
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

    private suspend fun loadParticipant(contestId: String): ParticipantLookup =
        try {
            val participant = repository.participant(contestId)
            if (participant == null || participant.passingState == "UNREGISTERED") {
                ParticipantLookup(participant, OlympiadParticipation.NOT_PARTICIPATED)
            } else {
                ParticipantLookup(participant, OlympiadParticipation.PARTICIPATED)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ParticipantLookup(null, OlympiadParticipation.UNKNOWN)
        }

    /**
     * «Подробнее»: разворачивает `detailsUrl` в ссылку попытки и открывает
     * её результат нативно. В браузер ничего не отправляем.
     */
    fun openDetails(detailsUrl: String) {
        val raw = detailsUrl.trim()
        if (raw.isEmpty()) return
        attemptJob?.cancel()
        _state.update { it.copy(attempt = OlympiadAttemptState(detailsUrl = raw)) }
        attemptJob = viewModelScope.launch {
            try {
                val link = repository.detailsLink(raw)
                    ?: throw IllegalStateException("olympiad details link is empty")
                val data = attemptRepository.load(link)
                _state.update { current ->
                    val attempt = current.attempt
                    if (attempt == null || attempt.detailsUrl != raw) {
                        current
                    } else {
                        current.copy(
                            attempt = attempt.copy(
                                loading = false,
                                info = data.info,
                                tasks = data.tasks,
                                error = null,
                            )
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { current ->
                    val attempt = current.attempt
                    if (attempt == null || attempt.detailsUrl != raw) {
                        current
                    } else {
                        current.copy(
                            attempt = attempt.copy(
                                loading = false,
                                error = MeshCore.describeError(error),
                            )
                        )
                    }
                }
            }
        }
    }

    fun retryAttempt() {
        _state.value.attempt?.detailsUrl?.let(::openDetails)
    }

    fun closeAttempt() {
        attemptJob?.cancel()
        _state.update { it.copy(attempt = null) }
    }

    fun retryDetail() {
        _state.value.detail?.contestId?.let(::openContest)
    }

    fun closeDetail() {
        detailJob?.cancel()
        attemptJob?.cancel()
        _state.update { it.copy(detail = null, attempt = null) }
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

        fun factory(client: MeshClient, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OlympiadViewModel(
                        repository = MeshOlympiadRepository(client),
                        attemptRepository = if (client.previewMode) {
                            PreviewOlympiadAttemptRepository()
                        } else {
                            UchebnikOlympiadAttemptRepository(context)
                        },
                    ) as T
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
