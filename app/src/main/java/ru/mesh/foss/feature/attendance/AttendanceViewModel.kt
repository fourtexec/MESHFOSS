package ru.mesh.foss.feature.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.core.MeshCore

data class AttendanceUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val from: String = attendanceWeekStart(),
    val to: String = attendanceWeekEnd(attendanceWeekStart()),
    val snapshot: AttendanceSnapshot? = null,
    val error: String? = null,
)

/** Состояние экрана посещаемости и загрузка данных. */
class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val studentId: String,
    private val personId: String,
) : ViewModel() {

    private val initialStart = attendanceWeekStart()
    private val _state = MutableStateFlow(
        AttendanceUiState(
            from = initialStart,
            to = attendanceWeekEnd(initialStart),
        )
    )
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

    init {
        load(initialStart)
    }

    fun previousWeek() {
        load(MeshCore.addDays(_state.value.from, -7))
    }

    fun nextWeek() {
        load(MeshCore.addDays(_state.value.from, 7))
    }

    fun refresh() {
        load(_state.value.from, refreshing = true)
    }

    private fun load(from: String, refreshing: Boolean = false) {
        val to = attendanceWeekEnd(from)
        _state.update { it.copy(loading = !refreshing, refreshing = refreshing, from = from, to = to, error = null) }
        viewModelScope.launch {
            try {
                val snapshot = repository.load(studentId, personId, from, to)
                _state.update { it.copy(snapshot = snapshot, error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = MeshCore.describeError(error)) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    companion object {
        fun factory(client: MeshClient, studentId: String?, personId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AttendanceViewModel(
                        repository = MeshAttendanceRepository(client),
                        studentId = studentId.orEmpty(),
                        personId = personId.orEmpty(),
                    ) as T
                }
            }
    }
}
