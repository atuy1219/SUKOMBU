package com.atuy.scomb.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.SessionManager
import com.atuy.scomb.util.WebViewLoginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    object RequiresTwoFactor : LoginUiState
    data class Success(val sessionId: String) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    application: Application,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    private var loginManager: WebViewLoginManager? = null

    init {
        loginManager = WebViewLoginManager(application.applicationContext)
    }

    fun startLogin(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            try {
                val result = loginManager?.login(username, password)

                result?.onSuccess { sessionId ->
                    saveSessionAndComplete(sessionId)
                }?.onFailure { exception ->
                    when (exception) {
                        is WebViewLoginManager.TwoFactorRequiredException -> {
                            _uiState.value = LoginUiState.RequiresTwoFactor
                        }

                        else -> {
                            _uiState.value = LoginUiState.Error(
                                exception.message ?: "ログインに失敗しました"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    e.message ?: "予期しないエラーが発生しました"
                )
            }
        }
    }

    fun submitTwoFactorCode(code: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            try {
                val result = loginManager?.submitTwoFactorCode(code)

                result?.onSuccess { sessionId ->
                    saveSessionAndComplete(sessionId)
                }?.onFailure { exception ->
                    _uiState.value = LoginUiState.Error(
                        exception.message ?: "二段階認証に失敗しました"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    e.message ?: "予期しないエラーが発生しました"
                )
            }
        }
    }

    fun cancelLogin() {
        loginManager?.cleanup()
        _uiState.value = LoginUiState.Idle
    }

    private suspend fun saveSessionAndComplete(sessionId: String) {
        sessionManager.saveSessionId(sessionId)
        _uiState.value = LoginUiState.Success(sessionId)
    }

    // 旧メソッド（互換性のため残す）
    fun onLoginSuccess(sessionId: String) {
        viewModelScope.launch {
            saveSessionAndComplete(sessionId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        loginManager?.cleanup()
    }
}