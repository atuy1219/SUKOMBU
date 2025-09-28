package com.atuy.scomb.ui.features

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TaskListScreen(viewModel: TaskListViewModel = viewModel()) {
    // ViewModelのUI状態を監視
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("課題・テスト一覧") }) }
    ) { paddingValues ->
        when (val state = uiState) {
            is TaskListUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is TaskListUiState.Success -> {
                TaskList(tasks = state.tasks)
            }
            is TaskListUiState.Error -> {
                // エラー表示
            }
        }
    }
}

@Composable
fun TaskList(tasks: List<Task>) {
    LazyColumn {
        items(tasks) { task ->
            TaskListItem(task)
        }
    }
}

@Composable
fun TaskListItem(task: Task) {
    // ListItemやCardを使ってタスク情報を表示する
    ListItem(
        headlineContent = { Text(task.title) },
        supportingContent = { Text("${task.className}\n${task.deadline}") } // 日時変換処理が必要
    )
}