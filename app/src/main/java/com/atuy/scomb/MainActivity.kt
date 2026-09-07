package com.atuy.scomb

import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.atuy.scomb.data.manager.SettingsManager
import com.atuy.scomb.data.manager.AutoRefreshManager
import com.atuy.scomb.ui.ScombApp
import com.atuy.scomb.ui.theme.ScombTheme
import com.atuy.scomb.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    var notificationIntent by androidx.compose.runtime.mutableStateOf<Intent?>(null)
        private set

    @Inject
    lateinit var autoRefreshManager: AutoRefreshManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationIntent = intent
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()

            val darkTheme = when (themeMode) {
                SettingsManager.THEME_MODE_LIGHT -> false
                SettingsManager.THEME_MODE_DARK -> true
                else -> isSystemInDarkTheme()
            }

            ScombTheme(darkTheme = darkTheme) {
                ScombApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationIntent = intent
    }

    override fun onResume() {
        super.onResume()
        // フォアグラウンドに戻った時に更新チェック
        autoRefreshManager.checkAndTriggerRefresh()
    }

    override fun unregisterReceiver(receiver: BroadcastReceiver) {
        try {
            super.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}
