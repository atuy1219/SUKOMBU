package com.atuy.scomb

import android.content.BroadcastReceiver
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atuy.scomb.data.SettingsManager
import com.atuy.scomb.data.manager.AutoRefreshManager
import com.atuy.scomb.ui.ScombApp
import com.atuy.scomb.ui.theme.ScombTheme
import com.atuy.scomb.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var autoRefreshManager: AutoRefreshManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val themeMode by mainViewModel.themeMode.collectAsState()

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