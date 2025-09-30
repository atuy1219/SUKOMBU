package com.atuy.scomb.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.atuy.scomb.ui.features.HomeScreen
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
    val shouldShowTopBar = shouldShowBottomBar && currentDestination?.route != Screen.Timetable.route

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

            Scaffold(
                topBar = {
                    // bottomBarが表示される画面でのみTopBarを表示
                    if (shouldShowBottomBar) {
                        // TimetableScreenは独自のTopBarを持つので、ここでは表示しない
                        if (currentDestination?.route != Screen.Timetable.route) {
                            AppTopBar(
                                currentRoute = currentDestination?.route,
                                navController = navController
                            )
                        }
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
                    modifier = Modifier.padding(innerPadding),
                    enterTransition = {
                        val initialIndex = bottomBarScreens.indexOfFirst { it.route == initialState.destination.route }
                        val targetIndex = bottomBarScreens.indexOfFirst { it.route == targetState.destination.route }

                        // ログイン画面からの遷移など、bottomBarScreensに含まれない画面からの遷移はフェード
                        if (initialIndex == -1 || targetIndex == -1) {
                            return@NavHost fadeIn(animationSpec = tween(300))
                        }

                        // 右のタブに移動する場合は右からスライドイン
                        if (initialIndex < targetIndex) {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                        } else { // 左のタブに移動する場合は左からスライドイン
                            slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                        }
                    },
                    exitTransition = {
                        val initialIndex = bottomBarScreens.indexOfFirst { it.route == initialState.destination.route }
                        val targetIndex = bottomBarScreens.indexOfFirst { it.route == targetState.destination.route }

                        if (initialIndex == -1 || targetIndex == -1) {
                            return@NavHost fadeOut(animationSpec = tween(300))
                        }

                        // 右のタブに移動する場合は左へスライドアウト
                        if (initialIndex < targetIndex) {
                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                        } else { // 左のタブに移動する場合は右へスライドアウト
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                        }
                    }
                ) {
                    // 各画面コンポーザブルには innerPadding を渡す
                    composable(Screen.Home.route) { HomeScreen(paddingValues = innerPadding) }
                    composable(Screen.Tasks.route) { TaskListScreen(paddingValues = innerPadding) }
                    composable(Screen.Timetable.route) { TimetableScreen() } // TimetableScreenはpaddingを内部で処理
                    composable(Screen.News.route) { NewsScreen(paddingValues = innerPadding) }
                    composable(Screen.Settings.route) { SettingsScreen(paddingValues = innerPadding, navController = navController) }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(currentRoute: String?, navController: NavController) {
    // AnimatedContentでトップバー全体をラップし、
    // currentRoute の変更を検知してアニメーションさせる
    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "TopBarAnimation"
    ) { targetRoute ->
        TopAppBar(
            title = {
                Text(
                    when (targetRoute) {
                        Screen.Home.route -> "ホーム"
                        Screen.Tasks.route -> "課題"
                        Screen.News.route -> "お知らせ"
                        Screen.Settings.route -> "設定"
                        else -> ""
                    }
                )
            },
            /*actions = {
            // ホーム画面の時だけ設定アイコンを表示
            if (targetRoute == Screen.Home.route) {
                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "設定"
                    )
                }
            }
        },*/
            /*navigationIcon = {
            // ホーム以外の画面で戻るボタンを表示
            if (currentRoute != Screen.Home.route) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る"
                    )
                }
            }
        },*/

            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
    }
}