package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.manager.AuthManager
import com.atuy.scomb.data.manager.AutoRefreshManager
import com.atuy.scomb.data.manager.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val authManager: AuthManager,
    private val autoRefreshManager: AutoRefreshManager
) : ViewModel() {

    val themeMode: StateFlow<Int> = settingsManager.themeModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsManager.THEME_MODE_SYSTEM
        )

    val autoRefreshInterval: StateFlow<Long> = settingsManager.autoRefreshIntervalFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 60 // default 60 min
        )

    val username: StateFlow<String> = authManager.usernameFlow
        .map { it ?: "未ログイン" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setThemeMode(mode)
        }
    }

    fun setAutoRefreshInterval(minutes: Long) {
        viewModelScope.launch {
            settingsManager.setAutoRefreshInterval(minutes)
            // 設定変更時にWorkManagerを再スケジュール
            autoRefreshManager.scheduleAutoRefresh(minutes)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.clearAuthToken()
            autoRefreshManager.cancelAutoRefresh()
        }
    }
}