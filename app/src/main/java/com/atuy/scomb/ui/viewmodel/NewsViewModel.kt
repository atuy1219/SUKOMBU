package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.manager.AutoRefreshManager
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsFilter(
    val searchQuery: String = "",
    val unreadOnly: Boolean = false,
    val selectedCategories: Set<String> = emptySet(),
    val selectedAuthors: Set<String> = emptySet()
)

sealed interface NewsUiState {
    data object Loading : NewsUiState

    data class Success(
        val filteredNews: List<NewsItem>,
        val allCategories: List<String>,
        val allAuthors: List<String>,
        val filter: NewsFilter,
        val isSearchActive: Boolean,
        val isRefreshing: Boolean = false
    ) : NewsUiState

    data class Error(
        val message: String,
        val isRefreshing: Boolean = false
    ) : NewsUiState
}

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: ScombzRepository,
    private val autoRefreshManager: AutoRefreshManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private var allNews: List<NewsItem> = emptyList()
    private var loadJob: Job? = null

    init {
        fetchNews(forceRefresh = false)
        observeAutoRefresh()
    }

    private fun observeAutoRefresh() {
        viewModelScope.launch {
            autoRefreshManager.refreshEvent.collect {
                fetchNews(forceRefresh = true)
            }
        }
    }

    fun fetchNews(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = if (forceRefresh && previousState is NewsUiState.Success) {
                previousState.copy(isRefreshing = true)
            } else {
                NewsUiState.Loading
            }

            try {
                allNews = repository.getNews(forceRefresh)
                    .sortedByDescending(NewsItem::publishTime)

                val previousSuccess = previousState as? NewsUiState.Success
                _uiState.value = createSuccessState(
                    filter = previousSuccess?.filter ?: NewsFilter(),
                    isSearchActive = previousSuccess?.isSearchActive ?: false
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = NewsUiState.Error(
                    message = e.message ?: "An unknown error occurred"
                )
            }
        }
    }

    fun markAsRead(newsItem: NewsItem) {
        viewModelScope.launch {
            repository.markAsRead(newsItem)
            allNews = allNews.map { item ->
                if (item.newsId == newsItem.newsId) item.copy(unread = false) else item
            }
            applyUiFilters()
        }
    }

    fun updateFilter(newFilter: NewsFilter) {
        val currentState = _uiState.value as? NewsUiState.Success ?: return
        _uiState.value = currentState.copy(
            filter = newFilter,
            filteredNews = applyFilters(allNews, newFilter)
        )
    }

    fun toggleSearchActive() {
        val currentState = _uiState.value as? NewsUiState.Success ?: return
        _uiState.value = currentState.copy(
            isSearchActive = !currentState.isSearchActive
        )
    }

    private fun applyUiFilters() {
        val currentState = _uiState.value as? NewsUiState.Success ?: return
        _uiState.value = currentState.copy(
            filteredNews = applyFilters(allNews, currentState.filter)
        )
    }

    private fun createSuccessState(
        filter: NewsFilter,
        isSearchActive: Boolean
    ): NewsUiState.Success {
        val categories = allNews
            .map(NewsItem::category)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()

        val authors = allNews
            .map(NewsItem::domain)
            .filter { it.isNotBlank() && it != UNKNOWN_AUTHOR }
            .distinct()
            .sorted()

        val normalizedFilter = filter.copy(
            selectedCategories = filter.selectedCategories.intersect(categories.toSet()),
            selectedAuthors = filter.selectedAuthors.intersect(authors.toSet())
        )

        return NewsUiState.Success(
            filteredNews = applyFilters(allNews, normalizedFilter),
            allCategories = categories,
            allAuthors = authors,
            filter = normalizedFilter,
            isSearchActive = isSearchActive,
            isRefreshing = false
        )
    }

    private fun applyFilters(
        news: List<NewsItem>,
        filter: NewsFilter
    ): List<NewsItem> {
        return news.filter { item ->
            val queryMatches = filter.searchQuery.isBlank() ||
                item.title.contains(filter.searchQuery, ignoreCase = true)
            val unreadMatches = !filter.unreadOnly || item.unread
            val categoryMatches = filter.selectedCategories.isEmpty() ||
                item.category in filter.selectedCategories
            val authorMatches = filter.selectedAuthors.isEmpty() ||
                item.domain in filter.selectedAuthors

            queryMatches && unreadMatches && categoryMatches && authorMatches
        }
    }

    private companion object {
        const val UNKNOWN_AUTHOR = "掲載元不明"
    }
}
