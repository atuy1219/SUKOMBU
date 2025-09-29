package com.atuy.scomb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScombApp(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val authState by mainViewModel.authState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // ▼▼▼ 変更点：ナビゲーションの状態に応じて表示する項目を決定 ▼▼▼
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 表示する画面のリスト
    val bottomBarScreens = listOf(Screen.Home, Screen.Timetable, Screen.Tasks, Screen.News, Screen.Settings)
    // 現在のルートがボトムバーの画面リストに含まれるか
    val shouldShowBottomBar = currentDestination?.route in bottomBarScreens.map { it.route }

    // 現在のルートから画面のタイトルを取得
    val currentScreen = bottomBarScreens.find { it.route == currentDestination?.route }

    when (authState) {
        is AuthState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AuthState.Authenticated, is AuthState.Unauthenticated -> {
            val startDestination = if (authState is AuthState.Authenticated) Screen.Home.route else Screen.Login.route

            // ▼▼▼ 変更点：Scaffoldをアプリの唯一の親にする ▼▼▼
            Scaffold(
                topBar = {
                    // ログイン成功後、かつボトムバーが表示される画面でのみトップバーを表示
                    if (shouldShowBottomBar && authState is AuthState.Authenticated) {
                        TopAppBar(
                            title = { Text(currentScreen?.label ?: "") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.background
                            ),
                            actions = {
                                // ホーム画面でのみ設定ボタンを表示
                                if (currentScreen == Screen.Home) {
                                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = "設定"
                                        )
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    // ログイン成功後、かつボトムバーが表示される画面でのみボトムバーを表示
                    if (shouldShowBottomBar && authState is AuthState.Authenticated) {
                        NavigationBar {
                            bottomBarScreens.forEach { screen ->
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
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    // 各画面コンポーザブルには innerPadding を渡す
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