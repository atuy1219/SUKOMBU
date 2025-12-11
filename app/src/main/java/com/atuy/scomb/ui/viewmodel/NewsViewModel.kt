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
    val selectedCategories: Set<String> = emptySet()
)

sealed interface NewsUiState {
    object Loading : NewsUiState
    data class Success(
        val filteredNews: List<NewsItem>,
        val allCategories: List<String>,
        val filter: NewsFilter,
        val isSearchActive: Boolean,
        val isRefreshing: Boolean = false
    ) : NewsUiState

    data class Error(val message: String, val isRefreshing: Boolean = false) : NewsUiState
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
            val currentState = _uiState.value
            if (forceRefresh && currentState is NewsUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            } else {
                _uiState.value = NewsUiState.Loading
            }

            try {
                allNews = repository.getNews(forceRefresh).sortedByDescending { it.publishTime }
                val categories = allNews.map { it.category }.distinct()
                val initialFilter =
                    if (currentState is NewsUiState.Success) currentState.filter else NewsFilter()
                val isSearchActive =
                    if (currentState is NewsUiState.Success) currentState.isSearchActive else false

                _uiState.value = NewsUiState.Success(
                    filteredNews = applyFilters(allNews, initialFilter),
                    allCategories = categories,
                    filter = initialFilter,
                    isSearchActive = isSearchActive,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = NewsUiState.Error(
                    e.message ?: "An unknown error occurred",
                    isRefreshing = false
                )
            }
        }
    }

    fun markAsRead(newsItem: NewsItem) {
        viewModelScope.launch {
            repository.markAsRead(newsItem)
            allNews =
                allNews.map { if (it.newsId == newsItem.newsId) it.copy(unread = false) else it }
            applyUiFilters()
        }
    }

    fun updateFilter(newFilter: NewsFilter) {
        val currentState = _uiState.value
        if (currentState is NewsUiState.Success) {
            _uiState.value = currentState.copy(
                filter = newFilter,
                filteredNews = applyFilters(allNews, newFilter)
            )
        }
    }

    fun toggleSearchActive() {
        val currentState = _uiState.value
        if (currentState is NewsUiState.Success) {
            _uiState.value = currentState.copy(isSearchActive = !currentState.isSearchActive)
        }
    }

    private fun applyUiFilters() {
        val currentState = _uiState.value
        if (currentState is NewsUiState.Success) {
            _uiState.value = currentState.copy(
                filteredNews = applyFilters(allNews, currentState.filter)
            )
        }
    }

    private fun applyFilters(news: List<NewsItem>, filter: NewsFilter): List<NewsItem> {
        return news.filter { item ->
            val queryMatch = if (filter.searchQuery.isBlank()) {
                true
            } else {
                item.title.contains(filter.searchQuery, ignoreCase = true)
            }
            val unreadMatch = if (filter.unreadOnly) {
                item.unread
            } else {
                true
            }
            val categoryMatch = if (filter.selectedCategories.isEmpty()) {
                true
            } else {
                item.category in filter.selectedCategories
            }
            queryMatch && unreadMatch && categoryMatch
        }
    }
}