package com.atuy.scomb.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource // 仮のアイコン用
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.atuy.scomb.ui.features.TaskListScreen

@Composable
fun ScombApp() {
    // ナビゲーションの状態を管理する NavController を作成
    val navController = rememberNavController()

    // アプリの基本的な骨格
    Scaffold(
        bottomBar = {
            // ボトムナビゲーションバー
            NavigationBar {
                val items = listOf(
                    Screen.Timetable,
                    Screen.Tasks,
                    Screen.News,
                    Screen.Settings
                )
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(painterResource(id = android.R.drawable.ic_menu_help), contentDescription = null) }, // TODO: アイコンを設定
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // スタート画面まで戻るように設定（戻るボタンでアプリが終了する挙動）
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // 画面遷移の定義
        NavHost(
            navController = navController,
            startDestination = Screen.Tasks.route, // 最初に表示する画面
            modifier = Modifier.padding(innerPadding)
        ) {
            // "tasks"というルートが指定されたら、TaskListScreenを表示する
            composable(Screen.Tasks.route) {
                TaskListScreen()
            }
            // 他の画面も同様に定義
            composable(Screen.Timetable.route) {
                // TODO: 時間割画面のComposableをここに配置
                Text("時間割画面")
            }
            composable(Screen.News.route) {
                // TODO: お知らせ画面のComposableをここに配置
                Text("お知らせ画面")
            }
            composable(Screen.Settings.route) {
                // TODO: 設定画面のComposableをここに配置
                Text("設定画面")
            }
        }
    }
}