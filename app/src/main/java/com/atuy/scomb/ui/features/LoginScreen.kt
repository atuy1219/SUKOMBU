package com.atuy.scomb.ui.features

import android.content.Intent
import android.os.Build
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

const val SCOMB_LOGIN_PAGE_URL =
    "https://scombz.shibaura-it.ac.jp/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust"
const val SCOMB_HOME_URL = "https://scombz.shibaura-it.ac.jp/portal/home"
private const val SCOMB_DOMAIN = "scombz.shibaura-it.ac.jp"

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                // ハードウェアアクセラレーションを無効化（シェーダーエラー対策）
                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

                // WebSettings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = true

                    // レイアウト／表示関連
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = false
                    displayZoomControls = false

                    // テキスト表示の改善
                    textZoom = 100
                    minimumFontSize = 8
                    minimumLogicalFontSize = 8
                    defaultFontSize = 16
                    defaultFixedFontSize = 13

                    // レンダリング設定
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    } else {
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                    }

                    // その他の設定
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    mediaPlaybackRequiresUserGesture = true

                    // フォントの設定
                    standardFontFamily = "sans-serif"
                    serifFontFamily = "serif"
                    sansSerifFontFamily = "sans-serif"
                    fixedFontFamily = "monospace"
                }

                // Cookie を受け入れる
                CookieManager.getInstance().setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }

                // Console ログを Logcat に出す
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

                        // ページ読み込み後、フォントと表示を強制的に調整するJavaScriptを実行
                        view?.evaluateJavascript("""
                            (function() {
                                document.body.style.fontFamily = 'sans-serif';
                                document.body.style.fontSize = '16px';
                                document.body.style.webkitTextSizeAdjust = '100%';
                                document.body.style.textSizeAdjust = '100%';
                                var style = document.createElement('style');
                                style.textContent = '* { -webkit-font-smoothing: antialiased; }';
                                document.head.appendChild(style);
                            })();
                        """.trimIndent(), null)

                        // ホームページまたはポータルページに到達したらCookieを取得
                        if (!url.isNullOrBlank() &&
                            (url.startsWith(SCOMB_HOME_URL) ||
                                    url.contains("scombz.shibaura-it.ac.jp/portal"))) {

                            Log.d(TAG, "Reached ScombZ portal, attempting to get session cookie...")

                            try {
                                // Cookieをフラッシュして確実に保存
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    CookieManager.getInstance().flush()
                                }

                                // 少し待ってからCookieを取得
                                view?.postDelayed({
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    Log.d(TAG, "All cookies for $url: $cookies")

                                    if (!cookies.isNullOrBlank()) {
                                        val sessionCookie = cookies
                                            .split(";")
                                            .map { it.trim() }
                                            .firstOrNull { it.startsWith("SESSION=") }

                                        if (!sessionCookie.isNullOrBlank()) {
                                            val sessionId = sessionCookie.substringAfter("SESSION=").trim()
                                            Log.d(TAG, "Session ID found: $sessionId")
                                            viewModel.onLoginSuccess(sessionId)
                                        } else {
                                            Log.w(TAG, "SESSION cookie not found in: $cookies")
                                        }
                                    } else {
                                        Log.w(TAG, "No cookies found")
                                    }
                                }, 500) // 500ms待機
                            } catch (e: Exception) {
                                Log.e(TAG, "Error retrieving cookies", e)
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val targetUri = request?.url ?: return true

                        return if (targetUri.host == SCOMB_DOMAIN ||
                            targetUri.host?.endsWith("sic.shibaura-it.ac.jp") == true ||
                            targetUri.host?.contains("shibaura-it.ac.jp") == true) {
                            // Shibaura関連のドメインはWebViewで読み込む
                            false
                        } else {
                            // 外部リンクは外部ブラウザで開く
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, targetUri)
                                view?.context?.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to open external link: $targetUri", e)
                            }
                            true
                        }
                    }
                }

                // 初期ロード
                loadUrl(SCOMB_LOGIN_PAGE_URL)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}