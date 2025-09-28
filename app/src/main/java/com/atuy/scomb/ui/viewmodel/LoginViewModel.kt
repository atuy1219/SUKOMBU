package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    object Idle : LoginUiState
    data class Success(val sessionId: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val sessionManager: SessionManager) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onLoginSuccess(sessionId: String) {
        viewModelScope.launch {
            sessionManager.saveSessionId(sessionId)
            _uiState.value = LoginUiState.Success(sessionId)
        }
    }
}