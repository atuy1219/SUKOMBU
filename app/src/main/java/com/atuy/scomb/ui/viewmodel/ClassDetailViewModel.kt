package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.ClassCellDao
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.db.TaskDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ClassDetailUiState {
    object Loading : ClassDetailUiState
    data class Success(val classCell: ClassCell, val tasks: List<Task>) : ClassDetailUiState
    data class Error(val message: String) : ClassDetailUiState
}

@HiltViewModel
class ClassDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val classCellDao: ClassCellDao,
    private val taskDao: TaskDao
) : ViewModel() {

    private val classId: String = savedStateHandle.get<String>("classId")!!

    private val _uiState = MutableStateFlow<ClassDetailUiState>(ClassDetailUiState.Loading)
    val uiState: StateFlow<ClassDetailUiState> = _uiState.asStateFlow()

    init {
        loadClassDetails()
    }

    fun loadClassDetails() {
        viewModelScope.launch {
            _uiState.value = ClassDetailUiState.Loading
            try {
                val classCells = classCellDao.getClassCellsById(classId)
                if (classCells.isEmpty()) {
                    _uiState.value = ClassDetailUiState.Error("授業情報が見つかりません。")
                } else {
                    val classCell = classCells.first()
                    val tasks = taskDao.getTasksByClassId(classId)
                    _uiState.value = ClassDetailUiState.Success(classCell, tasks)
                }
            } catch (e: Exception) {
                _uiState.value = ClassDetailUiState.Error(e.message ?: "データの読み込みに失敗しました。")
            }
        }
    }
}