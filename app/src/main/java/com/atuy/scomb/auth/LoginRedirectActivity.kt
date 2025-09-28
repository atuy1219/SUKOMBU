package com.atuy.scomb.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.atuy.scomb.MainActivity // 起点アクティビティ名に合わせる
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = intent?.data
        if (data != null) {
            // 例: myapp://auth/callback?SAMLResponse=xxxx
            val samlResponse = data.getQueryParameter("SAMLResponse")
            val code = data.getQueryParameter("code")

            // ここで SAMLResponse/code を安全にバックエンドに渡すなどの処理を行う
            // （同期的処理が必要なら coroutine や repository に渡して行う）
            lifecycleScope.launch(Dispatchers.IO) {
                samlResponse?.let {
                    // TODO: 送信 / 保存など。簡易例は SharedPreferences や Hilt の repository に入れる
                }
                // 処理後に MainActivity に戻す（必要なら flag を使って既存スタックを再利用）
                val intent = Intent(this@LoginRedirectActivity, MainActivity::class.java).apply {
                    putExtra("auth_result_saml", samlResponse)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            }
        } else {
            finish()
        }
    }
}
