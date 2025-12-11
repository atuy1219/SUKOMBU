package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.manager.AuthManager
import com.atuy.scomb.data.manager.AutoRefreshManager
import com.atuy.scomb.data.manager.SettingsManager
import com.atuy.scomb.domain.ScheduleNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: Int = SettingsManager.THEME_MODE_SYSTEM,
    val showHomeNews: Boolean = SettingsManager.DEFAULT_SHOW_HOME_NEWS,
    val displayWeekDays: Set<Int> = setOf(0, 1, 2, 3, 4, 5),
    val timetablePeriodCount: Int = SettingsManager.DEFAULT_TIMETABLE_PERIOD_COUNT,
    val notificationTimings: Set<Int> = setOf(60),
    val isDebugMode: Boolean = SettingsManager.DEFAULT_DEBUG_MODE,
    val autoRefreshInterval: Long = SettingsManager.DEFAULT_AUTO_REFRESH_INTERVAL,
    val username: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val authManager: AuthManager,
    private val autoRefreshManager: AutoRefreshManager,
    private val scheduleNotificationsUseCase: ScheduleNotificationsUseCase
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsManager.themeModeFlow,
        settingsManager.showHomeNewsFlow,
        settingsManager.displayWeekDaysFlow,
        settingsManager.timetablePeriodCountFlow,
        settingsManager.notificationTimingsFlow,
        settingsManager.debugModeFlow,
        settingsManager.autoRefreshIntervalFlow,
        authManager.usernameFlow
    ) { params ->
        val themeMode = params[0] as Int
        val showHomeNews = params[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val displayWeekDays = params[2] as Set<Int>
        val timetablePeriodCount = params[3] as Int
        @Suppress("UNCHECKED_CAST")
        val notificationTimingsStrings = params[4] as Set<String>
        val isDebugMode = params[5] as Boolean
        val autoRefreshInterval = params[6] as Long
        val username = params[7] as? String ?: "未ログイン"

        val notificationTimings = notificationTimingsStrings.mapNotNull { it.toIntOrNull() }.toSet()

        SettingsUiState(
            themeMode = themeMode,
            showHomeNews = showHomeNews,
            displayWeekDays = displayWeekDays,
            timetablePeriodCount = timetablePeriodCount,
            notificationTimings = notificationTimings,
            isDebugMode = isDebugMode,
            autoRefreshInterval = autoRefreshInterval,
            username = username
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setThemeMode(mode)
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

    fun updateNotificationTimings(timings: Set<Int>) {
        viewModelScope.launch {
            val timingsStrings = timings.map { it.toString() }.toSet()
            settingsManager.setNotificationTimings(timingsStrings)
        }
    }

    fun scheduleTestNotification() {
        scheduleNotificationsUseCase.scheduleTestNotification()
    }

    fun disableDebugMode() {
        viewModelScope.launch {
            settingsManager.setDebugMode(false)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate", "unused")
    fun setAutoRefreshInterval(minutes: Long) {
        viewModelScope.launch {
            settingsManager.setAutoRefreshInterval(minutes)
            autoRefreshManager.scheduleAutoRefresh(minutes)
        }
    }

    fun onVersionClick() {
        val currentDebug = uiState.value.isDebugMode
        if (!currentDebug) {
            viewModelScope.launch {
                settingsManager.setDebugMode(true)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.clearAuthToken()
            autoRefreshManager.cancelAutoRefresh()
        }
    }
}