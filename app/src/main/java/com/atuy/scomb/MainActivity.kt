package com.atuy.scomb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.atuy.scomb.ui.ScombApp
import com.atuy.scomb.ui.theme.ScombTheme
import android.content.Intent

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ナビゲーションバー/ステータスバーをコンテンツの後ろにする（既存）
        enableEdgeToEdge()

        setContent {
            ScombTheme {
                // ここでアプリのルート Composable を呼び出す
                ScombApp()
            }
        }
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra("auth_result_saml")?.let { saml ->
            // ViewModel 等に投げる、UI を遷移させる、トークンを保存する等
        }
    }
}
