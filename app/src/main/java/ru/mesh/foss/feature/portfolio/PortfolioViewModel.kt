package ru.mesh.foss.feature.portfolio

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
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.PortfolioAverageMark
import ru.mesh.foss.data.model.PortfolioContext
import ru.mesh.foss.data.model.PortfolioCulturalVisit
import ru.mesh.foss.data.model.PortfolioDiagnosticYear
import ru.mesh.foss.data.model.PortfolioEmployment
import ru.mesh.foss.data.model.PortfolioEvent
import ru.mesh.foss.data.model.PortfolioFinalYear
import ru.mesh.foss.data.model.PortfolioReward
import ru.mesh.foss.data.model.PortfolioWidget

data class PortfolioUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val context: PortfolioContext? = null,
    val personId: String? = null,
    val widgets: List<PortfolioWidget> = emptyList(),
    val events: List<PortfolioEvent> = emptyList(),
    val rewards: List<PortfolioReward> = emptyList(),
    val finalMarks: List<PortfolioFinalYear> = emptyList(),
    val averageMark: PortfolioAverageMark? = null,
    val diagnostics: List<PortfolioDiagnosticYear> = emptyList(),
    val employments: List<PortfolioEmployment> = emptyList(),
    val culturalVisits: List<PortfolioCulturalVisit> = emptyList(),
    val error: String? = null,
    val partialError: Boolean = false,
) {
    val hasAnyData: Boolean
        get() = widgets.isNotEmpty() ||
            events.isNotEmpty() ||
            rewards.isNotEmpty() ||
            finalMarks.isNotEmpty() ||
            averageMark != null ||
            diagnostics.isNotEmpty() ||
            employments.isNotEmpty() ||
            culturalVisits.isNotEmpty()
}

/** Состояние read-only экрана «Портфолио». */
class PortfolioViewModel(
    private val repository: PortfolioRepository,
    private val fallbackPersonId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioUiState())
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun refresh() {
        load(refreshing = true)
    }

    private fun load(refreshing: Boolean = false) {
        loadJob?.cancel()
        _state.update {
            it.copy(
                loading = it.context == null && !refreshing,
                refreshing = refreshing,
                error = null,
                partialError = false,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val context = repository.context()
                val personId = context.personId
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackPersonId?.takeIf { it.isNotBlank() }
                    ?: throw MeshCore.MeshError(Words.PortfolioUnavailable.text, "bad-portfolio")

                val loaded = coroutineScope {
                    val widgets = async { attempt { repository.widgets(personId) } }
                    val events = async { attempt { repository.events(personId) } }
                    val rewards = async { attempt { repository.rewards(personId) } }
                    val finalMarks = async { attempt { repository.finalMarks(personId) } }
                    val average = async { attempt { repository.averageMark(personId) } }
                    val diagnostics = async { attempt { repository.diagnostics(personId) } }
                    val employments = async { attempt { repository.employments(personId) } }
                    val cultural = async { attempt { repository.culturalVisits(personId) } }
                    PortfolioLoaded(
                        widgets = widgets.await(),
                        events = events.await(),
                        rewards = rewards.await(),
                        finalMarks = finalMarks.await(),
                        average = average.await(),
                        diagnostics = diagnostics.await(),
                        employments = employments.await(),
                        cultural = cultural.await(),
                    )
                }

                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        context = context,
                        personId = personId,
                        widgets = loaded.widgets.getOrDefault(emptyList()),
                        events = loaded.events.getOrDefault(emptyList())
                            .sortedByDescending { event -> event.startDate ?: event.endDate.orEmpty() },
                        rewards = loaded.rewards.getOrDefault(emptyList())
                            .sortedByDescending { reward -> reward.date.orEmpty() },
                        finalMarks = loaded.finalMarks.getOrDefault(emptyList())
                            .sortedByDescending { year -> year.yearTitle.orEmpty() },
                        averageMark = loaded.average.getOrNull(),
                        diagnostics = loaded.diagnostics.getOrDefault(emptyList())
                            .sortedByDescending { year -> year.learningYear.orEmpty() },
                        employments = loaded.employments.getOrDefault(emptyList()),
                        culturalVisits = loaded.cultural.getOrDefault(emptyList()),
                        error = null,
                        partialError = loaded.hasFailure,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = MeshCore.describeError(error),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(client: MeshClient, personId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PortfolioViewModel(MeshPortfolioRepository(client), personId) as T
                }
            }
    }
}

private data class PortfolioLoaded(
    val widgets: Result<List<PortfolioWidget>>,
    val events: Result<List<PortfolioEvent>>,
    val rewards: Result<List<PortfolioReward>>,
    val finalMarks: Result<List<PortfolioFinalYear>>,
    val average: Result<PortfolioAverageMark?>,
    val diagnostics: Result<List<PortfolioDiagnosticYear>>,
    val employments: Result<List<PortfolioEmployment>>,
    val cultural: Result<List<PortfolioCulturalVisit>>,
) {
    val hasFailure: Boolean
        get() = widgets.isFailure ||
            events.isFailure ||
            rewards.isFailure ||
            finalMarks.isFailure ||
            average.isFailure ||
            diagnostics.isFailure ||
            employments.isFailure ||
            cultural.isFailure
}

private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
