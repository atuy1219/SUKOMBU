// FILE: app/src/main/java/com/atuy/scomb/ui/ScombApp.kt
package com.atuy.scomb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.atuy.scomb.ui.features.HomeScreen
import com.atuy.scomb.ui.features.LoginScreen
import com.atuy.scomb.ui.features.NewsScreen
import com.atuy.scomb.ui.features.SettingsScreen
import com.atuy.scomb.ui.features.TaskListScreen
import com.atuy.scomb.ui.features.TimetableScreen
import com.atuy.scomb.ui.viewmodel.AuthState
import com.atuy.scomb.ui.viewmodel.MainViewModel
import androidx.navigation.NavController

@Composable
fun ScombApp(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val authState by mainViewModel.authState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    when (authState) {
        is AuthState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AuthState.Authenticated, is AuthState.Unauthenticated -> {
            // ログイン後の開始画面をホームに変更
            val startDestination = if (authState is AuthState.Authenticated) Screen.Home.route else Screen.Login.route

            Scaffold(
                bottomBar = {
                    if (authState is AuthState.Authenticated) {
                        val items = listOf(Screen.Timetable, Screen.Tasks, Screen.Home, Screen.News, Screen.Settings)
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(screen.label) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Login.route) {
                        LoginScreen(
                            onLoginSuccess = {
                                // ログイン成功時はホーム画面に遷移
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Screen.Home.route) { HomeScreen(navController = navController) }
                    composable(Screen.Home.route) { HomeScreen() }
                    composable(Screen.Tasks.route) { TaskListScreen() }
                    composable(Screen.Timetable.route) { TimetableScreen() }
                    composable(Screen.News.route) { NewsScreen() }
                    composable(Screen.Settings.route) { SettingsScreen(navController = navController) }
                }
            }
        }
    }
}