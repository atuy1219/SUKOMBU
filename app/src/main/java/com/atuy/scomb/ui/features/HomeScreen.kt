package com.atuy.scomb.ui.features

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.R
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.ui.viewmodel.HomeData
import com.atuy.scomb.ui.viewmodel.HomeUiState
import com.atuy.scomb.ui.viewmodel.HomeViewModel
import com.atuy.scomb.util.DateUtils
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    paddingValues: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionDeniedMessage = stringResource(R.string.permission_notification_denied)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = permissionDeniedMessage,
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is HomeUiState.Success -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = {
                        viewModel.loadHomeData(forceRefresh = true)
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Dashboard(homeData = state.homeData)
                }
            }

            is HomeUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.loadHomeData(forceRefresh = true) }
                )
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
            TodaysClassesSection(classes = homeData.todaysClasses)
        }
        item {
            UpcomingTasksSection(tasks = homeData.upcomingTasks)
        }

        item {
            RecentNewsSection(news = homeData.recentNews)
        }
    }
}

@Composable
fun TodaysClassesSection(classes: List<ClassCell>) {
    DashboardSection(title = stringResource(R.string.home_todays_classes)) {
        if (classes.isEmpty()) {
            Text(stringResource(R.string.home_no_classes_today), modifier = Modifier.padding(16.dp))
        } else {
            classes.forEach { classCell ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_period_format, classCell.period + 1, classCell.name ?: "")) },
                    supportingContent = { Text(classCell.room ?: stringResource(R.string.home_room_unset)) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun UpcomingTasksSection(tasks: List<Task>) {
    DashboardSection(title = stringResource(R.string.home_upcoming_tasks)) {
        if (tasks.isEmpty()) {
            Text(stringResource(R.string.home_no_upcoming_tasks), modifier = Modifier.padding(16.dp))
        } else {
            tasks.forEach { task ->
                ListItem(
                    headlineContent = { Text(task.title, maxLines = 1) },
                    supportingContent = { Text(task.className) },
                    trailingContent = { Text(DateUtils.timeToString(task.deadline)) }
                )
                HorizontalDivider()
            }
        }
    }
}


@Composable
fun RecentNewsSection(news: List<NewsItem>) {
    DashboardSection(title = stringResource(R.string.home_recent_news)) {
        if (news.isEmpty()) {
            Text(stringResource(R.string.home_no_new_news), modifier = Modifier.padding(16.dp))
        } else {
            news.forEach { newsItem ->
                ListItem(
                    headlineContent = { Text(newsItem.title, maxLines = 2) },
                    supportingContent = { Text(newsItem.domain) },
                    trailingContent = { Text(newsItem.publishTime) }
                )
                HorizontalDivider()
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