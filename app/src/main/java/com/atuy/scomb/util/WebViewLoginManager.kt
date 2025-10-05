package com.atuy.scomb.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class WebViewLoginManager(private val context: Context) {

    companion object {
        private const val TAG = "WebViewLoginManager"
        private const val DOMAIN = "https://scombz.shibaura-it.ac.jp"
        private const val LOGIN_TIMEOUT_MS = 120000L
        private const val SESSION_POLL_INTERVAL_MS = 500L
        private const val MAX_POLL_COUNT = 120
    }

    private var webView: WebView? = null
    private var listener: LoginListener? = null
    private var isSessionDetected = false
    private var is2faCodeExtracted = false

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var pollCount = 0

    fun startLogin(username: String, password: String, listener: LoginListener) {
        this.listener = listener
        this.isSessionDetected = false
        this.is2faCodeExtracted = false
        this.pollCount = 0

        initializeWebView(username, password)
        startTimeout()
    }

    private fun initializeWebView(username: String, password: String) {
        cleanup()

        webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSavePassword(true)
                saveFormData = true
            }
            addJavascriptInterface(LoginBridge(), "AndroidLoginBridge")
            webViewClient = createWebViewClient(username, password)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            removeAllCookies(null)
            flush()
        }

        val loginUrl = "$DOMAIN/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust"
        webView?.loadUrl(loginUrl)
    }

    private inner class LoginBridge {
        @JavascriptInterface
        fun onSessionDetected(session: String) {
            GlobalScope.launch(Dispatchers.Main) {
                handleSessionDetected(session)
            }
        }

        @JavascriptInterface
        fun onLoginError(message: String) {
            GlobalScope.launch(Dispatchers.Main) {
                Log.e(TAG, "✗ Login error: $message")
                listener?.onLoginError(message)
                listener?.onError(message)
            }
        }

        @JavascriptInterface
        fun onTwoFactorCodeExtracted(code: String) {
            GlobalScope.launch(Dispatchers.Main) {
                if (!is2faCodeExtracted) {
                    is2faCodeExtracted = true
                    Log.d(TAG, "✓ 2FA code extracted: $code")
                    listener?.onTwoFactorCodeExtracted(code)
                    startSessionPolling()
                }
            }
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, "[JS] $message")
        }
    }

    private fun handleSessionDetected(session: String) {
        if (isSessionDetected || session.isBlank()) return

        isSessionDetected = true
        Log.d(TAG, "✓ Session detected successfully")

        // CRITICAL: WebViewを即座に停止・破棄してレンダラークラッシュを防ぐ
        try {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                webViewClient = object : WebViewClient() {}
                removeJavascriptInterface("AndroidLoginBridge")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping webview", e)
        }

        // リスナーに通知（これが画面遷移をトリガーする）
        val savedListener = listener

        // クリーンアップを即座に実行
        cleanup()

        // 通知は最後に行う（cleanupでlistenerがnullになるため）
        savedListener?.onSuccess(session)
    }

    private fun createWebViewClient(username: String, password: String): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (isSessionDetected) return true
                Log.d(TAG, "→ Navigation to: ${request?.url}")
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (isSessionDetected) {
                    Log.d(TAG, "⊙ Session already detected, ignoring page load")
                    return
                }

                Log.d(TAG, "⊙ Page loaded: $url")

                // ホームページ検出
                if (url?.contains("/portal/home") == true) {
                    Log.d(TAG, "⊙ Home page detected - checking session")
                    checkSessionInPage(view)
                    return
                }

                // 認証フロー実行
                view?.evaluateJavascript(getAuthFlowScript(username, password)) { result ->
                    if (isSessionDetected) return@evaluateJavascript
                    val cleanResult = result?.trim('"') ?: "null"
                    Log.d(TAG, "⊙ Auth flow result: $cleanResult")
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (isSessionDetected) return

                // ホームページへの遷移を検出
                if (url?.contains("/portal/home") == true) {
                    Log.d(TAG, "⊙ Home page navigation started - checking session early")
                    checkSessionViaCookieManager()
                }
            }
        }
    }

    private fun getAuthFlowScript(username: String, password: String): String {
        val escapedUsername = escapeForJs(username)
        val escapedPassword = escapeForJs(password)

        return """
            (function() {
                AndroidLoginBridge.log('Starting auth flow check');
                
                const sessionMatch = document.cookie.match(/SESSION=([^;]+)/);
                if (sessionMatch && sessionMatch[1]) {
                    AndroidLoginBridge.log('Session found in cookie');
                    AndroidLoginBridge.onSessionDetected(sessionMatch[1]);
                    return 'SESSION_FOUND';
                }
                
                const errorElement = document.getElementById('errorText') || document.querySelector('.error');
                if (errorElement && errorElement.innerText.trim()) {
                    AndroidLoginBridge.log('Error detected: ' + errorElement.innerText.trim());
                    AndroidLoginBridge.onLoginError(errorElement.innerText.trim());
                    return 'ERROR_DETECTED';
                }
                
                const codeElement = document.getElementById('validEntropyNumber');
                if (codeElement) {
                    const code = codeElement.innerText.trim();
                    if (code) {
                        AndroidLoginBridge.log('2FA code found: ' + code);
                        AndroidLoginBridge.onTwoFactorCodeExtracted(code);
                        return '2FA_CODE_EXTRACTED';
                    }
                }
                
                const mfaLink = document.getElementById('AzureMfaAuthentication');
                if (mfaLink && mfaLink.href) {
                    AndroidLoginBridge.log('MFA link found, clicking...');
                    setTimeout(function() {
                        mfaLink.click();
                        AndroidLoginBridge.log('MFA link clicked');
                    }, 100);
                    return 'MFA_LINK_CLICKED';
                }
                
                const usernameInput = document.getElementById('userNameInput') || 
                                     document.querySelector('input[name="UserName"]');
                const passwordInput = document.getElementById('passwordInput') || 
                                     document.querySelector('input[name="Password"]');
                
                if (usernameInput && passwordInput) {
                    AndroidLoginBridge.log('Login form found');
                    usernameInput.value = '$escapedUsername';
                    usernameInput.dispatchEvent(new Event('input', {bubbles: true}));
                    passwordInput.value = '$escapedPassword';
                    passwordInput.dispatchEvent(new Event('input', {bubbles: true}));
                    
                    const submitBtn = document.getElementById('submitButton') || 
                                     document.getElementById('primaryButton');
                    if (submitBtn) {
                        AndroidLoginBridge.log('Submitting credentials...');
                        setTimeout(function() { submitBtn.click(); }, 100);
                        return 'CREDENTIALS_SUBMITTED';
                    }
                    
                    const form = usernameInput.form || passwordInput.form;
                    if (form) {
                        AndroidLoginBridge.log('Submitting form...');
                        setTimeout(function() { form.submit(); }, 100);
                        return 'FORM_SUBMITTED';
                    }
                }
                
                return 'WAITING';
            })();
        """.trimIndent()
    }

    private fun checkSessionInPage(view: WebView?) {
        if (isSessionDetected) return

        view?.evaluateJavascript(
            """
            (function() {
                AndroidLoginBridge.log('Checking session in home page');
                const m = document.cookie.match(/SESSION=([^;]+)/);
                if (m && m[1]) {
                    AndroidLoginBridge.log('Session found: ' + m[1].substring(0, 10) + '...');
                    AndroidLoginBridge.onSessionDetected(m[1]);
                    return m[1];
                }
                AndroidLoginBridge.log('No session in cookie');
                return null;
            })();
            """.trimIndent()
        ) { result ->
            if (isSessionDetected) return@evaluateJavascript
            if (result == "null" || result.isNullOrBlank()) {
                checkSessionViaCookieManager()
            }
        }
    }

    private fun startSessionPolling() {
        Log.d(TAG, "⊙ Starting session polling")
        stopPolling()
        pollCount = 0

        pollRunnable = object : Runnable {
            override fun run() {
                if (isSessionDetected) return

                pollCount++
                if (pollCount % 10 == 0) {
                    Log.d(TAG, "⊙ Polling... ($pollCount/$MAX_POLL_COUNT)")
                }

                // CookieManager確認
                checkSessionViaCookieManager()

                // URL確認
                webView?.url?.let { url ->
                    if (url.contains("/portal/home") && !isSessionDetected) {
                        Log.d(TAG, "⊙ Home page detected in polling")
                        checkSessionViaCookieManager()
                    }
                }

                if (pollCount >= MAX_POLL_COUNT) {
                    Log.e(TAG, "✗ Session polling timeout")
                    GlobalScope.launch(Dispatchers.Main) {
                        listener?.onError("認証タイムアウト: セッションが取得できませんでした")
                        cleanup()
                    }
                    return
                }

                pollHandler.postDelayed(this, SESSION_POLL_INTERVAL_MS)
            }
        }
        pollHandler.post(pollRunnable!!)
    }

    private fun checkSessionViaCookieManager() {
        if (isSessionDetected) return

        try {
            CookieManager.getInstance().flush()
            val cookies = CookieManager.getInstance().getCookie(DOMAIN) ?: return

            val match = Regex("SESSION=([^;]+)").find(cookies)
            if (match != null) {
                val sessionId = match.groupValues[1]
                Log.d(TAG, "✓ Session found via CookieManager")
                GlobalScope.launch(Dispatchers.Main) {
                    handleSessionDetected(sessionId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking cookies", e)
        }
    }

    private fun startTimeout() {
        timeoutHandler.postDelayed({
            if (!isSessionDetected) {
                Log.e(TAG, "✗ Login timeout (2 minutes)")
                listener?.onError("ログインがタイムアウトしました。再試行してください。")
                cleanup()
            }
        }, LOGIN_TIMEOUT_MS)
    }

    private fun stopPolling() {
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        pollRunnable = null
    }

    fun cancelLogin() {
        if (!isSessionDetected) {
            listener?.onError("ログインがキャンセルされました")
        }
        cleanup()
    }

    fun cleanup() {
        stopPolling()
        timeoutHandler.removeCallbacksAndMessages(null)

        try {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                webViewClient = object : WebViewClient() {}
                removeJavascriptInterface("AndroidLoginBridge")
                removeAllViews()
                destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        } finally {
            webView = null
            listener = null
        }
    }

    private fun escapeForJs(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}