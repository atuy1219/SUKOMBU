package com.atuy.scomb.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.atuy.scomb.R

sealed class Screen(val route: String, @StringRes val resourceId: Int, val icon: ImageVector) {
    object Login : Screen("login", R.string.screen_login, Icons.AutoMirrored.Filled.List)
    object Home : Screen("home", R.string.screen_home, Icons.Filled.Home)
    object Timetable : Screen("timetable", R.string.screen_timetable, Icons.Filled.DateRange)
    object Tasks : Screen("tasks", R.string.screen_tasks, Icons.AutoMirrored.Filled.List)
    object News : Screen("news", R.string.screen_news, Icons.Filled.Notifications)
    object Settings : Screen("settings", R.string.screen_settings, Icons.Filled.Settings)
    object ClassDetail : Screen("classDetail/{classId}", R.string.screen_class_detail, Icons.Filled.DateRange)
}