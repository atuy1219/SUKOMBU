package com.atuy.scomb.ui.features

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

    fun openUrlInCustomTab(context: Context, url: String) {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(context, url.toUri())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is NewsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is NewsUiState.Success -> {
                AnimatedVisibility(visible = state.isSearchActive) {
                    FilterBar(
                        filter = state.filter,
                        categories = state.allCategories,
                        onFilterChanged = { viewModel.updateFilter(it) }
                    )
                }

                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = {
                        viewModel.fetchNews(forceRefresh = true)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    NewsList(
                        newsItems = state.filteredNews,
                        onItemClick = { newsItem ->
                            viewModel.markAsRead(newsItem)
                            openUrlInCustomTab(context, newsItem.url)
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

@Composable
fun FilterBar(
    filter: NewsFilter,
    categories: List<String>,
    onFilterChanged: (NewsFilter) -> Unit
) {
    var searchQuery by remember { mutableStateOf(filter.searchQuery) }
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("タイトルを検索") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onFilterChanged(filter.copy(searchQuery = searchQuery))
                focusManager.clearFocus()
            })
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.unreadOnly,
                onClick = { onFilterChanged(filter.copy(unreadOnly = !filter.unreadOnly)) },
                label = { Text("未読のみ") }
            )
            MultiSelectCategorySelector(
                allCategories = categories,
                selectedCategories = filter.selectedCategories,
                onSelectionChanged = { onFilterChanged(filter.copy(selectedCategories = it)) }
            )
        }
    }
}

@Composable
fun MultiSelectCategorySelector(
    allCategories: List<String>,
    selectedCategories: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val buttonText = if (selectedCategories.isEmpty()) {
        "カテゴリ"
    } else {
        "カテゴリ (${selectedCategories.size})"
    }

    TextButton(onClick = { showDialog = true }) {
        Text(buttonText)
        Icon(Icons.Default.ArrowDropDown, contentDescription = "カテゴリを選択")
    }

    if (showDialog) {
        val tempSelection = remember { mutableStateOf(selectedCategories) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("カテゴリを選択") },
            text = {
                LazyColumn {
                    items(allCategories) { category ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val currentSelection = tempSelection.value.toMutableSet()
                                    if (category in currentSelection) {
                                        currentSelection.remove(category)
                                    } else {
                                        currentSelection.add(category)
                                    }
                                    tempSelection.value = currentSelection
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = category in tempSelection.value,
                                onCheckedChange = { isChecked ->
                                    val currentSelection = tempSelection.value.toMutableSet()
                                    if (isChecked) {
                                        currentSelection.add(category)
                                    } else {
                                        currentSelection.remove(category)
                                    }
                                    tempSelection.value = currentSelection
                                }
                            )
                            Text(category, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelectionChanged(tempSelection.value)
                        showDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("キャンセル")
                }
            }
        )
    }
}


@Composable
fun NewsList(newsItems: List<NewsItem>, onItemClick: (NewsItem) -> Unit) {
    if (newsItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("表示するお知らせがありません。")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(newsItems) { news ->
                NewsListItem(
                    newsItem = news,
                    onClick = { onItemClick(news) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun NewsListItem(newsItem: NewsItem, onClick: () -> Unit) {
    val alpha = if (newsItem.unread) 1.0f else 0.6f

    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .graphicsLayer(alpha = alpha),
        headlineContent = {
            Text(
                text = newsItem.title,
                fontWeight = if (newsItem.unread) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = {
            Column {
                Text(text = "カテゴリ: ${newsItem.category}")
                Text(text = "掲載元: ${newsItem.domain}")
            }
        },
        trailingContent = {
            Text(text = newsItem.publishTime)
        }
    )
}

