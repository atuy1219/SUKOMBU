package com.atuy.scomb.ui.features

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.ui.viewmodel.NewsUiState
import com.atuy.scomb.ui.viewmodel.NewsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    paddingValues: PaddingValues,
    viewModel: NewsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues) // Scaffoldからのpaddingを適用
    ) {
        when (val state = uiState) {
            is NewsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is NewsUiState.Success -> {
                NewsList(newsItems = state.news)
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
fun NewsList(newsItems: List<NewsItem>) {
    LazyColumn(
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        items(newsItems) { news ->
            NewsListItem(newsItem = news)
            HorizontalDivider()
        }
    }
}

@Composable
fun NewsListItem(newsItem: NewsItem) {
    ListItem(
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