package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.manager.AuthManager
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: ScombzRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(username: String, password: String, twoFaCode: String?) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                // 1. ログイン処理
                val token = repository.login(username, password, twoFaCode)

                // 2. トークンとユーザー名を保存 (DataStoreへの保存はsuspend関数)
                authManager.saveAuthToken(token)
                authManager.saveUsername(username)

                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "ログインに失敗しました")
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}