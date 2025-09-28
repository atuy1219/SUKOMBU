package com.atuy.scomb.ui.features

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
    // ViewModelのUI状態を監視し、変更があれば自動的に再描画される
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("課題・テスト一覧") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
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
                    // TODO: エラー表示用のUIをここに記述
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("エラーが発生しました: ${state.message}")
                    }
                }
            }
        }
    }
}

@Composable
fun TaskList(tasks: List<Task>) {
    // 大量
    LazyColumn {
        items(tasks) { task ->
            TaskListItem(task = task)
        }
    }
}

@Composable
fun TaskListItem(task: Task) {
    Column {
        ListItem(
            // 課題の種類に応じたアイコンを表示
            leadingContent = { TaskTypeIcon(task.taskType) },
            // メインのテキスト（課題名）
            headlineContent = {
                Text(
                    text = task.title,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) MaterialTheme.colorScheme.outline else LocalContentColor.current
                )
            },
            // サブテキスト（科目名と締め切り）
            supportingContent = {
                Column {
                    Text(task.className)
                    Text(
                        text = DateUtils.timeToString(task.deadline),
                        color = if (task.deadline < System.currentTimeMillis() + 24 * 60 * 60 * 1000)
                            MaterialTheme.colorScheme.error // 24時間以内なら赤字
                        else
                            LocalContentColor.current
                    )
                }
            }
        )
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun TaskTypeIcon(taskType: Int) {
    // 元アプリのアイコンとテキストの組み合わせを再現
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