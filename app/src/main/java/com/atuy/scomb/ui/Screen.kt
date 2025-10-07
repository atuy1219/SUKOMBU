package com.atuy.scomb.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Login : Screen("login", "ログイン", Icons.AutoMirrored.Filled.List)
    object Home : Screen("home", "ホーム", Icons.Filled.Home)
    object Timetable : Screen("timetable", "時間割", Icons.Filled.DateRange)
    object Tasks : Screen("tasks", "課題", Icons.AutoMirrored.Filled.List)
    object News : Screen("news", "お知らせ", Icons.Filled.Notifications)
    object Settings : Screen("settings", "設定", Icons.Filled.Settings)
}