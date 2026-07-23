package com.atuy.scomb.ui.features

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.ui.viewmodel.NewsFilter
import com.atuy.scomb.ui.viewmodel.NewsUiState
import com.atuy.scomb.ui.viewmodel.NewsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    viewModel: NewsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is NewsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is NewsUiState.Success -> {
                AnimatedVisibility(
                    visible = state.isSearchActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    FilterBar(
                        filter = state.filter,
                        categories = state.allCategories,
                        authors = state.allAuthors,
                        onFilterChanged = viewModel::updateFilter
                    )
                }

                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.fetchNews(forceRefresh = true) },
                    modifier = Modifier.weight(1f)
                ) {
                    NewsList(
                        newsItems = state.filteredNews,
                        onItemClick = { newsItem ->
                            viewModel.markAsRead(newsItem)
                            context.openNewsUrl(newsItem.url)
                        }
                    )
                }
            }

            is NewsUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.fetchNews(forceRefresh = true) }
                )
            }
        }
    }
}

private fun Context.openNewsUrl(url: String) {
    CustomTabsIntent.Builder()
        .build()
        .launchUrl(this, url.toUri())
}

@Composable
fun FilterBar(
    filter: NewsFilter,
    categories: List<String>,
    authors: List<String>,
    onFilterChanged: (NewsFilter) -> Unit
) {
    var searchQuery by remember(filter.searchQuery) {
        mutableStateOf(filter.searchQuery)
    }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("タイトルを検索") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            onFilterChanged(filter.copy(searchQuery = ""))
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "クリア")
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onFilterChanged(filter.copy(searchQuery = searchQuery))
                    focusManager.clearFocus()
                }
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.unreadOnly,
                onClick = {
                    onFilterChanged(filter.copy(unreadOnly = !filter.unreadOnly))
                },
                label = { Text("未読のみ") }
            )

            MultiSelectFilterChip(
                label = "カテゴリ",
                dialogTitle = "カテゴリを選択",
                options = categories,
                selectedOptions = filter.selectedCategories,
                onSelectionChanged = {
                    onFilterChanged(filter.copy(selectedCategories = it))
                }
            )

            MultiSelectFilterChip(
                label = "教授",
                dialogTitle = "教授名を選択",
                options = authors,
                selectedOptions = filter.selectedAuthors,
                onSelectionChanged = {
                    onFilterChanged(filter.copy(selectedAuthors = it))
                }
            )
        }
    }
}

@Composable
fun MultiSelectFilterChip(
    label: String,
    dialogTitle: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val chipLabel = if (selectedOptions.isEmpty()) {
        label
    } else {
        "$label (${selectedOptions.size})"
    }

    AssistChip(
        onClick = { showDialog = true },
        label = { Text(chipLabel) },
        trailingIcon = {
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        },
        enabled = options.isNotEmpty()
    )

    if (showDialog) {
        var temporarySelection by remember(selectedOptions) {
            mutableStateOf(selectedOptions)
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(dialogTitle) },
            text = {
                LazyColumn {
                    items(options, key = { it }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    temporarySelection = temporarySelection.toggle(option)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = option in temporarySelection,
                                onCheckedChange = {
                                    temporarySelection = temporarySelection.toggle(option)
                                }
                            )
                            Text(
                                text = option,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelectionChanged(temporarySelection)
                        showDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

private fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}

@Composable
fun NewsList(
    newsItems: List<NewsItem>,
    onItemClick: (NewsItem) -> Unit
) {
    if (newsItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "表示するお知らせがありません。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = newsItems,
            key = NewsItem::newsId
        ) { news ->
            NewsCard(
                newsItem = news,
                onClick = { onItemClick(news) }
            )
        }
    }
}

@Composable
fun NewsCard(
    newsItem: NewsItem,
    onClick: () -> Unit
) {
    val alpha = if (newsItem.unread) 1.0f else 0.6f
    val containerColor = if (newsItem.unread) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (newsItem.unread) 2.dp else 0.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = newsItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (newsItem.unread) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (newsItem.unread) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, top = 4.dp)
                            .size(8.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = newsItem.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = newsItem.domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = newsItem.publishTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
