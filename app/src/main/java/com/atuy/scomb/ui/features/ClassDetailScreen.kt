package com.atuy.scomb.ui.features

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.ui.viewmodel.ClassDetailUiState
import com.atuy.scomb.ui.viewmodel.ClassDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    navController: NavController,
    viewModel: ClassDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.openUrlEvent.collect { url: String ->
            try {
                val builder = CustomTabsIntent.Builder()
                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(context, url.toUri())
            } catch (e: Exception) {
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("授業詳細") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is ClassDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is ClassDetailUiState.Success -> {
                    ClassDetailContent(
                        classCell = state.classCell,
                        tasks = state.tasks,
                        contentPadding = paddingValues,
                        onClassPageClick = { viewModel.onClassPageClick() }
                    )
                }

                is ClassDetailUiState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadClassDetails() })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassDetailContent(
    classCell: ClassCell,
    tasks: List<Task>,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClassPageClick: () -> Unit = {}
) {
    val context = LocalContext.current
    fun openUrl(url: String?) {
        if (url.isNullOrEmpty()) return
        try {
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(context, url.toUri())
        } catch (e: Exception) {
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = contentPadding
    ) {
        item(key = "header") {
            Text(
                text = classCell.name ?: "科目名なし",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .animateItem(fadeInSpec = tween(durationMillis = 300))
            )
        }

        item(key = "basic_info") {
            DetailSectionTitle(title = "基本情報")
            DetailInfoRow(label = "曜日", value = (classCell.dayOfWeek).toDayOfWeekString())
            DetailInfoRow(label = "時限", value = "${classCell.period + 1} 限")
            DetailInfoRow(label = "教室", value = classCell.room ?: "未設定")
            DetailInfoRow(label = "教員", value = classCell.teachers ?: "未設定")
            DetailInfoRow(label = "単位数", value = classCell.numberOfCredit?.toString() ?: "未設定")
        }

        item(key = "links") {
            Spacer(modifier = Modifier.height(16.dp))
            DetailSectionTitle(title = "リンク")
            ListItem(
                headlineContent = { Text("授業ページ") },
                leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                modifier = Modifier
                    .clickable { onClassPageClick() }
                    .animateItem(fadeInSpec = tween(durationMillis = 300))
            )
            ListItem(
                headlineContent = { Text("シラバス") },
                leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                modifier = Modifier
                    .clickable { openUrl(classCell.syllabusUrl) }
                    .animateItem(fadeInSpec = tween(durationMillis = 300))
            )
        }

        item(key = "memo") {
            Spacer(modifier = Modifier.height(16.dp))
            DetailSectionTitle(title = "メモ")
            Text(
                // TODO: メモを編集可能にする
                text = classCell.note ?: "メモはありません。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .animateItem(fadeInSpec = tween(durationMillis = 300))
            )
        }

        item(key = "task_header") {
            Spacer(modifier = Modifier.height(16.dp))
            DetailSectionTitle(title = "関連する課題")
        }

        if (tasks.isEmpty()) {
            item(key = "no_tasks") {
                Text(
                    text = "関連する課題はありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .animateItem(fadeInSpec = tween(durationMillis = 300))
                )
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                TaskListItem(
                    task = task,
                    onTaskClick = { openUrl(task.url) },
                    modifier = Modifier.animateItem(fadeInSpec = tween(durationMillis = 300))
                )
            }
        }
    }
}

@Composable
fun DetailSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Int.toDayOfWeekString(): String {
    return when (this) {
        0 -> "月曜日"
        1 -> "火曜日"
        2 -> "水曜日"
        3 -> "木曜日"
        4 -> "金曜日"
        5 -> "土曜日"
        6 -> "日曜日"
        else -> "不明"
    }
}