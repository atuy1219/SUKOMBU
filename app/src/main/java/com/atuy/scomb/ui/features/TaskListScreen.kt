package com.atuy.scomb.ui.features

import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.R
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

    LaunchedEffect(viewModel) {
        viewModel.openUrlEvent.collect { url ->
            try {
                customTabsIntent.launchUrl(context, url.toUri())
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.error_url_fetch_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TaskListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is TaskListUiState.Success -> {
                AnimatedVisibility(
                    visible = state.isSearchActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(bottom = 8.dp)
                    ) {
                        TaskSearchBar(
                            searchQuery = state.filter.searchQuery,
                            onSearchQueryChanged = { query ->
                                viewModel.updateFilter(state.filter.copy(searchQuery = query))
                            }
                        )
                        FilterBar(
                            filter = state.filter,
                            onFilterChanged = { newFilter -> viewModel.updateFilter(newFilter) }
                        )
                    }
                }

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
                            viewModel.onTaskClick(task)
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

@Composable
fun TaskSearchBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text(stringResource(R.string.task_list_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChanged("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
    )
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
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filter.showAssignments,
            onClick = { onFilterChanged(filter.copy(showAssignments = !filter.showAssignments)) },
            label = { Text(stringResource(R.string.task_list_filter_assignment)) }
        )
        FilterChip(
            selected = filter.showTests,
            onClick = { onFilterChanged(filter.copy(showTests = !filter.showTests)) },
            label = { Text(stringResource(R.string.task_list_filter_test)) }
        )
        FilterChip(
            selected = filter.showSurveys,
            onClick = { onFilterChanged(filter.copy(showSurveys = !filter.showSurveys)) },
            label = { Text(stringResource(R.string.task_list_filter_survey)) }
        )
        Spacer(modifier = Modifier.weight(1f))
        FilterChip(
            selected = filter.showCompleted,
            onClick = { onFilterChanged(filter.copy(showCompleted = !filter.showCompleted)) },
            label = { Text(stringResource(R.string.task_list_filter_completed)) },
            leadingIcon = {
                if (filter.showCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.task_list_filter_completed),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
    }
}

@Composable
fun TaskList(tasks: List<Task>, onTaskClick: (Task) -> Unit) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.task_list_no_tasks),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(task = task, onTaskClick = onTaskClick)
            }
        }
    }
}

@Composable
fun TaskCard(
    task: Task,
    onTaskClick: (Task) -> Unit
) {
    val accentColor = when (task.taskType) {
        0 -> MaterialTheme.colorScheme.primary      // 課題
        1 -> MaterialTheme.colorScheme.error        // テスト
        2 -> MaterialTheme.colorScheme.secondary    // アンケート
        else -> MaterialTheme.colorScheme.tertiary  // その他
    }

    val isOverdue = task.deadline < System.currentTimeMillis() && !task.done
    val deadlineColor =
        if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTaskClick(task) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp)
    ) {
        // IntrinsicSize.Minを使用することで、コンテンツの高さに合わせてRowの高さを決定し、
        // その高さに合わせて左側のカラーバー(Box)を伸縮させます。
        Row(modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)) {
            // 左端のカラーバー
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        // maxLinesを削除または増やして、タイトルが長くても表示されるようにする
                        // ここでは念のためmaxLines = 2にしておくが、無制限でも良い
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    // 完了済みバッジ
                    if (task.done) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.task_list_done),
                            tint = MaterialTheme.colorScheme.green,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = task.className,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1 // クラス名は1行で省略してもそれほど問題ないことが多い
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateUtils.timeToString(task.deadline),
                        style = MaterialTheme.typography.labelMedium,
                        color = deadlineColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!task.done) {
                        Text(
                            text = DateUtils.formatRemainingTime(task.deadline),
                            style = MaterialTheme.typography.labelMedium,
                            color = deadlineColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

val androidx.compose.material3.ColorScheme.green: androidx.compose.ui.graphics.Color
    get() = androidx.compose.ui.graphics.Color(0xFF4CAF50)