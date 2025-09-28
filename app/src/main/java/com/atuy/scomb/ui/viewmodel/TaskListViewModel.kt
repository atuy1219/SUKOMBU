package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// UIの状態を表現するためのクラス
sealed interface TaskListUiState {
    object Loading : TaskListUiState
    data class Success(val tasks: List<Task>) : TaskListUiState
    data class Error(val message: String) : TaskListUiState
}

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: ScombzRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskListUiState>(TaskListUiState.Loading)
    val uiState: StateFlow<TaskListUiState> = _uiState

    init {
        // ViewModelが作られたら、すぐにタスクを取得開始
        fetchTasks(forceRefresh = false)
    }

    fun fetchTasks(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.value = TaskListUiState.Loading
            try {
                // TODO: ログイン機能実装後、動的にsessionIdを取得する
                val sessionId = "YOUR_SESSION_ID"
                val tasks = repository.getAllTasks(sessionId, forceRefresh)
                _uiState.value = TaskListUiState.Success(tasks)
            } catch (e: Exception) {
                _uiState.value = TaskListUiState.Error(e.message ?: "An unknown error occurred")
                e.printStackTrace()
            }
        }
    }
}