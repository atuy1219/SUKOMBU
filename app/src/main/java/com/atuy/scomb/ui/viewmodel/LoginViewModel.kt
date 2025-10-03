package com.atuy.scomb.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.SessionManager
import com.atuy.scomb.util.LoginListener
import com.atuy.scomb.util.WebViewLoginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    data class RequiresTwoFactor(val code: String) : LoginUiState
    data class Success(val sessionId: String) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    application: Application,
    private val sessionManager: SessionManager
) : AndroidViewModel(application), LoginListener {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    private var loginManager: WebViewLoginManager? = null

    fun startLogin(username: String, password: String) {
        _uiState.value = LoginUiState.Loading
        // 既存のログイン処理があれば破棄して新しい処理を開始
        loginManager?.cleanup()
        loginManager = WebViewLoginManager(getApplication()).apply {
            startLogin(username, password, this@LoginViewModel)
        }
    }


    override fun onSuccess(sessionId: String) {
        viewModelScope.launch {
            saveSessionAndComplete(sessionId)
        }
    }

    override fun onTwoFactorCodeExtracted(code: String) {
        _uiState.value = LoginUiState.RequiresTwoFactor(code)
    }

    override fun onError(message: String) {
        _uiState.value = LoginUiState.Error(message)
        loginManager = null
    }

    override fun onLoginError(message: String) {

        onError(message)
    }


    fun cancelLogin() {
        loginManager?.cleanup()
        loginManager = null
        _uiState.value = LoginUiState.Idle
    }

    private suspend fun saveSessionAndComplete(sessionId: String) {
        sessionManager.saveSessionId(sessionId)
        _uiState.value = LoginUiState.Success(sessionId)
        loginManager = null
    }

    override fun onCleared() {
        super.onCleared()
        loginManager?.cleanup()
    }
}


