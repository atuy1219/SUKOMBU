package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// UIの状態を表す sealed interface
sealed interface TaskListUiState {
    object Loading : TaskListUiState
    data class Success(val tasks: List<Task>) : TaskListUiState
    data class Error(val message: String) : TaskListUiState
}

class TaskListViewModel(private val repository: ScombzRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskListUiState>(TaskListUiState.Loading)
    val uiState: StateFlow<TaskListUiState> = _uiState

    fun fetchTasks(sessionId: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = TaskListUiState.Loading
            try {
                val tasks = repository.getAllTasks(sessionId, forceRefresh)
                _uiState.value = TaskListUiState.Success(tasks)
            } catch (e: Exception) {
                _uiState.value = TaskListUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}