package com.atuy.scomb.util

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private const val TAG = "WebViewLoginManager"

// ログイン処理の各イベントを通知するためのリスナーインターフェース
interface LoginListener {
    fun onSuccess(sessionId: String)
    fun onTwoFactorCodeExtracted(code: String)
    fun onError(message: String)
}

class WebViewLoginManager(private val context: Context) {

    private var webView: WebView? = null
    private var listener: LoginListener? = null
    private var is2faCodeExtracted = false
    private var isSessionDetected = false

    // JavaScriptからKotlinのメソッドを呼び出すためのブリッジクラス
    inner class LoginJsInterface {
        @JavascriptInterface
        fun onSessionDetected(sessionId: String) {
            if (isSessionDetected) return // 重複呼び出し防止
            isSessionDetected = true

            Log.d(TAG, "Session detected: $sessionId")
            GlobalScope.launch(Dispatchers.Main) {
                listener?.onSuccess(sessionId)
                cleanup()
            }
        }

        @JavascriptInterface
        fun onTwoFactorCodeExtracted(code: String) {
            if (is2faCodeExtracted) return // 重複呼び出し防止
            is2faCodeExtracted = true

            Log.d(TAG, "Two-factor code extracted: $code")
            GlobalScope.launch(Dispatchers.Main) {
                listener?.onTwoFactorCodeExtracted(code)
            }
        }

        @JavascriptInterface
        fun onLoginError(message: String) {
            Log.e(TAG, "Login error: $message")
            GlobalScope.launch(Dispatchers.Main) {
                listener?.onError(message)
                cleanup()
            }
        }
    }

    /**
     * セッションを定期的にチェックするスクリプト
     */
    private fun getSessionPollingScript(): String {
        return """
        (function() {
            if (window.sessionPollingStarted) {
                console.log('Session polling already started');
                return;
            }
            window.sessionPollingStarted = true;
            
            console.log('Starting session polling...');
            
            const checkSession = function() {
                const cookies = document.cookie;
                console.log('Checking cookies:', cookies);
                
                if (cookies.includes('SESSION=')) {
                    const sessionMatch = cookies.match(/SESSION=([^;]+)/);
                    if (sessionMatch && sessionMatch[1]) {
                        const sessionId = sessionMatch[1];
                        console.log('Session found:', sessionId);
                        
                        if (window.sessionPollingInterval) {
                            clearInterval(window.sessionPollingInterval);
                            delete window.sessionPollingInterval;
                        }
                        
                        AndroidLoginBridge.onSessionDetected(sessionId);
                        return true;
                    }
                }
                return false;
            };
            
            // 即座に一度チェック
            if (!checkSession()) {
                // 見つからなければポーリング開始
                window.sessionPollingInterval = setInterval(checkSession, 1000);
            }
        })();
        """.trimIndent()
    }

    fun startLogin(username: String, password: String, listener: LoginListener) {
        this.listener = listener
        this.is2faCodeExtracted = false
        this.isSessionDetected = false
        initializeWebViewAndLoad(username, password)
    }

    private fun initializeWebViewAndLoad(username: String, password: String) {
        if (webView != null) {
            cleanup()
        }

        webView = WebView(context).apply webViewApply@{
            layoutParams = android.view.ViewGroup.LayoutParams(1, 1)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
            }

            addJavascriptInterface(LoginJsInterface(), "AndroidLoginBridge")

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAcceptThirdPartyCookies(this@webViewApply, true)
                }
            }

            webViewClient = createWebViewClient(username, password)

            val loginUrl =
                "https://scombz.shibaura-it.ac.jp/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust"
            loadUrl(loginUrl)
        }
    }

    private fun createWebViewClient(username: String, password: String): WebViewClient {
        return object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")

                // ▼▼▼ 変更点①: ホームページURLへの到達をログイン成功とみなし、セッション取得処理を開始 ▼▼▼
                // 2段階認証を承認するとこのURLにリダイレクトされるため、この時点で成功と判断する。
                if (url?.startsWith("https://scombz.shibaura-it.ac.jp/portal/home") == true) {
                    if (!isSessionDetected) {
                        Log.d(TAG, "Homepage detected. Starting session polling.")
                        view?.evaluateJavascript(getSessionPollingScript(), null)
                    }
                    // ホームページに到達したら、以降のスクリプト評価は不要
                    return
                }
                // ▲▲▲ 変更点① ▲▲▲

                view?.evaluateJavascript(
                    """
                    (function() {
                        console.log('Page finished, checking state...');
                        
                        // 優先度1: ログイン成功（セッション確認）
                        if (document.cookie.includes('SESSION=')) {
                            const sessionMatch = document.cookie.match(/SESSION=([^;]+)/);
                            if (sessionMatch && sessionMatch[1]) {
                                console.log('Session detected on page load');
                                AndroidLoginBridge.onSessionDetected(sessionMatch[1]);
                                return 'SUCCESS';
                            }
                        }

                        // 優先度2: エラーメッセージ
                        const errorElement = document.getElementById('errorText') || document.querySelector('.error');
                        if (errorElement && errorElement.innerText.trim()) {
                            console.log('Error detected:', errorElement.innerText.trim());
                            AndroidLoginBridge.onLoginError(errorElement.innerText.trim());
                            return 'ERROR_DETECTED';
                        }

                        // 優先度3: 二段階認証コード表示ページ
                        const codeElement = document.getElementById('validEntropyNumber');
                        if (codeElement && codeElement.innerText) {
                            const code = codeElement.innerText.trim();
                            if (code) {
                                console.log('2FA code found:', code);
                                AndroidLoginBridge.onTwoFactorCodeExtracted(code);
                                return '2FA_CODE_EXTRACTED';
                            }
                        }

                        // 優先度4: 認証方法選択ページ
                        const mfaLink = document.getElementById('AzureMfaAuthentication');
                        if (mfaLink) {
                            console.log('MFA link found, clicking...');
                            mfaLink.click();
                            return 'MFA_LINK_CLICKED';
                        }

                        // 優先度5: ID/パスワード入力ページ
                        const usernameInput = document.getElementById('userNameInput') || document.querySelector('input[name="UserName"]');
                        const passwordInput = document.getElementById('passwordInput') || document.querySelector('input[name="Password"]');
                        if (usernameInput && passwordInput && !usernameInput.value) {
                            console.log('Login form found, filling credentials...');
                            usernameInput.value = '$username';
                            passwordInput.value = '$password';
                            const submitBtn = document.getElementById('submitButton') || document.getElementById('primaryButton');
                            if (submitBtn) {
                                submitBtn.click();
                                return 'CREDENTIALS_SUBMITTED';
                            }
                        }
                        
                        return 'UNKNOWN_STATE';
                    })();
                    """.trimIndent()
                ) { result ->
                    Log.d(TAG, "JS execution result: $result")

                    if (result?.contains("2FA_CODE_EXTRACTED") == true && !isSessionDetected) {
                        Log.d(TAG, "Starting session polling after 2FA code extraction")
                        view?.postDelayed({
                            view.evaluateJavascript(getSessionPollingScript()) { pollingResult ->
                                Log.d(TAG, "Polling script executed: $pollingResult")
                            }
                        }, 500) // 少し遅延を入れて確実に実行
                    }
                }
            }
        }
    }

    fun cleanup() {
        webView?.evaluateJavascript(
            """
            if (window.sessionPollingInterval) { 
                clearInterval(window.sessionPollingInterval); 
                delete window.sessionPollingInterval;
                delete window.sessionPollingStarted;
            }
            """.trimIndent(),
            null
        )
        webView?.destroy()
        webView = null
        listener = null
    }
}

