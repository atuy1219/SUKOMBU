package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.AuthManager
import com.atuy.scomb.data.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val notificationTimings: Set<Int> = emptySet()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsManager.notificationTimingsFlow
        .map { timings ->
            SettingsUiState(notificationTimings = timings.mapNotNull { it.toIntOrNull() }.toSet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState()
        )

    fun updateNotificationTimings(timings: Set<Int>) {
        viewModelScope.launch {
            settingsManager.setNotificationTimings(timings.map { it.toString() }.toSet())
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.clearAuthToken()
        }
    }
}
