package com.atuy.scomb

import android.content.BroadcastReceiver
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atuy.scomb.ui.ScombApp
import com.atuy.scomb.ui.theme.ScombTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScombTheme {
                ScombApp()
            }
        }
    }

    override fun unregisterReceiver(receiver: BroadcastReceiver) {
        try {
            super.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}