package com.atuy.scomb.ui.features

import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.ui.viewmodel.LoginUiState
import com.atuy.scomb.ui.viewmodel.LoginViewModel

private const val TAG = "LoginScreen"

// Scomb のログイン・ホーム URL（必要に応じて正確な URL に合わせてください）
const val SCOMB_LOGIN_PAGE_URL =
    "https://scombz.shibaura-it.ac.jp/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust"
const val SCOMB_HOME_URL = "https://scombz.shibaura-it.ac.jp/portal/home"

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ViewModel 側で Success を検出したら画面遷移等を行う
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                // WebSettings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = true

                    // レイアウト／表示関連
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    } else {
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                    }

                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    // 必要なら実機の Chrome と同じ UA を設定する（コメント解除して試してください）
                    // userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.7339.155 Mobile Safari/537.36"
                }

                // Cookie を受け入れる（ログイン検出に必要）
                CookieManager.getInstance().setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }

                // Console ログを Logcat に出す（デバッグ）
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d(TAG, "JS: ${consoleMessage?.message()} -- ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}")
                        return super.onConsoleMessage(consoleMessage)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        Log.e(TAG, "WebView error: ${error?.description ?: "unknown"}")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d(TAG, "Finished loading: $url")

                        // 目的のホーム URL に到達したら Cookie から SESSION を取る
                        if (!url.isNullOrBlank() && url.startsWith(SCOMB_HOME_URL)) {
                            try {
                                val cookies = CookieManager.getInstance().getCookie(url)
                                Log.d(TAG, "CookieManager cookies: $cookies")

                                // SESSION=... を探す
                                val sessionCookie = cookies
                                    ?.split(";")
                                    ?.map { it.trim() }
                                    ?.firstOrNull { it.startsWith("SESSION=") }

                                if (!sessionCookie.isNullOrBlank()) {
                                    val sessionId = sessionCookie.substringAfter("SESSION=").trim()
                                    Log.d(TAG, "sessionId found: $sessionId")
                                    viewModel.onLoginSuccess(sessionId)
                                } else {
                                    // HttpOnly / SameSite 等で取れない可能性あり → document.cookie を試す（HttpOnly は無理）
                                    view?.evaluateJavascript("document.cookie") { result ->
                                        Log.d(TAG, "document.cookie: $result")
                                        // result は文字列リテラルなので整形
                                        val raw = result?.removeSurrounding("\"")?.replace("\\u003D", "=")?.replace("\\u0026", "&")
                                        val session = raw
                                            ?.split(";")
                                            ?.map { it.trim() }
                                            ?.firstOrNull { it.startsWith("SESSION=") }
                                        if (!session.isNullOrBlank()) {
                                            val sessionId = session.substringAfter("SESSION=").trim()
                                            Log.d(TAG, "sessionId from document.cookie: $sessionId")
                                            viewModel.onLoginSuccess(sessionId)
                                        } else {
                                            Log.w(TAG, "No session cookie found after JS check")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Cookie read failed", e)
                            }
                        }
                    }

                    // 別タブや外部のリンクをアプリ内で開きたい場合に制御
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val target = request?.url?.toString() ?: return false
                        // 必要に応じて外部リンク判定を追加する
                        view?.loadUrl(target)
                        return true
                    }
                }

                // 初期ロード（ログインページ）
                loadUrl(SCOMB_LOGIN_PAGE_URL)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
