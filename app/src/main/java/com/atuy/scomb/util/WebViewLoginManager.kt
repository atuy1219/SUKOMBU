package com.atuy.scomb.util

import android.content.Context
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

    // JavaScriptからKotlinのメソッドを呼び出すためのブリッジクラス
    inner class LoginJsInterface {
        @JavascriptInterface
        fun onSessionDetected(sessionId: String) {
            Log.d(TAG, "Session detected: $sessionId")
            // UIスレッドでリスナーを呼び出す
            GlobalScope.launch(Dispatchers.Main) {
                listener?.onSuccess(sessionId)
            }
            cleanup()
        }

        @JavascriptInterface
        fun onTwoFactorCodeExtracted(code: String) {
            Log.d(TAG, "Two-factor code extracted: $code")
            GlobalScope.launch(Dispatchers.Main) {
                listener?.onTwoFactorCodeExtracted(code)
                // 認証コード抽出後、セッションが確立されるまでポーリングを開始する
                webView?.evaluateJavascript(startSessionPollingScript(), null)
            }
        }

        @JavascriptInterface
        fun onLoginError(message: String) {
            Log.e(TAG, "Login error: $message")
            GlobalScope.launch(Dispatchers.Main) {
                listener?.onError(message)
            }
            cleanup()
        }
    }

    /**
     * ログイン成功（SESSIONクッキーの存在）を定期的にチェックするJavaScriptを生成します。
     */
    private fun startSessionPollingScript(): String {
        return """
        (function() {
            // 既にポーリング処理が実行中の場合は何もしない
            if (window.sessionPollingInterval) return;

            console.log('Starting session polling...');
            window.sessionPollingInterval = setInterval(function() {
                // SESSIONクッキーが見つかったか確認
                if (document.cookie.includes('SESSION=')) {
                    const sessionId = document.cookie.split(';').find(c => c.trim().startsWith('SESSION=')).split('=')[1];
                    if (sessionId) {
                        console.log('Session found by polling, stopping poll.');
                        // ポーリングを停止
                        clearInterval(window.sessionPollingInterval);
                        delete window.sessionPollingInterval;
                        // 成功を通知
                        AndroidLoginBridge.onSessionDetected(sessionId);
                    }
                }
            }, 2000); // 2秒ごとにチェック
        })();
        """.trimIndent()
    }

    /**
     * ログイン処理を開始します。
     */
    fun startLogin(username: String, password: String, listener: LoginListener) {
        this.listener = listener
        initializeWebViewAndLoad(username, password)
    }

    private fun initializeWebViewAndLoad(username: String, password: String) {
        if (webView != null) {
            cleanup()
        }

        webView = WebView(context).apply webViewApply@{
            // このWebViewはバックグラウンド処理用で画面には表示されない
            layoutParams = android.view.ViewGroup.LayoutParams(1, 1)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE

                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            addJavascriptInterface(LoginJsInterface(), "AndroidLoginBridge")

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@webViewApply, true)
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

                // ▼▼▼ 変更点: ホーム画面のURLを検知して成功とみなす ▼▼▼
                if (url?.startsWith("https://scombz.shibaura-it.ac.jp/portal/home") == true) {
                    Log.d(TAG, "Home page reached. Starting session polling to get cookie.")
                    // ホーム画面に到達したらログイン成功とみなし、SESSIONクッキーがセットされるまで待機する
                    view?.evaluateJavascript(startSessionPollingScript(), null)
                    return // これ以降のJavaScript評価は不要
                }
                // ▲▲▲ 変更点 ▲▲▲

                // JavaScriptを注入し、ページの状態を判別・操作する
                view?.evaluateJavascript(
                    """
                    (function() {
                        // 優先度1: ログイン成功（ScombZポータルページ）-> URLベースの判定に移行したため、Cookieのみのチェックは予備として残す
                        if (document.cookie.includes('SESSION=')) {
                            const sessionId = document.cookie.split(';').find(c => c.trim().startsWith('SESSION=')).split('=')[1];
                            if (sessionId) {
                                AndroidLoginBridge.onSessionDetected(sessionId);
                                return 'SUCCESS_BY_COOKIE';
                            }
                        }

                        // 優先度2: エラーメッセージ
                        const errorElement = document.getElementById('errorText') || document.querySelector('.error');
                        if (errorElement && errorElement.innerText.trim()) {
                             AndroidLoginBridge.onLoginError(errorElement.innerText.trim());
                             return 'ERROR_DETECTED';
                        }

                        // 優先度3: 二段階認証コード表示ページ
                        const codeElement = document.getElementById('validEntropyNumber');
                        if (codeElement && codeElement.innerText && !window.is2faCodeExtracted) {
                            window.is2faCodeExtracted = true; // 抽出済みフラグを立てる
                            AndroidLoginBridge.onTwoFactorCodeExtracted(codeElement.innerText.trim());
                            return '2FA_CODE_EXTRACTED';
                        }

                        // 優先度4: 認証方法選択ページ
                        const mfaLink = document.getElementById('AzureMfaAuthentication');
                        if (mfaLink) {
                            mfaLink.click();
                            return 'MFA_LINK_CLICKED';
                        }

                        // 優先度5: ID/パスワード入力ページ
                        const usernameInput = document.getElementById('userNameInput') || document.querySelector('input[name="UserName"]');
                        const passwordInput = document.getElementById('passwordInput') || document.querySelector('input[name="Password"]');
                        // usernameInput.value が空の場合のみ入力（再入力防止）
                        if (usernameInput && passwordInput && !usernameInput.value) {
                             usernameInput.value = '$username';
                             passwordInput.value = '$password';
                             const submitBtn = document.getElementById('submitButton') || document.getElementById('primaryButton');
                             if(submitBtn) {
                                 submitBtn.click();
                                 return 'CREDENTIALS_SUBMITTED';
                             }
                        }
                        
                        return 'UNKNOWN_STATE';
                    })();
                """.trimIndent()
                ) { result ->
                    Log.d(TAG, "JS execution result: $result")
                }
            }
        }
    }

    /**
     * WebViewのリソースを解放します。
     */
    fun cleanup() {
        // ポーリング処理を停止する
        webView?.evaluateJavascript(
            "if(window.sessionPollingInterval) { clearInterval(window.sessionPollingInterval); delete window.sessionPollingInterval; }",
            null
        )
        webView?.destroy()
        webView = null
        listener = null
    }
}
