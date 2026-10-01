@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.atuy.scomb.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import android.app.Activity
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atuy.scomb.R
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem

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
    val activity = context as? Activity
    val currentIntent by rememberUpdatedState((activity as? com.atuy.scomb.MainActivity)?.notificationIntent ?: activity?.intent)

    LaunchedEffect(authState, navController, currentIntent) {
        Log.d(
            "ScombApp_Debug",
            "LaunchedEffect triggered. AuthState is: ${authState::class.java.simpleName}"
        )

        if (authState is AuthState.Authenticated) {
            val notificationUrl = currentIntent?.getStringExtra("notification_url")
            if (!notificationUrl.isNullOrBlank()) {
                openUrlInCustomTab(context, notificationUrl)
                currentIntent?.removeExtra("notification_url")
                currentIntent?.removeExtra("notification_type")
            }

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
    val currentRoute = navBackStackEntry?.destination?.route
    val currentRouteState by rememberUpdatedState(currentRoute)
    val mainScreens = remember {
        listOf(Screen.Home, Screen.Timetable, Screen.Tasks, Screen.News, Screen.Settings)
    }
    val pagerState = rememberPagerState {
        mainScreens.size
    }
    val scope = rememberCoroutineScope()
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    var syncingRoute by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        val page = mainScreens.indexOfFirst { it.route == currentRoute }
        if (page >= 0 && page != pagerState.settledPage && !pagerState.isScrollInProgress) {
            syncingRoute = true
            try {
                pagerState.scrollToPage(page)
            } finally {
                syncingRoute = false
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .drop(1)
            .collect { (page, scrolling) ->
                val route = mainScreens[page].route
                if (!scrolling && !syncingRoute &&
                    mainScreens.any { it.route == currentRouteState } && route != currentRouteState
                ) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
    }
    val selectPage: (Int) -> Unit = { page ->
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    // Keep the actual page and its bars underneath details throughout the back gesture.
    Box(Modifier.fillMaxSize()) {
        if (authState is AuthState.Authenticated) {
            Scaffold(
                modifier = Modifier.semantics {
                    if (currentRoute == Screen.ClassDetail.route) hideFromAccessibility()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    AppTopBar(
                        currentRoute = mainScreens[pagerState.currentPage].route,
                        timetableViewModel = timetableViewModel,
                        newsViewModel = newsViewModel,
                        taskListViewModel = taskListViewModel
                    )
                },
                bottomBar = {
                    if (!isTablet) {
                        ShortNavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            arrangement = ShortNavigationBarArrangement.EqualWeight
                        ) {
                            mainScreens.forEachIndexed { index, screen ->
                                ShortNavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(stringResource(screen.resourceId)) },
                                    selected = pagerState.currentPage == index,
                                    onClick = { selectPage(index) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(Modifier.fillMaxSize().padding(innerPadding)) {
                    if (isTablet) {
                        WideNavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            arrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            mainScreens.forEachIndexed { index, screen ->
                                WideNavigationRailItem(
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(stringResource(screen.resourceId)) },
                                    railExpanded = false,
                                    selected = pagerState.currentPage == index,
                                    onClick = { selectPage(index) }
                                )
                            }
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        key = { mainScreens[it].route },
                        userScrollEnabled = currentRoute in mainScreens.map { it.route }
                    ) { page ->
                        when (mainScreens[page]) {
                            Screen.Home -> AdaptiveRouteContainer(isTablet, 1200.dp) {
                                HomeScreen(navController = navController, paddingValues = innerPadding)
                            }
                            Screen.Timetable -> AdaptiveRouteContainer(isTablet, 1400.dp) {
                                TimetableScreen(navController, timetableViewModel)
                            }
                            Screen.Tasks -> AdaptiveRouteContainer(isTablet, 900.dp) {
                                TaskListScreen(viewModel = taskListViewModel)
                            }
                            Screen.News -> AdaptiveRouteContainer(isTablet, 900.dp) {
                                NewsScreen(newsViewModel)
                            }
                            Screen.Settings -> AdaptiveRouteContainer(isTablet, 900.dp) {
                                SettingsScreen(navController = navController)
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }

        if (authState is AuthState.Loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        } else {
            val startDestination =
                if (authState is AuthState.Authenticated) Screen.Home.route else Screen.Login.route
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable(Screen.Login.route) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        AdaptiveRouteContainer(isTablet, 520.dp) { LoginScreen() }
                    }
                }
                mainScreens.forEach { screen -> composable(screen.route) {} }
                composable(
                    route = Screen.ClassDetail.route,
                    arguments = listOf(
                        navArgument("classId") { type = NavType.StringType },
                        navArgument("dayOfWeek") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("period") { type = NavType.IntType; defaultValue = -1 }
                    ),
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        )
                    },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) { entry ->
                    val lifecycleState by entry.lifecycle.currentStateAsState()
                    SwipeBackContainer(
                        enabled = currentRoute == Screen.ClassDetail.route &&
                            lifecycleState == Lifecycle.State.RESUMED,
                        onBack = { navController.popBackStack() }
                    ) { navigateBack ->
                        Box(
                            Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                        ) {
                            AdaptiveRouteContainer(isTablet, 1000.dp) {
                                ClassDetailScreen(onNavigateBack = navigateBack)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveRouteContainer(
    isTablet: Boolean,
    maxWidth: Dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(
                    if (isTablet) {
                        Modifier
                            .widthIn(max = maxWidth)
                            .fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
        ) {
            content()
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
    val topBarEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            fadeIn(animationSpec = topBarEffectsSpec) togetherWith fadeOut(animationSpec = topBarEffectsSpec)
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
                            stringResource(
                                when (targetRoute) {
                                    Screen.Home.route -> R.string.screen_home
                                    Screen.Settings.route -> R.string.screen_settings
                                    else -> R.string.app_name // デフォルト
                                }
                            )
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
        title = { Text(stringResource(R.string.screen_news)) },
        actions = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.toggleSearchActive() }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search)
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
        title = { Text(stringResource(R.string.screen_tasks)) },
        actions = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.toggleSearchActive() }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search)
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
                    // ここは動的な値なのでそのままですが、読み込み中などはリソース化可能
                    Text(if (current.year != 0) current.getDisplayName() else stringResource(R.string.loading))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "学期選択")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    val startYear = Calendar.getInstance().get(Calendar.YEAR)
                    val terms = remember(startYear) {
                        buildList {
                            for (i in 0..5) {
                                val displayYear = startYear - i
                                add(TimetableTerm(displayYear, "2"))
                                add(TimetableTerm(displayYear, "1"))
                            }
                        }
                    }
                    terms.forEachIndexed { index, term ->
                        SelectableDropdownMenuItem(
                            selected = term == current,
                            text = { Text(term.getDisplayName()) },
                            shapes = MenuDefaults.itemShape(index, terms.size),
                            selectedLeadingIcon = {
                                Icon(Icons.Default.Check, contentDescription = null)
                            },
                            onClick = {
                                viewModel.changeYearAndTerm(term.year, term.term)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.refresh() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.refresh)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

private fun openUrlInCustomTab(context: android.content.Context, url: String) {
    runCatching {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, url.toUri())
    }.onFailure {
        Log.e("ScombApp", "Failed to open Custom Tab", it)
    }
}
