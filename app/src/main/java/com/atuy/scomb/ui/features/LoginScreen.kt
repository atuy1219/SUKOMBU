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

                            // ホームページに到達したらCookieを取得
                            if (!url.isNullOrBlank() && url.startsWith(SCOMB_HOME_URL)) {
                                // 少し待機してからCookieを取得（非同期処理の完了を待つ）
                                view?.postDelayed({
                                    try {
                                        // CookieManager.flush()を呼んで確実に保存
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                            CookieManager.getInstance().flush()
                                        }

                                        val cookies = CookieManager.getInstance().getCookie(url)
                                        Log.d(TAG, "All cookies: $cookies")

                                        if (!cookies.isNullOrBlank()) {
                                            // SESSION cookieを探す
                                            val sessionCookie = cookies
                                                .split(";")
                                                .map { it.trim() }
                                                .firstOrNull { it.startsWith("SESSION=") }

                                            if (!sessionCookie.isNullOrBlank()) {
                                                val sessionId = sessionCookie.substringAfter("SESSION=").trim()
                                                Log.d(TAG, "Session ID found: $sessionId")
                                                viewModel.onLoginSuccess(sessionId)
                                            } else {
                                                // JavaScriptでdocument.cookieを取得（フォールバック）
                                                tryGetCookieViaJavaScript(view, url)
                                            }
                                        } else {
                                            Log.w(TAG, "No cookies found from CookieManager")
                                            tryGetCookieViaJavaScript(view, url)
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error retrieving cookies", e)
                                    }
                                }, 1000) // 1秒待機
                            }
                        }

                        private fun tryGetCookieViaJavaScript(view: WebView?, url: String) {
                            Log.d(TAG, "Trying to get cookie via JavaScript")
                            view?.evaluateJavascript(
                                """
            (function() {
                var cookies = document.cookie;
                console.log('Document cookies: ' + cookies);
                return cookies;
            })()
            """.trimIndent()
                            ) { result ->
                                Log.d(TAG, "JavaScript cookie result: $result")
                                val raw = result?.removeSurrounding("\"")
                                    ?.replace("\\u003D", "=")
                                    ?.replace("\\u0026", "&")

                                val session = raw
                                    ?.split(";")
                                    ?.map { it.trim() }
                                    ?.firstOrNull { it.startsWith("SESSION=") }

                                if (!session.isNullOrBlank()) {
                                    val sessionId = session.substringAfter("SESSION=").trim()
                                    Log.d(TAG, "Session ID from JS: $sessionId")
                                    viewModel.onLoginSuccess(sessionId)
                                } else {
                                    Log.e(TAG, "Failed to retrieve session cookie")
                                    // エラーをUIに表示する処理を追加
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
