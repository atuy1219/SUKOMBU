// FILE: app/src/main/java/com/atuy/scomb/ui/Screen.kt
package com.atuy.scomb.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home // 追加
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Login : Screen("login", "ログイン", Icons.Filled.List)
    object Home : Screen("home", "ホーム", Icons.Filled.Home) // 追加
    object Timetable : Screen("timetable", "時間割", Icons.Filled.DateRange)
    object Tasks : Screen("tasks", "課題", Icons.Filled.List)
    object News : Screen("news", "お知らせ", Icons.Filled.Notifications)
    object Settings : Screen("settings", "設定", Icons.Filled.Settings)
}