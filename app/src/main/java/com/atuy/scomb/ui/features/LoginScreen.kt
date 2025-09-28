package com.atuy.scomb.ui.features

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.ui.viewmodel.LoginUiState
import com.atuy.scomb.ui.viewmodel.LoginViewModel
import androidx.compose.runtime.getValue

const val SCOMB_LOGIN_PAGE_URL = "https://scombz.shibaura-it.ac.jp/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust"
const val SCOMB_HOME_URL = "https://scombz.shibaura-it.ac.jp/portal/home"

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ログイン成功を検知したら、引数で渡された関数を実行
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    // ComposeからAndroidのWebViewを呼び出す
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // ページ読み込み完了ごとにCookieをチェック
                    if (url == SCOMB_HOME_URL) {
                        val cookies = CookieManager.getInstance().getCookie(url)
                        val sessionId = cookies?.split(";")?.find { it.trim().startsWith("SESSION=") }
                        if (sessionId != null) {
                            // SESSIONが見つかったらViewModelに通知
                            viewModel.onLoginSuccess(sessionId.trim())
                        }
                    }
                }
            }
            loadUrl(SCOMB_LOGIN_PAGE_URL)
        }
    })
}