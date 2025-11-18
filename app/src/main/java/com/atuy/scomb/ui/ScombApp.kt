package com.atuy.scomb.ui

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atuy.scomb.ui.features.ClassDetailScreen
import com.atuy.scomb.ui.features.HomeScreen
import com.atuy.scomb.ui.features.LoginScreen
import com.atuy.scomb.ui.features.NewsScreen
import com.atuy.scomb.ui.features.SettingsScreen
import com.atuy.scomb.ui.features.TaskListScreen
import com.atuy.scomb.ui.features.TimetableScreen
import com.atuy.scomb.ui.features.TimetableTerm
import com.atuy.scomb.ui.viewmodel.AuthState
import com.atuy.scomb.ui.viewmodel.MainViewModel
import com.atuy.scomb.ui.viewmodel.NewsViewModel
import com.atuy.scomb.ui.viewmodel.TaskListViewModel
import com.atuy.scomb.ui.viewmodel.TimetableViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScombApp(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val authState by mainViewModel.authState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val timetableViewModel: TimetableViewModel = hiltViewModel()
    val newsViewModel: NewsViewModel = hiltViewModel()
    val taskListViewModel: TaskListViewModel = hiltViewModel()

    val context = LocalContext.current

    LaunchedEffect(authState, navController) {
        Log.d(
            "ScombApp_Debug",
            "LaunchedEffect triggered. AuthState is: ${authState::class.java.simpleName}"
        )

        if (authState is AuthState.Authenticated) {
            val activity = context as? Activity
            val intent = activity?.intent
            val destination = intent?.getStringExtra("destination")

            if (destination == "tasks") {
                Log.d("ScombApp_Debug", "Navigating to Tasks from Widget")
                navController.navigate(Screen.Tasks.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                intent.removeExtra("destination")
            } else if (navController.currentDestination?.route == Screen.Login.route) {
                Log.d(
                    "ScombApp_Debug",
                    "Authenticated! Current route is Login. Navigating to Home."
                )
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            } else {
                Log.d(
                    "ScombApp_Debug",
                    "Authenticated! Current route is not Login (${navController.currentDestination?.route}), no navigation needed."
                )
            }
        } else if (authState is AuthState.Unauthenticated) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != Screen.Login.route) {
                Log.d(
                    "ScombApp_Debug",
                    "Unauthenticated! Current route is not Login ($currentRoute). Navigating to Login and clearing back stack."
                )
                navController.navigate(Screen.Login.route) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                }
            } else {
                Log.d(
                    "ScombApp_Debug",
                    "Unauthenticated! Already on Login screen, no navigation needed."
                )
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomBarScreens =
        listOf(Screen.Home, Screen.Timetable, Screen.Tasks, Screen.News, Screen.Settings)
    val shouldShowBottomBar = currentDestination?.route in bottomBarScreens.map { it.route }

    Scaffold(
        topBar = {
            if (shouldShowBottomBar) {
                AppTopBar(
                    currentRoute = currentDestination?.route,
                    timetableViewModel = timetableViewModel,
                    newsViewModel = newsViewModel,
                    taskListViewModel = taskListViewModel
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
        }
    ) { innerPadding ->
        if (authState is AuthState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val startDestination =
                if (authState is AuthState.Authenticated) Screen.Home.route else Screen.Login.route
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    val initialIndex =
                        bottomBarScreens.indexOfFirst { it.route == initialState.destination.route }
                    val targetIndex =
                        bottomBarScreens.indexOfFirst { it.route == targetState.destination.route }

                    if (initialIndex == -1 || targetIndex == -1) {
                        fadeIn(animationSpec = tween(300))
                    } else if (initialIndex < targetIndex) {
                        slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                    } else {
                        slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                    }
                },
                exitTransition = {
                    val initialIndex =
                        bottomBarScreens.indexOfFirst { it.route == initialState.destination.route }
                    val targetIndex =
                        bottomBarScreens.indexOfFirst { it.route == targetState.destination.route }

                    if (initialIndex == -1 || targetIndex == -1) {
                        fadeOut(animationSpec = tween(300))
                    } else if (initialIndex < targetIndex) {
                        slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                    } else {
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                    }
                }
            ) {
                composable(Screen.Login.route) {
                    LoginScreen()
                }
                composable(Screen.Home.route) { HomeScreen(paddingValues = innerPadding) }
                composable(Screen.Tasks.route) { TaskListScreen(viewModel = taskListViewModel) } // ViewModelを渡す
                composable(Screen.Timetable.route) { TimetableScreen(navController, timetableViewModel) }
                composable(Screen.News.route) { NewsScreen(newsViewModel) }
                composable(Screen.Settings.route) { SettingsScreen(navController = navController) }
                composable(
                    route = Screen.ClassDetail.route,
                    arguments = listOf(navArgument("classId") { type = NavType.StringType })
                ) {
                    ClassDetailScreen(navController = navController)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    currentRoute: String?,
    timetableViewModel: TimetableViewModel,
    newsViewModel: NewsViewModel,
    taskListViewModel: TaskListViewModel
) {
    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "TopBarAnimation"
    ) { targetRoute ->
        when (targetRoute) {
            Screen.Timetable.route -> {
                TimetableTopBar(viewModel = timetableViewModel)
            }
            Screen.News.route -> {
                NewsTopBar(viewModel = newsViewModel)
            }
            Screen.Tasks.route -> {
                TasksTopBar(viewModel = taskListViewModel)
            }
            else -> {
                TopAppBar(
                    title = {
                        Text(
                            when (targetRoute) {
                                Screen.Home.route -> "ホーム"
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsTopBar(viewModel: NewsViewModel) {
    TopAppBar(
        title = { Text("お知らせ") },
        actions = {
            IconButton(onClick = { viewModel.toggleSearchActive() }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "検索"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksTopBar(viewModel: TaskListViewModel) {
    TopAppBar(
        title = { Text("課題") },
        actions = {
            IconButton(onClick = { viewModel.toggleSearchActive() }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "検索"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableTopBar(
    viewModel: TimetableViewModel
) {
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val currentTerm by viewModel.currentTerm.collectAsStateWithLifecycle()
    val current = TimetableTerm(currentYear, currentTerm)

    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Box {
                Row(
                    modifier = Modifier.clickable { menuExpanded = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (current.year != 0) current.getDisplayName() else "読み込み中...")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "学期選択")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    val startYear = Calendar.getInstance().get(Calendar.YEAR)
                    for (i in 0..5) {
                        val displayYear = startYear - i
                        DropdownMenuItem(
                            text = { Text(TimetableTerm(displayYear, "2").getDisplayName()) },
                            onClick = {
                                viewModel.changeYearAndTerm(displayYear, "2")
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TimetableTerm(displayYear, "1").getDisplayName()) },
                            onClick = {
                                viewModel.changeYearAndTerm(displayYear, "1")
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "更新"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}