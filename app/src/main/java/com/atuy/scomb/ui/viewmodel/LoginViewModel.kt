package com.atuy.scomb.ui.viewmodel

import android.app.Application
import android.util.Log
import android.webkit.WebView
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
    private var credentials: Pair<String, String>? = null

    // 1. ユーザー名とパスワードを保持する
    fun setCredentials(username: String, password: String) {
        credentials = username to password
        _uiState.value = LoginUiState.Loading
    }

    // 2. ComposableからWebViewインスタンスを受け取ってログインを開始
    fun startLogin(webView: WebView) {
        val (username, password) = credentials ?: run {
            onError("Username and password are not set.")
            return
        }

        loginManager = WebViewLoginManager(getApplication()).apply {
            startLogin(webView, username, password, this@LoginViewModel)
        }
    }

    override fun onSuccess(sessionId: String) {
        Log.d("LoginViewModel_Debug", "onSuccess called with session ID.")
        if (_uiState.value is LoginUiState.Success) return
        viewModelScope.launch {
            sessionManager.saveSessionId(sessionId)
            _uiState.value = LoginUiState.Success(sessionId)
            cleanup()
        }
    }

    override fun onTwoFactorCodeExtracted(code: String) {
        Log.d("LoginViewModel_Debug", "onTwoFactorCodeExtracted called with code: $code")
        // 成功状態になっていなければ2FA画面を表示
        if (_uiState.value !is LoginUiState.Success) {
            _uiState.value = LoginUiState.RequiresTwoFactor(code)
        }
    }

    override fun onError(message: String) {
        Log.e("LoginViewModel_Debug", "onError called with message: $message")
        if (_uiState.value is LoginUiState.Success) return
        _uiState.value = LoginUiState.Error(message)
        cleanup()
    }

    override fun onLoginError(message: String) {
        onError(message)
    }

    fun cancelLogin() {
        Log.d("LoginViewModel_Debug", "cancelLogin called.")
        cleanup()
        _uiState.value = LoginUiState.Idle
    }

    // LoginScreenが破棄されたときに呼ばれる
    fun onWebViewDisposed() {
        Log.d("LoginViewModel_Debug", "onWebViewDisposed called.")
        // ログイン処理が完了していない場合のみ、状態をリセット
        if (_uiState.value !is LoginUiState.Success) {
            _uiState.value = LoginUiState.Idle
        }
        loginManager = null // WebViewはLoginScreen側で破棄されるため、参照をクリアするだけ
    }


    private fun cleanup() {
        loginManager?.cleanup()
        loginManager = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("LoginViewModel_Debug", "onCleared called.")
        cleanup()
    }
}

