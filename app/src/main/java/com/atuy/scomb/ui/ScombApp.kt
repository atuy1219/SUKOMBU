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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomBarScreens = listOf(Screen.Home, Screen.Timetable, Screen.Tasks, Screen.News, Screen.Settings)
    val shouldShowBottomBar = currentDestination?.route in bottomBarScreens.map { it.route }
    val shouldShowTopBar = shouldShowBottomBar && currentDestination?.route != Screen.Timetable.route

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
                    if (shouldShowTopBar) {
                        AppTopBar(
                            currentRoute = currentDestination?.route,
                            navController = navController
                        )
                    }
                },
                bottomBar = {
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

                        if (initialIndex == -1 || targetIndex == -1) {
                            return@NavHost fadeIn(animationSpec = tween(300))
                        }

                        if (initialIndex < targetIndex) {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                        } else {
                            slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                        }
                    },
                    exitTransition = {
                        val initialIndex = bottomBarScreens.indexOfFirst { it.route == initialState.destination.route }
                        val targetIndex = bottomBarScreens.indexOfFirst { it.route == targetState.destination.route }

                        if (initialIndex == -1 || targetIndex == -1) {
                            return@NavHost fadeOut(animationSpec = tween(300))
                        }

                        if (initialIndex < targetIndex) {
                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                        } else {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                        }
                    }
                ) {
                    composable(Screen.Home.route) { HomeScreen(paddingValues = innerPadding) }
                    composable(Screen.Tasks.route) { TaskListScreen(paddingValues = innerPadding) }
                    composable(Screen.Timetable.route) { TimetableScreen() }
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
    }
}