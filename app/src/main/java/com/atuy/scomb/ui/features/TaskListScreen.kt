package com.atuy.scomb.ui.features

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.ui.viewmodel.TaskFilter
import com.atuy.scomb.ui.viewmodel.TaskListUiState
import com.atuy.scomb.ui.viewmodel.TaskListViewModel
import com.atuy.scomb.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val customTabsIntent = remember { CustomTabsIntent.Builder().build() }

    Column(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TaskListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is TaskListUiState.Success -> {
                FilterBar(
                    filter = state.filter,
                    onFilterChanged = { newFilter -> viewModel.updateFilter(newFilter) }
                )
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = {
                        viewModel.fetchTasks(forceRefresh = true)
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    TaskList(
                        tasks = state.tasks,
                        onTaskClick = { task ->
                            customTabsIntent.launchUrl(context, Uri.parse(task.url))
                        }
                    )
                }
            }

            is TaskListUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.fetchTasks(forceRefresh = true) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    filter: TaskFilter,
    onFilterChanged: (TaskFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filter.showAssignments,
            onClick = { onFilterChanged(filter.copy(showAssignments = !filter.showAssignments)) },
            label = { Text("課題") }
        )
        FilterChip(
            selected = filter.showTests,
            onClick = { onFilterChanged(filter.copy(showTests = !filter.showTests)) },
            label = { Text("テスト") }
        )
        FilterChip(
            selected = filter.showSurveys,
            onClick = { onFilterChanged(filter.copy(showSurveys = !filter.showSurveys)) },
            label = { Text("アンケート") }
        )
        Spacer(modifier = Modifier.weight(1f))
        FilterChip(
            selected = filter.showCompleted,
            onClick = { onFilterChanged(filter.copy(showCompleted = !filter.showCompleted)) },
            label = { Text("完了済み") },
            leadingIcon = {
                if (filter.showCompleted) {
                    Icon(Icons.Default.Check, contentDescription = "完了済み")
                }
            }
        )
    }
}

@Composable
fun TaskList(tasks: List<Task>, onTaskClick: (Task) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(tasks) { task ->
            TaskListItem(task = task, onTaskClick = onTaskClick)
        }
    }
}

@Composable
fun TaskListItem(task: Task, onTaskClick: (Task) -> Unit) {
    Column(modifier = Modifier.clickable { onTaskClick(task) }) {
        ListItem(
            leadingContent = { TaskTypeIcon(task.taskType) },
            headlineContent = {
                Text(
                    text = task.title,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) MaterialTheme.colorScheme.outline else LocalContentColor.current
                )
            },
            supportingContent = {
                Column {
                    Text(task.className)
                    val remainingTime = DateUtils.formatRemainingTime(task.deadline)
                    Text(
                        text = "${DateUtils.timeToString(task.deadline)} ($remainingTime)",
                        color = if (task.deadline < System.currentTimeMillis() && !task.done) {
                            MaterialTheme.colorScheme.error
                        } else if (task.deadline < System.currentTimeMillis() + 24 * 60 * 60 * 1000 && !task.done) {
                            MaterialTheme.colorScheme.error // 24時間未満もエラーカラー
                        } else {
                            LocalContentColor.current
                        },
                        fontSize = 12.sp
                    )
                }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun TaskTypeIcon(taskType: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
        val (iconText, color) = when (taskType) {
            0 -> "課題" to MaterialTheme.colorScheme.primary
            1 -> "テスト" to MaterialTheme.colorScheme.error
            2 -> "アンケート" to MaterialTheme.colorScheme.secondary
            else -> "他" to MaterialTheme.colorScheme.onSurface
        }
        Text(
            text = iconText,
            fontSize = 12.sp,
            color = color
        )
    }
}
