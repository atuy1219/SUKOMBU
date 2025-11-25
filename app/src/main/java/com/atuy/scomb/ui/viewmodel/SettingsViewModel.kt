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
    val showSaturday: Boolean = true,
    val timetablePeriodCount: Int = 7
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val settingsManager: SettingsManager,
    private val scheduleNotificationsUseCase: ScheduleNotificationsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsManager.notificationTimingsFlow,
        settingsManager.showHomeNewsFlow,
        settingsManager.debugModeFlow,
        settingsManager.showSaturdayFlow,
        settingsManager.timetablePeriodCountFlow
    ) { timings, showNews, debugMode, showSat, periodCount ->
        SettingsUiState(
            notificationTimings = timings.mapNotNull { it.toIntOrNull() }.toSet(),
            showHomeNews = showNews,
            isDebugMode = debugMode,
            showSaturday = showSat,
            timetablePeriodCount = periodCount
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

    fun updateShowSaturday(show: Boolean) {
        viewModelScope.launch {
            settingsManager.setShowSaturday(show)
        }
    }

    fun updateTimetablePeriodCount(count: Int) {
        viewModelScope.launch {
            settingsManager.setTimetablePeriodCount(count)
        }
    }

    fun onVersionClick() {
        if (uiState.value.isDebugMode) return

        versionTapCount++
        if (versionTapCount >= 7) {
            viewModelScope.launch {
                settingsManager.setDebugMode(true)
                Toast.makeText(context, context.getString(R.string.settings_debug_mode_enabled), Toast.LENGTH_SHORT).show()
            }
            versionTapCount = 0
        }
    }

    fun disableDebugMode() {
        viewModelScope.launch {
            settingsManager.setDebugMode(false)
            Toast.makeText(context, context.getString(R.string.settings_debug_mode_disabled), Toast.LENGTH_SHORT).show()
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