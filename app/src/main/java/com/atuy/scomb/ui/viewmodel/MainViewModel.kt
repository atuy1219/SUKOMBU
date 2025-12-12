package com.atuy.scomb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.manager.AuthManager
import com.atuy.scomb.data.manager.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface AuthState {
    object Loading : AuthState
    object Authenticated : AuthState
    object Unauthenticated : AuthState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    authManager: AuthManager,
    settingsManager: SettingsManager
) : ViewModel() {

    val authState: StateFlow<AuthState> = authManager.authTokenFlow
        .map { authToken ->
            if (authToken != null) {
                Log.d("MainViewModel_Debug", "Auth token found. Emitting Authenticated.")
                AuthState.Authenticated
            } else {
                Log.d("MainViewModel_Debug", "Auth token is null. Emitting Unauthenticated.")
                AuthState.Unauthenticated
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Loading
        )

    val themeMode: StateFlow<Int> = settingsManager.themeModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsManager.THEME_MODE_SYSTEM
        )
}