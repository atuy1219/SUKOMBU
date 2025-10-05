package com.atuy.scomb.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.SessionManager
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
class MainViewModel @Inject constructor(sessionManager: SessionManager) : ViewModel() {
    val authState: StateFlow<AuthState> = sessionManager.sessionIdFlow
        .map { sessionId ->
            if (sessionId != null) {
                Log.d("MainViewModel_Debug", "Session ID found. Emitting Authenticated.")
                AuthState.Authenticated
            } else {
                Log.d("MainViewModel_Debug", "Session ID is null. Emitting Unauthenticated.")
                AuthState.Unauthenticated
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Loading
        )
}

