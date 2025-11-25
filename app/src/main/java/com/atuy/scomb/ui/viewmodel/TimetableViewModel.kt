package com.atuy.scomb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.SettingsManager
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject


sealed interface TimetableUiState {
    object Loading : TimetableUiState
    data class Success(
        val timetable: List<List<ClassCell?>>,
        val isRefreshing: Boolean = false,
        val displayWeekDays: Set<Int> = setOf(0, 1, 2, 3, 4, 5),
        val periodCount: Int = 7
    ) : TimetableUiState

    data class Error(val message: String, val isRefreshing: Boolean = false) : TimetableUiState
}

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val repository: ScombzRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {
    private val TAG = "TimetableViewModel"

    private val _uiState = MutableStateFlow<TimetableUiState>(TimetableUiState.Loading)
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    private val _currentYear = MutableStateFlow(
        DateUtils.getCurrentScombTerm().year
    )
    val currentYear: StateFlow<Int> = _currentYear.asStateFlow()

    private val _currentTerm = MutableStateFlow(
        DateUtils.getCurrentScombTerm().term
    )
    val currentTerm: StateFlow<String> = _currentTerm.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadData(forceRefresh = false)
        observeSettings()
    }

    private fun observeSettings() {
        // 設定が変更されたらUI状態を更新する
        combine(
            settingsManager.displayWeekDaysFlow,
            settingsManager.timetablePeriodCountFlow
        ) { displayWeekDays, periodCount ->
            val currentState = _uiState.value
            if (currentState is TimetableUiState.Success) {
                _uiState.value = currentState.copy(
                    displayWeekDays = displayWeekDays,
                    periodCount = periodCount
                )
            }
        }.launchIn(viewModelScope)
    }

    fun changeYearAndTerm(newYear: Int, newTerm: String) {
        _currentYear.value = newYear
        _currentTerm.value = newTerm
        loadData(forceRefresh = false)
    }

    fun refresh() {
        loadData(forceRefresh = true)
    }

    private fun loadData(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val year = _currentYear.value
            val term = _currentTerm.value
            Log.d(TAG, "loadData called for $year-$term, forceRefresh=$forceRefresh")

            val currentState = _uiState.value
            if (forceRefresh && currentState is TimetableUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            } else {
                _uiState.value = TimetableUiState.Loading
            }

            try {
                val classCells = repository.getTimetable(year, term, forceRefresh)
                Log.d(TAG, "Successfully fetched ${classCells.size} classes for $year-$term")

                val timetableGrid = List(6) { day -> // 月〜土 (0-5)
                    List(7) { period -> // 1-7限 (0-6)
                        classCells.find { it.dayOfWeek == day && it.period == period }
                    }
                }

                _uiState.value = TimetableUiState.Success(
                    timetable = timetableGrid,
                    isRefreshing = false
                    // displayWeekDays, periodCount は observeSettings で更新される
                )

            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d(TAG, "loadData Job was cancelled.")
                    throw e
                }
                val message = e.message ?: "不明なエラーが発生しました"
                _uiState.value = TimetableUiState.Error(message, isRefreshing = false)
                Log.e(TAG, "Error fetching timetable: $message", e)
            }
        }
    }
}