package com.atuy.scomb.ui.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.ui.viewmodel.TaskListUiState
import com.atuy.scomb.ui.viewmodel.TaskListViewModel
import com.atuy.scomb.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // paddingValuesは使わず、BoxでfillMaxSizeのみ
    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TaskListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TaskListUiState.Success -> {
                TaskList(tasks = state.tasks)
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

@Composable
fun TaskList(tasks: List<Task>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(tasks) { task ->
            TaskListItem(task = task)
        }
    }
}

@Composable
fun TaskListItem(task: Task) {
    Column {
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
                    Text(
                        text = DateUtils.timeToString(task.deadline),
                        color = if (task.deadline < System.currentTimeMillis() + 24 * 60 * 60 * 1000)
                            MaterialTheme.colorScheme.error
                        else
                            LocalContentColor.current
                    )
                }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun TaskTypeIcon(taskType: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val iconText = when (taskType) {
            0 -> "課題"
            1 -> "テスト"
            2 -> "アンケート"
            else -> "他"
        }
        Text(
            text = iconText,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "エラーが発生しました",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("再試行")
        }
    }
}