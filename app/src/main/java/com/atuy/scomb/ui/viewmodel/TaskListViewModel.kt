package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.domain.ScheduleNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskFilter(
    val showAssignments: Boolean = true,
    val showTests: Boolean = true,
    val showSurveys: Boolean = true,
    val showCompleted: Boolean = false
)

sealed interface TaskListUiState {
    object Loading : TaskListUiState
    data class Success(
        val tasks: List<Task>,
        val filter: TaskFilter,
        val isRefreshing: Boolean = false
    ) : TaskListUiState

    data class Error(val message: String, val isRefreshing: Boolean = false) : TaskListUiState
}

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: ScombzRepository,
    private val scheduleNotificationsUseCase: ScheduleNotificationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskListUiState>(TaskListUiState.Loading)
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    private var allTasks: List<Task> = emptyList()
    private var loadJob: Job? = null

    private val _openUrlEvent = Channel<String>(Channel.BUFFERED)
    val openUrlEvent = _openUrlEvent.receiveAsFlow()

    init {
        fetchTasks(forceRefresh = false)
    }

    fun fetchTasks(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val currentState = _uiState.value
            val currentFilter =
                if (currentState is TaskListUiState.Success) currentState.filter else TaskFilter()

            if (forceRefresh && currentState is TaskListUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            } else {
                _uiState.value = TaskListUiState.Loading
            }

            try {
                allTasks = repository.getTasksAndSurveys(forceRefresh)
                _uiState.value = TaskListUiState.Success(
                    tasks = filterTasks(allTasks, currentFilter),
                    filter = currentFilter,
                    isRefreshing = false
                )
                scheduleNotificationsUseCase(allTasks)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value =
                    TaskListUiState.Error(e.message ?: "不明なエラーが発生しました", isRefreshing = false)
                e.printStackTrace()
            }
        }
    }

    fun onTaskClick(task: Task) {
        viewModelScope.launch {
            try {
                val url = repository.getTaskUrl(task)
                _openUrlEvent.send(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateFilter(newFilter: TaskFilter) {
        val currentState = _uiState.value
        if (currentState is TaskListUiState.Success) {
            _uiState.value = currentState.copy(
                tasks = filterTasks(allTasks, newFilter),
                filter = newFilter
            )
        }
    }


    private fun filterTasks(tasks: List<Task>, filter: TaskFilter): List<Task> {
        return tasks.filter { task ->
            val typeMatch = when (task.taskType) {
                0 -> filter.showAssignments
                1 -> filter.showTests
                2 -> filter.showSurveys
                else -> true
            }
            val completionMatch = if (filter.showCompleted) {
                true
            } else {
                !task.done
            }
            typeMatch && completionMatch
        }.sortedBy { it.deadline }
    }
}
