package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

sealed interface TimetableUiState {
    object Loading : TimetableUiState
    // UIで扱いやすいように2次元配列でデータを保持
    data class Success(val timetable: Array<Array<ClassCell?>>) : TimetableUiState
    data class Error(val message: String) : TimetableUiState
}

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val repository: ScombzRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TimetableUiState>(TimetableUiState.Loading)
    val uiState: StateFlow<TimetableUiState> = _uiState

    init {
        // 現在の年月から年度と学期を自動で決定
        val calendar = Calendar.getInstance()
        val year = if (calendar.get(Calendar.MONTH) < 3) calendar.get(Calendar.YEAR) - 1 else calendar.get(Calendar.YEAR)
        val term = if (calendar.get(Calendar.MONTH) in 3..8) "1" else "2" // 4月-9月を前期(1), 10月-3月を後期(2)とする

        fetchTimetable(year, term, forceRefresh = false)
    }

    fun fetchTimetable(year: Int, term: String, forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.value = TimetableUiState.Loading
            try {
                val classCells = repository.getTimetable(year, term, forceRefresh)
                // 5曜日×7時限の2次元配列を作成
                val timetableGrid: Array<Array<ClassCell?>> = Array(5) { Array(7) { null } }
                classCells.forEach { cell ->
                    // ScombZの曜日は 0:月, 1:火 ...
                    if (cell.dayOfWeek in 0..4 && cell.period in 0..6) {
                        timetableGrid[cell.dayOfWeek][cell.period] = cell
                    }
                }
                _uiState.value = TimetableUiState.Success(timetableGrid)
            } catch (e: Exception) {
                _uiState.value = TimetableUiState.Error(e.message ?: "An unknown error occurred")
                e.printStackTrace()
            }
        }
    }
}