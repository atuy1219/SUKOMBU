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
    }

    private var webView: WebView? = null
    private var listener: LoginListener? = null
    private var is2faCodeExtracted = false
    private var isSessionDetected = false

    private var cookiePollHandler: Handler? = null
    private var cookiePollRunnable: Runnable? = null

    fun startLogin(username: String, password: String, listener: LoginListener) {
        this.listener = listener
        this.is2faCodeExtracted = false
        this.isSessionDetected = false
        initializeWebViewAndLoad(username, password)
    }

    private fun initializeWebViewAndLoad(username: String, password: String) {
        cleanup()

        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            addJavascriptInterface(AndroidLoginBridge(), "AndroidLoginBridge")
            webViewClient = createWebViewClient(username, password)
        }

        try {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Log.w(TAG, "CookieManager setup failed", e)
        }

        val loginUrl =
            "https://scombz.shibaura-it.ac.jp/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust"
        webView?.loadUrl(loginUrl)
    }

    private inner class AndroidLoginBridge {
        @JavascriptInterface
        fun onSessionDetected(session: String) {
            Log.d(TAG, "onSessionDetected JS -> $session")
            GlobalScope.launch(Dispatchers.Main) {
                if (!isSessionDetected) {
                    isSessionDetected = true
                    listener?.onSuccess(session)
                    cleanup()
                }
            }
        }

        @JavascriptInterface
        fun onLoginError(message: String) {
            Log.d(TAG, "onLoginError JS -> $message")
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    listener?.onLoginError(message)
                } catch (_: Throwable) {
                }
                try {
                    listener?.onError(message)
                } catch (_: Throwable) {
                }
            }
        }

        @JavascriptInterface
        fun onTwoFactorCodeExtracted(code: String) {
            Log.d(TAG, "onTwoFactorCodeExtracted JS -> $code")
            GlobalScope.launch(Dispatchers.Main) {
                if (!is2faCodeExtracted) {
                    is2faCodeExtracted = true
                    listener?.onTwoFactorCodeExtracted(code)
                }
            }
        }
    }

    private fun createWebViewClient(username: String, password: String): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")

                try {
                    CookieManager.getInstance().flush()
                    val cookies = CookieManager.getInstance().getCookie(DOMAIN)
                    Log.d(TAG, "Current cookies: $cookies")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting cookies", e)
                }

                if (url?.startsWith("$DOMAIN/portal/home") == true) {
                    Log.d(TAG, "Homepage detected!")
                    if (!isSessionDetected) {
                        view?.evaluateJavascript(
                            """
                            (function() {
                                const m = document.cookie.match(/SESSION=([^;]+)/);
                                if (m && m[1]) {
                                    AndroidLoginBridge.onSessionDetected(m[1]);
                                    return 'SESSION_FOUND';
                                }
                                return 'SESSION_NOT_FOUND';
                            })();
                            """.trimIndent()
                        ) { result ->
                            Log.d(TAG, "Homepage session check: $result")
                            if (result?.contains("SESSION_NOT_FOUND") == true) {
                                checkSessionViaCookieManager()
                            }
                        }
                    }
                    return
                }

                // 認証フロー処理
                view?.evaluateJavascript(
                    """
                    (function() {
                        console.log('Page finished, checking state.');
                        
                        const usernameInput = document.getElementById('userNameInput') || document.querySelector('input[name="UserName"]');
                        const passwordInput = document.getElementById('passwordInput') || document.querySelector('input[name="Password"]');
                        const sessionMatch = document.cookie.match(/SESSION=([^;]+)/);
                        
                        if (sessionMatch && sessionMatch[1]) {
                            AndroidLoginBridge.onSessionDetected(sessionMatch[1]);
                            return 'SUCCESS';
                        }
                        
                        const errorElement = document.getElementById('errorText') || document.querySelector('.error');
                        if (errorElement && errorElement.innerText.trim()) {
                            AndroidLoginBridge.onLoginError(errorElement.innerText.trim());
                            return 'ERROR_DETECTED';
                        }
                        
                        const codeElement = document.getElementById('validEntropyNumber');
                        if (codeElement && codeElement.innerText) {
                            const code = codeElement.innerText.trim();
                            if (code) {
                                AndroidLoginBridge.onTwoFactorCodeExtracted(code);
                                return '2FA_CODE_EXTRACTED';
                            }
                        }
                        
                        const mfaLink = document.getElementById('AzureMfaAuthentication');
                        if (mfaLink) {
                            mfaLink.click();
                            return 'MFA_LINK_CLICKED';
                        }
                        
                        if (usernameInput && passwordInput) {
                            usernameInput.focus();
                            usernameInput.value = '${escapeForJs(username)}';
                            usernameInput.dispatchEvent(new Event('input', {bubbles: true}));
                            passwordInput.focus();
                            passwordInput.value = '${escapeForJs(password)}';
                            passwordInput.dispatchEvent(new Event('input', {bubbles: true}));
                            const submitBtn = document.getElementById('submitButton') || document.getElementById('primaryButton');
                            if (submitBtn) {
                                submitBtn.click();
                                return 'CREDENTIALS_SUBMITTED';
                            } else {
                                const form = usernameInput.form || passwordInput.form;
                                if (form) { form.submit(); return 'FORM_SUBMITTED'; }
                            }
                        }
                        return 'UNKNOWN_STATE';
                    })();
                    """.trimIndent()
                ) { result ->
                    Log.d(TAG, "JS execution result: $result")

                    if (result?.contains("2FA_CODE_EXTRACTED") == true && !isSessionDetected) {
                        Log.d(TAG, "Starting aggressive session polling after 2FA")
                        // より積極的なポーリングを開始
                        startAggressiveSessionPolling()
                    }
                }
            }
        }
    }

    private fun startAggressiveSessionPolling() {
        // 既存のポーリングを停止
        stopPolling()

        // JSポーリングを開始
        webView?.evaluateJavascript(getSessionPollingScript()) { result ->
            Log.d(TAG, "JS polling started: $result")
        }

        // Androidポーリングを開始（より頻繁に）
        startAndroidCookiePolling()

        // 定期的にページをリロードしてセッションを確認（バックグラウンド対策）
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isSessionDetected) {
                Log.d(TAG, "Checking if redirected to home...")
                webView?.url?.let { currentUrl ->
                    if (currentUrl.startsWith("$DOMAIN/portal/home")) {
                        Log.d(TAG, "Detected home page during polling, force checking session")
                        checkSessionViaCookieManager()
                    }
                }
            }
        }, 3000)
    }

    private fun checkSessionViaCookieManager() {
        try {
            CookieManager.getInstance().flush()
            val cookies = CookieManager.getInstance().getCookie(DOMAIN) ?: ""
            Log.d(TAG, "Force checking cookies: $cookies")
            val match = Regex("SESSION=([^;]+)").find(cookies)
            if (match != null && !isSessionDetected) {
                val sessionId = match.groupValues[1]
                Log.d(TAG, "Session found via force check: $sessionId")
                GlobalScope.launch(Dispatchers.Main) {
                    isSessionDetected = true
                    listener?.onSuccess(sessionId)
                    cleanup()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in force check", e)
        }
    }

    private fun getSessionPollingScript(): String {
        return """
            (function() {
                if (window.sessionPollingStarted) { return 'POLLING_ALREADY_STARTED'; }
                window.sessionPollingStarted = true;
                
                let pollCount = 0;
                const maxPolls = 60;
                
                window.sessionPollingInterval = setInterval(function() {
                    pollCount++;
                    try {
                        // 現在のURLをチェック
                        if (window.location.href.indexOf('/portal/home') !== -1) {
                            console.log('Detected home page in JS polling');
                        }
                        
                        const m = document.cookie.match(/SESSION=([^;]+)/);
                        if (m && m[1]) {
                            console.log('Session found in JS polling: ' + m[1]);
                            AndroidLoginBridge.onSessionDetected(m[1]);
                            clearInterval(window.sessionPollingInterval);
                            delete window.sessionPollingInterval;
                            delete window.sessionPollingStarted;
                        } else if (pollCount >= maxPolls) {
                            console.log('JS polling timeout');
                            clearInterval(window.sessionPollingInterval);
                            delete window.sessionPollingInterval;
                            delete window.sessionPollingStarted;
                        }
                    } catch(e) {
                        console.log('session polling error', e);
                    }
                }, 500);
                
                return 'POLLING_STARTED';
            })();
        """.trimIndent()
    }

    private fun startAndroidCookiePolling() {
        if (cookiePollHandler != null) return

        cookiePollHandler = Handler(Looper.getMainLooper())
        var pollCount = 0
        val maxPollCount = 120 // 60秒間（500ms間隔）

        cookiePollRunnable = object : Runnable {
            override fun run() {
                pollCount++
                try {
                    CookieManager.getInstance().flush()
                    val cookies = CookieManager.getInstance().getCookie(DOMAIN) ?: ""

                    if (pollCount % 4 == 0) { // 2秒ごとにログ出力
                        Log.d(TAG, "Polling cookies ($pollCount/$maxPollCount)")
                    }

                    val match = Regex("SESSION=([^;]+)").find(cookies)
                    if (match != null) {
                        val sessionId = match.groupValues[1]
                        Log.d(TAG, "Session found via CookieManager: $sessionId")
                        GlobalScope.launch(Dispatchers.Main) {
                            if (!isSessionDetected) {
                                isSessionDetected = true
                                listener?.onSuccess(sessionId)
                                cleanup()
                            }
                        }
                        return
                    }

                    // 現在のURLもチェック
                    webView?.url?.let { currentUrl ->
                        if (currentUrl.startsWith("$DOMAIN/portal/home") && !isSessionDetected) {
                            Log.d(
                                TAG,
                                "Home page detected during polling, attempting session extraction"
                            )
                            webView?.evaluateJavascript(
                                """
                                (function() {
                                    const m = document.cookie.match(/SESSION=([^;]+)/);
                                    if (m && m[1]) {
                                        AndroidLoginBridge.onSessionDetected(m[1]);
                                        return m[1];
                                    }
                                    return 'NOT_FOUND';
                                })();
                                """.trimIndent()
                            ) { result ->
                                Log.d(TAG, "URL-triggered session check: $result")
                            }
                        }
                    }

                    if (pollCount >= maxPollCount) {
                        Log.e(TAG, "Session polling timeout after $pollCount attempts")
                        GlobalScope.launch(Dispatchers.Main) {
                            listener?.onError("認証タイムアウト: セッションが取得できませんでした")
                            cleanup()
                        }
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Cookie polling error", e)
                }

                cookiePollHandler?.postDelayed(this, 500) // 500ms間隔
            }
        }
        cookiePollHandler?.post(cookiePollRunnable!!)
    }

    private fun stopPolling() {
        try {
            webView?.evaluateJavascript(
                """
                (function() {
                    if (window.sessionPollingInterval) { 
                        clearInterval(window.sessionPollingInterval); 
                        delete window.sessionPollingInterval;
                        delete window.sessionPollingStarted;
                    }
                })();
                """.trimIndent(), null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error while clearing JS polling", e)
        }

        try {
            cookiePollHandler?.removeCallbacks(cookiePollRunnable ?: Runnable { })
            cookiePollHandler = null
            cookiePollRunnable = null
        } catch (e: Exception) {
            Log.w(TAG, "Error while stopping cookie polling", e)
        }
    }

    fun cleanup() {
        stopPolling()

        try {
            webView?.stopLoading()
            webView?.removeAllViews()
            webView?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error while destroying webview", e)
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