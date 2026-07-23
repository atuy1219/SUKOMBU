package com.atuy.scomb.ui.features

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.hasSourceName
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
                        searchError = state.searchError,
                        onFilterChanged = viewModel::updateFilter
                    )
                }

                AnimatedVisibility(visible = state.isSelectionMode) {
                    NewsSelectionBar(
                        state = state,
                        onClearSelection = viewModel::clearSelection,
                        onToggleSelectAll = viewModel::toggleSelectAllFiltered,
                        onToggleReadState = viewModel::toggleSelectedReadState
                    )
                }

                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.fetchNews(forceRefresh = true) },
                    modifier = Modifier.weight(1f)
                ) {
                    NewsList(
                        newsItems = state.filteredNews,
                        isSelectionMode = state.isSelectionMode,
                        selectedNewsIds = state.selectedNewsIds,
                        onItemOpen = { newsItem ->
                            viewModel.markAsRead(newsItem)
                            context.openNewsUrl(newsItem.url)
                        },
                        onSelectionToggle = viewModel::toggleNewsSelection
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
private fun NewsSelectionBar(
    state: NewsUiState.Success,
    onClearSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleReadState: () -> Unit
) {
    val visibleIds = state.filteredNews.map(NewsItem::newsId).toSet()
    val allVisibleSelected = visibleIds.isNotEmpty() &&
        visibleIds.all { it in state.selectedNewsIds }

    Surface(tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${state.selectedNewsIds.size}件選択",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
            TextButton(
                onClick = onToggleSelectAll,
                enabled = state.filteredNews.isNotEmpty()
            ) {
                Text(if (allVisibleSelected) "全て解除" else "全て選択")
            }
            TextButton(
                onClick = onToggleReadState,
                enabled = state.selectedNewsIds.isNotEmpty()
            ) {
                Text(if (state.selectedNewsAreAllRead) "未読" else "既読")
            }
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "選択を終了")
            }
        }
    }
}

@Composable
fun NewsList(
    newsItems: List<NewsItem>,
    isSelectionMode: Boolean,
    selectedNewsIds: Set<String>,
    onItemOpen: (NewsItem) -> Unit,
    onSelectionToggle: (String) -> Unit
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
                isSelectionMode = isSelectionMode,
                isSelected = news.newsId in selectedNewsIds,
                onClick = {
                    if (isSelectionMode) {
                        onSelectionToggle(news.newsId)
                    } else {
                        onItemOpen(news)
                    }
                },
                onLongClick = { onSelectionToggle(news.newsId) },
                onSelectionToggle = { onSelectionToggle(news.newsId) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsCard(
    newsItem: NewsItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectionToggle: () -> Unit
) {
    val alpha = if (newsItem.unread || isSelected) 1.0f else 0.6f
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        newsItem.unread -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (newsItem.unread || isSelected) 2.dp else 0.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectionToggle() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

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
                    if (newsItem.hasSourceName) {
                        Text(
                            text = "発信元: ${newsItem.domain}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
