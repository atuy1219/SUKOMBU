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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


sealed interface TimetableUiState {
    object Loading : TimetableUiState
    data class Success(
        val timetable: List<List<ClassCell?>>,
        val otherClasses: List<ClassCell> = emptyList(),
        val undoneTaskClassIds: Set<String> = emptySet(), // 未完了課題がある授業IDのセット
        val isRefreshing: Boolean = false,
        val displayWeekDays: Set<Int> = setOf(0, 1, 2, 3, 4, 5),
        val periodCount: Int = 7
    ) : TimetableUiState

    data class Error(val message: String, val isRefreshing: Boolean = false) : TimetableUiState
}

// 内部でのデータ取得状態管理用
private sealed interface TimetableDataState {
    object Loading : TimetableDataState
    data class Success(
        val timetable: List<List<ClassCell?>>,
        val otherClasses: List<ClassCell>,
        val undoneTaskClassIds: Set<String>,
        val isRefreshing: Boolean
    ) : TimetableDataState
    data class Error(val message: String, val isRefreshing: Boolean) : TimetableDataState
}

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val repository: ScombzRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {
    private val TAG = "TimetableViewModel"

    // 内部的なデータのロード状態
    private val _dataState = MutableStateFlow<TimetableDataState>(TimetableDataState.Loading)

    // 設定値のフロー
    private val settingsFlow = combine(
        settingsManager.displayWeekDaysFlow,
        settingsManager.timetablePeriodCountFlow
    ) { displayWeekDays, periodCount ->
        Pair(displayWeekDays, periodCount)
    }

    // UI状態はデータ状態と設定値を合成して生成する
    val uiState: StateFlow<TimetableUiState> = combine(
        _dataState,
        settingsFlow
    ) { dataState, (displayWeekDays, periodCount) ->
        when (dataState) {
            is TimetableDataState.Loading -> TimetableUiState.Loading
            is TimetableDataState.Success -> TimetableUiState.Success(
                timetable = dataState.timetable,
                otherClasses = dataState.otherClasses,
                undoneTaskClassIds = dataState.undoneTaskClassIds,
                isRefreshing = dataState.isRefreshing,
                displayWeekDays = displayWeekDays,
                periodCount = periodCount
            )
            is TimetableDataState.Error -> TimetableUiState.Error(
                message = dataState.message,
                isRefreshing = dataState.isRefreshing
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TimetableUiState.Loading
    )

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

            // 現在の状態がSuccessならリフレッシュ表示にする
            val currentDataState = _dataState.value
            if (forceRefresh && currentDataState is TimetableDataState.Success) {
                _dataState.value = currentDataState.copy(isRefreshing = true)
            } else {
                _dataState.value = TimetableDataState.Loading
            }

            try {
                coroutineScope {
                    // 並列でデータ取得
                    // 課題情報の取得 (キャッシュ優先)
                    val tasksDeferred = async { repository.getTasksAndSurveys(false) }
                    // 時間割の取得
                    val timetableDeferred = async { repository.getTimetable(year, term, forceRefresh) }

                    val tasks = tasksDeferred.await()
                    val classCells = timetableDeferred.await()

                    Log.d(TAG, "Successfully fetched ${classCells.size} classes for $year-$term")

                    val timetableGrid = List(6) { day -> // 月〜土 (0-5)
                        List(7) { period -> // 1-7限 (0-6)
                            classCells.find { it.dayOfWeek == day && it.period == period }
                        }
                    }

                    val otherClasses = classCells.filter { it.period == 8 || it.dayOfWeek == 8 }
                    Log.d(TAG, "Found ${otherClasses.size} 'other' classes")

                    // 未完了かつ期限内の課題がある授業IDを抽出
                    val currentTime = System.currentTimeMillis()
                    val undoneTaskClassIds = tasks
                        .filter { !it.done && it.deadline > currentTime }
                        .map { it.classId }
                        .toSet()

                    _dataState.value = TimetableDataState.Success(
                        timetable = timetableGrid,
                        otherClasses = otherClasses,
                        undoneTaskClassIds = undoneTaskClassIds,
                        isRefreshing = false
                    )
                }

            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d(TAG, "loadData Job was cancelled.")
                    throw e
                }
                val message = e.message ?: "不明なエラーが発生しました"
                _dataState.value = TimetableDataState.Error(message, isRefreshing = false)
                Log.e(TAG, "Error fetching timetable: $message", e)
            }
        }
    }
}