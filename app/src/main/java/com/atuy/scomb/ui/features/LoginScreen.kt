package com.atuy.scomb.ui.features

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.ui.viewmodel.LoginUiState
import com.atuy.scomb.ui.viewmodel.LoginViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var webView by remember { mutableStateOf<WebView?>(null) }

    if (uiState is LoginUiState.Loading && webView == null) {
        AndroidView(
            modifier = Modifier.size(0.dp), // 画面には表示しない
            factory = {
                WebView(it).apply {
                    viewModel.startLogin(this)
                    webView = this
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("LoginScreen_Debug", "DisposableEffect onDispose, cleaning up WebView.")
            webView?.destroy()
            webView = null
            viewModel.onWebViewDisposed()
        }
    }


    when (val state = uiState) {
        is LoginUiState.Idle, is LoginUiState.Error -> {
            LoginForm(
                onLogin = { username, password ->
                    viewModel.setCredentials(username, password)
                },
                isLoading = false,
                errorMessage = if (state is LoginUiState.Error) state.message else null
            )
        }

        is LoginUiState.Loading -> {
            LoginForm(
                onLogin = { _, _ -> },
                onCancel = { viewModel.cancelLogin() },
                isLoading = true,
                errorMessage = null
            )
        }

        is LoginUiState.RequiresTwoFactor -> {
            TwoFactorChallengeScreen(
                code = state.code,
                onCancel = { viewModel.cancelLogin() }
            )
        }

        is LoginUiState.Success -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun LoginForm(
    onLogin: (String, String) -> Unit,
    onCancel: () -> Unit = {},
    errorMessage: String? = null,
    isLoading: Boolean = false
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ScombZにログイン",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("学籍番号 (scombz@sic)") },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("パスワード") },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "パスワードを隠す" else "パスワードを表示する"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            onLogin(username, password)
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onLogin(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("ログイン")
                }
            }
            if (isLoading) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("キャンセル")
                }
            }
        }
    }
}

@Composable
fun TwoFactorChallengeScreen(
    code: String,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "二段階認証",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "認証アプリで以下の番号が表示されていることを確認し、承認してください。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = code,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            CircularProgressIndicator()

            Text(
                text = "承認後、自動的にログインします...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("キャンセル")
            }
        }
    }
}

