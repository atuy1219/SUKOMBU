// FILE: app/src/main/java/com/atuy/scomb/ui/viewmodel/TaskListViewModel.kt

package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.domain.ScheduleNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TaskListUiState {
    object Loading : TaskListUiState
    data class Success(val tasks: List<Task>) : TaskListUiState
    data class Error(val message: String) : TaskListUiState
}

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: ScombzRepository,
    private val scheduleNotificationsUseCase: ScheduleNotificationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskListUiState>(TaskListUiState.Loading)
    val uiState: StateFlow<TaskListUiState> = _uiState

    init {
        fetchTasks(forceRefresh = false)
    }

    fun fetchTasks(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.value = TaskListUiState.Loading
            try {
                val tasks = repository.getTasksAndSurveys(forceRefresh)
                _uiState.value = TaskListUiState.Success(tasks)
                scheduleNotificationsUseCase(tasks)
            } catch (e: Exception) {
                _uiState.value = TaskListUiState.Error(e.message ?: "An unknown error occurred")
                e.printStackTrace()
            }
        }
    }
}