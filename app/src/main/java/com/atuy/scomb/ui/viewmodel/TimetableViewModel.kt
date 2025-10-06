package com.atuy.scomb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject


sealed interface TimetableUiState {
    object Loading : TimetableUiState
    data class Success(val timetable: Array<Array<ClassCell?>>) : TimetableUiState
    data class Error(val message: String) : TimetableUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val repository: ScombzRepository
) : ViewModel() {
    private val TAG = "ViewModel"


    private val _currentYear = MutableStateFlow(
        Calendar.getInstance().let {
            if (it.get(Calendar.MONTH) < 3) it.get(Calendar.YEAR) - 1 else it.get(Calendar.YEAR)
        }
    )
    val currentYear: StateFlow<Int> = _currentYear.asStateFlow()

    private val _currentTerm = MutableStateFlow(
        Calendar.getInstance().let {
            if (it.get(Calendar.MONTH) in 3..7) "1" else "2"
        }
    )
    val currentTerm: StateFlow<String> = _currentTerm.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    private val yearAndTermFlow = _currentYear.combine(_currentTerm) { year, term ->
        Pair(year, term)
    }

    val uiState: StateFlow<TimetableUiState> = combine(
        yearAndTermFlow,
        _refreshTrigger
    ) { yearAndTerm, refreshCount ->
        Triple(yearAndTerm.first, yearAndTerm.second, refreshCount)
    }
        .flatMapLatest { (year, term, refreshCount) ->
            flow {
                Log.d(
                    TAG,
                    "flatMapLatest triggered with Year=$year, Term=$term, RefreshCount=$refreshCount"
                )
                if (year != 0 && term.isNotEmpty()) {
                    emit(TimetableUiState.Loading)
                    try {
                        val classCells = repository.getTimetable(year, term, forceRefresh = true)
                        Log.d(
                            TAG,
                            "Successfully fetched ${classCells.size} classes for $year-$term"
                        )
                        val timetableGrid: Array<Array<ClassCell?>> = Array(5) { Array(7) { null } }
                        classCells.forEach { cell ->
                            if (cell.dayOfWeek in 0..4 && cell.period in 0..6) {
                                timetableGrid[cell.dayOfWeek][cell.period] = cell
                            }
                        }
                        emit(TimetableUiState.Success(timetableGrid))
                        Log.d(TAG, "UI state updated to Success.")
                    } catch (e: Exception) {
                        emit(TimetableUiState.Error(e.message ?: "不明なエラーが発生しました"))
                        Log.e(TAG, "Error fetching timetable", e)
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TimetableUiState.Loading
        )

    fun changeYearAndTerm(newYear: Int, newTerm: String) {
        Log.d(TAG, "changeYearAndTerm called with Year=$newYear, Term=$newTerm")
        _currentYear.value = newYear
        _currentTerm.value = newTerm
    }

    fun refresh() {
        Log.d(TAG, "Refresh triggered")
        _refreshTrigger.value++
    }
}

