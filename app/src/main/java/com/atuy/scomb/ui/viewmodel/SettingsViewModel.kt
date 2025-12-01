package com.atuy.scomb.ui.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.R
import com.atuy.scomb.data.AuthManager
import com.atuy.scomb.data.SettingsManager
import com.atuy.scomb.domain.ScheduleNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val notificationTimings: Set<Int> = emptySet(),
    val showHomeNews: Boolean = true,
    val isDebugMode: Boolean = false,
    val displayWeekDays: Set<Int> = setOf(0, 1, 2, 3, 4, 5), // 0=月 ... 5=土
    val timetablePeriodCount: Int = 7,
    val themeMode: Int = SettingsManager.THEME_MODE_SYSTEM
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val settingsManager: SettingsManager,
    private val scheduleNotificationsUseCase: ScheduleNotificationsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 6つのFlowを結合するために、下部で定義した拡張combine関数を使用します
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsManager.notificationTimingsFlow,
        settingsManager.showHomeNewsFlow,
        settingsManager.debugModeFlow,
        settingsManager.displayWeekDaysFlow,
        settingsManager.timetablePeriodCountFlow,
        settingsManager.themeModeFlow
    ) { timings, showNews, debugMode, weekDays, periodCount, themeMode ->
        SettingsUiState(
            notificationTimings = timings.mapNotNull { it.toIntOrNull() }.toSet(),
            showHomeNews = showNews,
            isDebugMode = debugMode,
            displayWeekDays = weekDays,
            timetablePeriodCount = periodCount,
            themeMode = themeMode
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState()
        )

    private var versionTapCount = 0

    fun updateNotificationTimings(timings: Set<Int>) {
        viewModelScope.launch {
            settingsManager.setNotificationTimings(timings.map { it.toString() }.toSet())
        }
    }

    fun updateShowHomeNews(show: Boolean) {
        viewModelScope.launch {
            settingsManager.setShowHomeNews(show)
        }
    }

    fun updateDisplayWeekDays(days: Set<Int>) {
        viewModelScope.launch {
            settingsManager.setDisplayWeekDays(days)
        }
    }

    fun updateTimetablePeriodCount(count: Int) {
        viewModelScope.launch {
            settingsManager.setTimetablePeriodCount(count)
        }
    }

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setThemeMode(mode)
        }
    }

    fun onVersionClick() {
        if (uiState.value.isDebugMode) return

        versionTapCount++
        if (versionTapCount >= 7) {
            viewModelScope.launch {
                settingsManager.setDebugMode(true)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_debug_mode_enabled),
                    Toast.LENGTH_SHORT
                ).show()
            }
            versionTapCount = 0
        }
    }

    fun disableDebugMode() {
        viewModelScope.launch {
            settingsManager.setDebugMode(false)
            Toast.makeText(
                context,
                context.getString(R.string.settings_debug_mode_disabled),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun scheduleTestNotification() {
        scheduleNotificationsUseCase.scheduleTestNotification()
    }

    fun logout() {
        viewModelScope.launch {
            authManager.clearAuthToken()
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> = combine(listOf(flow1, flow2, flow3, flow4, flow5, flow6)) { args ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6
    )
}