package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.domain.ScheduleNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    data class Success(val tasks: List<Task>, val filter: TaskFilter) : TaskListUiState
    data class Error(val message: String) : TaskListUiState
}

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: ScombzRepository,
    private val scheduleNotificationsUseCase: ScheduleNotificationsUseCase
) : ViewModel() {

    private val _allTasks = MutableStateFlow<List<Task>>(emptyList())
    private val _filter = MutableStateFlow(TaskFilter())
    private val _uiState = MutableStateFlow<TaskListUiState>(TaskListUiState.Loading)
    val uiState: StateFlow<TaskListUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(_allTasks, _filter) { tasks, filter ->
                if (_uiState.value !is TaskListUiState.Loading) {
                    val filteredTasks = filterTasks(tasks, filter)
                    _uiState.value = TaskListUiState.Success(filteredTasks, filter)
                }
            }.collect{}
        }
        fetchTasks(forceRefresh = false)
    }

    fun fetchTasks(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.value = TaskListUiState.Loading
            try {
                val tasks = repository.getTasksAndSurveys(forceRefresh)
                _allTasks.value = tasks
                val filteredTasks = filterTasks(tasks, _filter.value)
                _uiState.value = TaskListUiState.Success(filteredTasks, _filter.value)
                scheduleNotificationsUseCase(tasks)
            } catch (e: Exception) {
                _uiState.value = TaskListUiState.Error(e.message ?: "不明なエラーが発生しました")
                e.printStackTrace()
            }
        }
    }

    fun updateFilter(newFilter: TaskFilter) {
        _filter.value = newFilter
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





