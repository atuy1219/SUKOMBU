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
    data object Loading : NewsUiState

    data class Success(
        val filteredNews: List<NewsItem>,
        val allCategories: List<String>,
        val filter: NewsFilter,
        val searchError: String?,
        val isSearchActive: Boolean,
        val isSelectionMode: Boolean = false,
        val selectedNewsIds: Set<String> = emptySet(),
        val selectedNewsAreAllRead: Boolean = false,
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
                    isSearchActive = previousSuccess?.isSearchActive ?: false,
                    selectedNewsIds = previousSuccess?.selectedNewsIds.orEmpty()
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
        if (!newsItem.unread) return
        updateNewsUnreadState(
            newsIds = setOf(newsItem.newsId),
            unread = false,
            exitSelectionMode = false
        )
    }

    fun toggleSelectedReadState() {
        val currentState = _uiState.value as? NewsUiState.Success ?: return
        val selectedItems = allNews.filter { it.newsId in currentState.selectedNewsIds }
        if (selectedItems.isEmpty()) return

        val shouldMarkUnread = selectedItems.all { !it.unread }
        updateNewsUnreadState(
            newsIds = currentState.selectedNewsIds,
            unread = shouldMarkUnread,
            exitSelectionMode = true
        )
    }

    private fun updateNewsUnreadState(
        newsIds: Set<String>,
        unread: Boolean,
        exitSelectionMode: Boolean
    ) {
        if (newsIds.isEmpty()) return

        viewModelScope.launch {
            val changedItems = allNews.filter { item ->
                item.newsId in newsIds && item.unread != unread
            }
            repository.setNewsUnread(changedItems, unread)

            allNews = allNews.map { item ->
                if (item.newsId in newsIds) item.copy(unread = unread) else item
            }

            val currentState = _uiState.value as? NewsUiState.Success ?: return@launch
            val selectedIds = if (exitSelectionMode) emptySet() else currentState.selectedNewsIds
            _uiState.value = buildSuccessState(
                currentState = currentState,
                filter = currentState.filter,
                selectedNewsIds = selectedIds,
                isSelectionMode = !exitSelectionMode && selectedIds.isNotEmpty()
            )
        }
    }

    fun updateFilter(newFilter: NewsFilter) {
        val currentState = _uiState.value as? NewsUiState.Success ?: return
        _uiState.value = buildSuccessState(
            currentState = currentState,
            filter = newFilter,
            selectedNewsIds = currentState.selectedNewsIds,
            isSelectionMode = currentState.isSelectionMode
        )
    }

    fun toggleSearchActive() {
        val currentState = _uiState.value as? NewsUiState.Success ?: return
        _uiState.value = currentState.copy(
            isSearchActive = !currentState.isSearchActive
        )
    }

    fun clearSelection() {
        val currentState = _uiState.value as? NewsUiState.Success ?: return
        _uiState.value = currentState.copy(
            isSelectionMode = false,
            selectedNewsIds = emptySet(),
            selectedNewsAreAllRead = false
        )
    }

    fun toggleNewsSelection(newsId: String) {
        val currentState = _uiState.value as? NewsUiState.Success ?: return
        val selectedIds = currentState.selectedNewsIds.toggle(newsId)
        _uiState.value = currentState.copy(
            isSelectionMode = selectedIds.isNotEmpty(),
            selectedNewsIds = selectedIds,
            selectedNewsAreAllRead = selectedIds.areAllRead()
        )
    }

    fun toggleSelectAllFiltered() {
        val currentState = _uiState.value as? NewsUiState.Success ?: return
        val visibleIds = currentState.filteredNews.map(NewsItem::newsId).toSet()
        if (visibleIds.isEmpty()) return

        val allVisibleSelected = visibleIds.all { it in currentState.selectedNewsIds }
        val selectedIds = if (allVisibleSelected) {
            currentState.selectedNewsIds - visibleIds
        } else {
            currentState.selectedNewsIds + visibleIds
        }

        _uiState.value = currentState.copy(
            isSelectionMode = selectedIds.isNotEmpty(),
            selectedNewsIds = selectedIds,
            selectedNewsAreAllRead = selectedIds.areAllRead()
        )
    }

    private fun createSuccessState(
        filter: NewsFilter,
        isSearchActive: Boolean,
        selectedNewsIds: Set<String>
    ): NewsUiState.Success {
        val categories = allNews
            .map(NewsItem::category)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()

        val normalizedFilter = filter.copy(
            selectedCategories = filter.selectedCategories.intersect(categories.toSet())
        )
        val existingIds = allNews.map(NewsItem::newsId).toSet()
        val normalizedSelectedIds = selectedNewsIds.intersect(existingIds)
        val filtered = applyFilters(allNews, normalizedFilter)

        return NewsUiState.Success(
            filteredNews = filtered.items,
            allCategories = categories,
            filter = normalizedFilter,
            searchError = filtered.error,
            isSearchActive = isSearchActive,
            isSelectionMode = normalizedSelectedIds.isNotEmpty(),
            selectedNewsIds = normalizedSelectedIds,
            selectedNewsAreAllRead = normalizedSelectedIds.areAllRead(),
            isRefreshing = false
        )
    }

    private fun buildSuccessState(
        currentState: NewsUiState.Success,
        filter: NewsFilter,
        selectedNewsIds: Set<String>,
        isSelectionMode: Boolean
    ): NewsUiState.Success {
        val filtered = applyFilters(allNews, filter)
        return currentState.copy(
            filteredNews = filtered.items,
            filter = filter,
            searchError = filtered.error,
            isSelectionMode = isSelectionMode,
            selectedNewsIds = selectedNewsIds,
            selectedNewsAreAllRead = selectedNewsIds.areAllRead()
        )
    }

    private fun applyFilters(
        news: List<NewsItem>,
        filter: NewsFilter
    ): FilteredNews {
        val matcher = NewsSearchMatcher.parse(filter.searchQuery)
        val filteredNews = news.filter { item ->
            val unreadMatches = !filter.unreadOnly || item.unread
            val categoryMatches = filter.selectedCategories.isEmpty() ||
                item.category in filter.selectedCategories

            matcher.matches(item) && unreadMatches && categoryMatches
        }
        return FilteredNews(filteredNews, matcher.error)
    }

    private fun Set<String>.areAllRead(): Boolean {
        if (isEmpty()) return false
        val selectedItems = allNews.filter { it.newsId in this }
        return selectedItems.size == size && selectedItems.all { !it.unread }
    }

    private data class FilteredNews(
        val items: List<NewsItem>,
        val error: String?
    )
}

private fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}
