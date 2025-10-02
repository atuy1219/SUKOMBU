package com.atuy.scomb.util

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "WebViewLoginManager"

class WebViewLoginManager(private val context: Context) {

    private var webView: WebView? = null
    private var loginCallback: ((Result<String>) -> Unit)? = null

    sealed class LoginState {
        object Initial : LoginState()
        object WaitingForCredentials : LoginState()
        object WaitingForTwoFactor : LoginState()
        data class Success(val sessionId: String) : LoginState()
        data class Error(val message: String) : LoginState()
    }

    // JavaScript Interfaceクラス
    inner class LoginJsInterface {
        @JavascriptInterface
        fun onSessionDetected(sessionId: String) {
            Log.d(TAG, "Session detected: $sessionId")
            loginCallback?.invoke(Result.success(sessionId))
            cleanup()
        }

        @JavascriptInterface
        fun onTwoFactorRequired() {
            Log.d(TAG, "Two-factor authentication required")
            loginCallback?.invoke(Result.failure(TwoFactorRequiredException()))
        }

        @JavascriptInterface
        fun onLoginError(message: String) {
            Log.e(TAG, "Login error: $message")
            loginCallback?.invoke(Result.failure(Exception(message)))
            cleanup()
        }
    }

    class TwoFactorRequiredException : Exception("二段階認証が必要です")

    suspend fun login(username: String, password: String): Result<String> =
        suspendCancellableCoroutine { continuation ->
            loginCallback = { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            continuation.invokeOnCancellation {
                cleanup()
            }

            try {
                initializeWebView()
                performLogin(username, password)
            } catch (e: Exception) {
                Log.e(TAG, "Login initialization failed", e)
                continuation.resumeWithException(e)
            }
        }

    suspend fun submitTwoFactorCode(code: String): Result<String> =
        suspendCancellableCoroutine { continuation ->
            loginCallback = { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            continuation.invokeOnCancellation {
                cleanup()
            }

            try {
                webView?.evaluateJavascript(
                    """
                    (function() {
                        var codeInput = document.querySelector('input[name="otc"]') ||
                                       document.querySelector('input[type="text"][maxlength="6"]') ||
                                       document.querySelector('input[placeholder*="コード"]');
                        if (codeInput) {
                            codeInput.value = '$code';
                            var submitBtn = document.querySelector('input[type="submit"]') ||
                                           document.querySelector('button[type="submit"]') ||
                                           document.querySelector('button');
                            if (submitBtn) {
                                submitBtn.click();
                                return true;
                            }
                        }
                        return false;
                    })();
                """.trimIndent()
                ) { result ->
                    Log.d(TAG, "Two-factor code submission result: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Two-factor submission failed", e)
                continuation.resumeWithException(e)
            }
        }

    private fun initializeWebView() {
        if (webView != null) {
            cleanup()
        }

        webView = WebView(context).apply webViewApply@{
            // 画面には表示しない（バックグラウンド処理）
            layoutParams = android.view.ViewGroup.LayoutParams(1, 1)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
            }

            // JavaScript Interfaceを登録
            addJavascriptInterface(LoginJsInterface(), "AndroidLoginBridge")

            // Cookie設定
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAcceptThirdPartyCookies(this@webViewApply, true)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page finished: $url")

                    // ページ読み込み後、状態を確認
                    checkPageState()
                }
            }
        }
    }

    private fun performLogin(username: String, password: String) {
        val loginUrl =
            "https://scombz.shibaura-it.ac.jp/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust"

        webView?.loadUrl(loginUrl)

        // ページ読み込み後にログイン処理を実行
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")

                // ADFSログインページの場合
                if (url?.contains("adfs.sic.shibaura-it.ac.jp") == true) {
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var usernameInput = document.querySelector('input[name="UserName"]') ||
                                               document.querySelector('input[type="text"]');
                            var passwordInput = document.querySelector('input[name="Password"]') ||
                                               document.querySelector('input[type="password"]');
                            
                            if (usernameInput && passwordInput) {
                                usernameInput.value = '$username';
                                passwordInput.value = '$password';
                                
                                var submitBtn = document.querySelector('input[type="submit"]') ||
                                               document.querySelector('button[type="submit"]');
                                if (submitBtn) {
                                    submitBtn.click();
                                    return true;
                                }
                            }
                            return false;
                        })();
                    """.trimIndent()
                    ) { result ->
                        Log.d(TAG, "Login form submission result: $result")
                    }
                }
                // ScombZポータルページの場合（ログイン成功）
                else if (url?.contains("scombz.shibaura-it.ac.jp/portal") == true) {
                    checkForSession()
                }
                // 二段階認証ページの場合
                else if (url?.contains("adfs") == true) {
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var twoFactorInput = document.querySelector('input[name="otc"]') ||
                                                document.querySelector('input[type="text"][maxlength="6"]');
                            return twoFactorInput !== null;
                        })();
                    """.trimIndent()
                    ) { result ->
                        if (result == "true") {
                            Log.d(TAG, "Two-factor page detected")
                            loginCallback?.invoke(Result.failure(TwoFactorRequiredException()))
                        }
                    }
                }
            }
        }
    }

    private fun checkPageState() {
        webView?.evaluateJavascript(
            """
            (function() {
                // セッションCookieの確認
                var cookies = document.cookie.split(';');
                for (var i = 0; i < cookies.length; i++) {
                    var cookie = cookies[i].trim();
                    if (cookie.startsWith('SESSION=')) {
                        var sessionId = cookie.substring(8);
                        AndroidLoginBridge.onSessionDetected(sessionId);
                        return;
                    }
                }
                
                // 二段階認証ページの確認
                var twoFactorInput = document.querySelector('input[name="otc"]') ||
                                    document.querySelector('input[type="text"][maxlength="6"]');
                if (twoFactorInput) {
                    AndroidLoginBridge.onTwoFactorRequired();
                    return;
                }
                
                // エラーメッセージの確認
                var errorElement = document.querySelector('.error-message') ||
                                  document.querySelector('[class*="error"]');
                if (errorElement && errorElement.textContent.trim()) {
                    AndroidLoginBridge.onLoginError(errorElement.textContent.trim());
                }
            })();
        """.trimIndent(), null
        )
    }

    private fun checkForSession() {
        webView?.evaluateJavascript(
            """
            (function() {
                var cookies = document.cookie.split(';');
                for (var i = 0; i < cookies.length; i++) {
                    var cookie = cookies[i].trim();
                    if (cookie.startsWith('SESSION=')) {
                        return cookie.substring(8);
                    }
                }
                return null;
            })();
        """.trimIndent()
        ) { sessionId ->
            if (!sessionId.isNullOrBlank() && sessionId != "null") {
                val cleanSessionId = sessionId.trim('"')
                Log.d(TAG, "Session found: $cleanSessionId")
                loginCallback?.invoke(Result.success(cleanSessionId))
                cleanup()
            }
        }
    }

    fun cleanup() {
        webView?.destroy()
        webView = null
        loginCallback = null
    }
}
