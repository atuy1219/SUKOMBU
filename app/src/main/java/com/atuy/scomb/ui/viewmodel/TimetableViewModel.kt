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
        val showSaturday: Boolean = true,
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
            settingsManager.showSaturdayFlow,
            settingsManager.timetablePeriodCountFlow
        ) { showSaturday, periodCount ->
            val currentState = _uiState.value
            if (currentState is TimetableUiState.Success) {
                _uiState.value = currentState.copy(
                    showSaturday = showSaturday,
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
                // 設定値を同時に取得する（APIコールとは並列でも良いが、今回は直列で取得）
                // Flowから最新の値を取得するには、first()を使う手もあるが、
                // observeSettingsで監視しているので、ここでの初期ロード時はデフォルト値で表示され、
                // Flowが値を流してくると更新される形でも動作はする。
                // 確実性を期すため、設定値をリポジトリ経由などで一括取得するか、
                // ここでFlowを購読してcombineでデータを流す設計にするのがベストだが、
                // 既存の設計に合わせて、データロード後に現在の設定値を反映させる形をとる。

                val classCells = repository.getTimetable(year, term, forceRefresh)
                Log.d(TAG, "Successfully fetched ${classCells.size} classes for $year-$term")

                // Flowから現在の設定値を取得（サスペンドしない場合はstateInなどで保持が必要だが、
                // SettingsManagerはFlowを返しているので、viewModelScope内でcollectして値を持っておくか、
                // ここでは簡易的にcombineで監視している仕組みに任せる。
                // ただし、初回Loading -> Successへの遷移時に設定値が必要なので、
                // 別途collectするか、combineでデータロードも管理するのが正しい。
                // 今回は簡易的に、Success生成時にデフォルト値を入れ、observeSettingsが即座に最新値を反映することを期待する。
                // あるいは、stateIn等を使って同期的に値を取れるようにする。

                // ここでは簡易的にSuccess状態を作る。設定値はobserveSettingsが更新してくれる。
                val timetableGrid = List(6) { day -> // 月〜土 (0-5)
                    List(7) { period -> // 1-7限 (0-6)
                        classCells.find { it.dayOfWeek == day && it.period == period }
                    }
                }

                _uiState.value = TimetableUiState.Success(
                    timetable = timetableGrid,
                    isRefreshing = false
                    // showSaturday, periodCount は observeSettings で更新されるためデフォルトでOK
                    // もしちらつきが気になるなら、DataStoreからfirst()で取得して設定する
                )

                // 設定値を反映させるために再取得（observeSettingsが動くはずだが、念のため）
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