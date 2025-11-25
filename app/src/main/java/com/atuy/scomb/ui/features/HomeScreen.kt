package com.atuy.scomb.ui.features

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atuy.scomb.R
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.ui.viewmodel.HomeData
import com.atuy.scomb.ui.viewmodel.HomeUiState
import com.atuy.scomb.ui.viewmodel.HomeViewModel
import com.atuy.scomb.ui.viewmodel.LinkItem
import com.atuy.scomb.util.DateUtils
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
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

    fun openUrl(url: String) {
        try {
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(context, url.toUri())
        } catch (e: Exception) {
            // Handle error
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ViewModelからのURLオープンイベントを監視
    LaunchedEffect(viewModel) {
        viewModel.openUrlEvent.collect { url ->
            openUrl(url)
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
                    Dashboard(
                        homeData = state.homeData,
                        showNews = state.showNews,
                        onClassClick = { classId -> navController.navigate("classDetail/$classId") },
                        onTaskClick = { task -> viewModel.onTaskClick(task) }, // タスククリック時にViewModelへ通知
                        onNewsClick = { url -> openUrl(url) },
                        onLinkClick = { url -> openUrl(url) }
                    )
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
fun Dashboard(
    homeData: HomeData,
    showNews: Boolean,
    onClassClick: (String) -> Unit,
    onTaskClick: (Task) -> Unit, // 引数をURLからTaskオブジェクトに変更
    onNewsClick: (String) -> Unit,
    onLinkClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TodaysClassesSection(
                classes = homeData.todaysClasses,
                onClassClick = onClassClick
            )
        }
        item {
            UpcomingTasksSection(
                tasks = homeData.upcomingTasks,
                onTaskClick = onTaskClick
            )
        }

        if (showNews) {
            item {
                RecentNewsSection(
                    news = homeData.recentNews,
                    onNewsClick = onNewsClick
                )
            }
        }

        item {
            QuickLinksSection(
                links = homeData.quickLinks,
                onLinkClick = onLinkClick
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DashboardSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun TodaysClassesSection(
    classes: List<ClassCell>,
    onClassClick: (String) -> Unit
) {
    val currentPeriod = DateUtils.getCurrentPeriod()

    DashboardSection(
        title = stringResource(R.string.home_todays_classes),
        icon = Icons.Default.Class
    ) {
        if (classes.isEmpty()) {
            EmptyStateItem(text = stringResource(R.string.home_no_classes_today))
        } else {
            classes.forEachIndexed { index, classCell ->
                val isCurrent = classCell.period == currentPeriod

                // 現在の授業の場合は背景色を変えて強調する
                val backgroundColor = if (isCurrent)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    Color.Transparent

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = backgroundColor),
                    headlineContent = {
                        Text(
                            text = classCell.name ?: "",
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = {
                        Text(
                            text = classCell.room ?: stringResource(R.string.home_room_unset),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${classCell.period + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        if (classCell.classId.isNotEmpty()) {
                            onClassClick(classCell.classId)
                        }
                    }
                )
                if (index < classes.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun UpcomingTasksSection(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit // URL文字列ではなくTaskオブジェクトを受け取るように変更
) {
    DashboardSection(
        title = stringResource(R.string.home_upcoming_tasks),
        icon = Icons.Default.AccessTime
    ) {
        if (tasks.isEmpty()) {
            EmptyStateItem(text = stringResource(R.string.home_no_upcoming_tasks))
        } else {
            tasks.forEachIndexed { index, task ->
                val isOverdue = task.deadline < System.currentTimeMillis()
                val timeColor =
                    if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            text = task.title,
                            maxLines = 1,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = task.className,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = DateUtils.timeToString(task.deadline),
                                style = MaterialTheme.typography.labelMedium,
                                color = timeColor
                            )
                            Text(
                                text = DateUtils.formatRemainingTime(task.deadline),
                                style = MaterialTheme.typography.labelSmall,
                                color = timeColor
                            )
                        }
                    },
                    modifier = Modifier.clickable { onTaskClick(task) } // Taskオブジェクトを渡す
                )
                if (index < tasks.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}


@Composable
fun RecentNewsSection(
    news: List<NewsItem>,
    onNewsClick: (String) -> Unit
) {
    DashboardSection(
        title = stringResource(R.string.home_recent_news),
        icon = Icons.Default.Notifications
    ) {
        if (news.isEmpty()) {
            EmptyStateItem(text = stringResource(R.string.home_no_new_news))
        } else {
            news.forEachIndexed { index, newsItem ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            text = newsItem.title,
                            maxLines = 2,
                            fontWeight = if (newsItem.unread) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    supportingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            if (newsItem.unread) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                            }
                            Text(
                                text = newsItem.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = newsItem.publishTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.clickable { onNewsClick(newsItem.url) }
                )
                if (index < news.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickLinksSection(
    links: List<LinkItem>,
    onLinkClick: (String) -> Unit
) {
    DashboardSection(title = stringResource(R.string.home_quick_links), icon = Icons.Default.Link) {
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            links.forEach { link ->
                AssistChip(
                    onClick = { onLinkClick(link.url) },
                    label = { Text(link.title) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (link.title.contains("シラバス")) Icons.Default.Class else Icons.Default.CalendarToday, // 簡易的なアイコン切り替え
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
fun EmptyStateItem(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}