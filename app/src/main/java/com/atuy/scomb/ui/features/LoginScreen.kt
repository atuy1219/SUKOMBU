package com.atuy.scomb.ui.features

import android.content.Intent
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.ui.viewmodel.LoginUiState
import com.atuy.scomb.ui.viewmodel.LoginViewModel

private const val TAG = "LoginScreen"

// Scombz のログイン・ホーム URL
const val SCOMB_LOGIN_PAGE_URL =
    "https://scombz.shibaura-it.ac.jp/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust"
const val SCOMB_HOME_URL = "https://scombz.shibaura-it.ac.jp/portal/home"
private const val SCOMBZ_DOMAIN = "scombz.shibaura-it.ac.jp"
private const val LOGIN_DOMAIN = "adfs.sic.shibaura-it.ac.jp"


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
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING

                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT

                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    // 必要なら実機の Chrome と同じ UA を設定する（コメント解除して試してください）
                    // userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.7339.155 Mobile Safari/537.36"
                }

                // Cookie を受け入れる（ログイン検出に必要）
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                // Console ログを Logcat に出す（デバッグ）
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d(
                            TAG,
                            "JS: ${consoleMessage?.message()} -- ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}"
                        )
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
                            try {
                                // CookieManager.flush()を呼んで確実に保存
                                CookieManager.getInstance().flush()

                                val cookies = CookieManager.getInstance().getCookie(SCOMB_HOME_URL)
                                Log.d(TAG, "Cookies for domain $SCOMB_HOME_URL: $cookies")

                                if (!cookies.isNullOrBlank()) {
                                    // SESSION cookieを探す
                                    val sessionCookie = cookies
                                        .split(";")
                                        .map { it.trim() }
                                        .firstOrNull { it.startsWith("SESSION=") }

                                    if (!sessionCookie.isNullOrBlank()) {
                                        val sessionId =
                                            sessionCookie.substringAfter("SESSION=").trim()
                                        Log.d(TAG, "Session ID found: $sessionId")
                                        viewModel.onLoginSuccess(sessionId)
                                    } else {
                                        Log.e(
                                            TAG,
                                            "SESSION cookie not found. HttpOnly cookie may be the cause."
                                        )
                                    }
                                } else {
                                    Log.w(
                                        TAG,
                                        "No cookies found from CookieManager for $SCOMB_HOME_URL"
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error retrieving cookies", e)
                            }
                        }
                    }

                    // 外部リンクは外部ブラウザで、ScombZ内はWebViewで開く
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val targetUri = request?.url ?: return true

                        return if (targetUri.host == SCOMBZ_DOMAIN||targetUri.host == LOGIN_DOMAIN) {
                            // ScombZドメイン内のリンクはWebViewで読み込む
                            false // falseを返すとWebViewがURLをロードする
                        } else {
                            // 外部リンクは外部ブラウザで開く
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, targetUri)
                                view?.context?.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to open external link: $targetUri", e)
                            }
                            true // trueを返すとWebViewはURLをロードしない
                        }
                    }
                }

                // 初期ロード（ログインページ）
                loadUrl(SCOMB_LOGIN_PAGE_URL)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
