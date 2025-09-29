package com.atuy.scomb.ui.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.ui.viewmodel.HomeData
import com.atuy.scomb.ui.viewmodel.HomeUiState
import com.atuy.scomb.ui.viewmodel.HomeViewModel
import com.atuy.scomb.util.DateUtils
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.atuy.scomb.ui.Screen
import androidx.navigation.NavGraph.Companion.findStartDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ホーム") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }) {                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "設定"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is HomeUiState.Success -> {
                    Dashboard(homeData = state.homeData)
                }
                is HomeUiState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { /* TODO: ViewModelに再取得メソッドを実装 */ }
                    )
                }
            }
        }
    }
}

@Composable
fun Dashboard(homeData: HomeData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            UpcomingTasksSection(tasks = homeData.upcomingTasks)
        }
        item {
            TodaysClassesSection(classes = homeData.todaysClasses)
        }
        item {
            RecentNewsSection(news = homeData.recentNews)
        }
    }
}

@Composable
fun UpcomingTasksSection(tasks: List<Task>) {
    DashboardSection(title = "直近の課題") {
        if (tasks.isEmpty()) {
            Text("直近の課題はありません。", modifier = Modifier.padding(16.dp))
        } else {
            tasks.forEach { task ->
                ListItem(
                    headlineContent = { Text(task.title, maxLines = 1) },
                    supportingContent = { Text(task.className) },
                    trailingContent = { Text(DateUtils.timeToString(task.deadline)) }
                )
                Divider()
            }
        }
    }
}

@Composable
fun TodaysClassesSection(classes: List<ClassCell>) {
    DashboardSection(title = "今日の授業") {
        if (classes.isEmpty()) {
            Text("今日の授業はありません。", modifier = Modifier.padding(16.dp))
        } else {
            classes.forEach { classCell ->
                ListItem(
                    headlineContent = { Text("${classCell.period + 1}限: ${classCell.name}") },
                    supportingContent = { Text(classCell.room ?: "未設定") }
                )
                Divider()
            }
        }
    }
}

@Composable
fun RecentNewsSection(news: List<NewsItem>) {
    DashboardSection(title = "最新のお知らせ") {
        if (news.isEmpty()) {
            Text("新しいお知らせはありません。", modifier = Modifier.padding(16.dp))
        } else {
            news.forEach { newsItem ->
                ListItem(
                    headlineContent = { Text(newsItem.title, maxLines = 2) },
                    supportingContent = { Text(newsItem.domain) },
                    trailingContent = { Text(newsItem.publishTime) }
                )
                Divider()
            }
        }
    }
}

@Composable
fun DashboardSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            content()
        }
    }
}