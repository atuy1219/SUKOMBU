package com.atuy.scomb.ui.viewmodel

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
            if (sessionId != null) AuthState.Authenticated else AuthState.Unauthenticated
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AuthState.Loading
        )
}