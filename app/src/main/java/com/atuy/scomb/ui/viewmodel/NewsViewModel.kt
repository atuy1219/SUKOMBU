package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NewsUiState {
    object Loading : NewsUiState
    data class Success(val news: List<NewsItem>, val isRefreshing: Boolean = false) : NewsUiState
    data class Error(val message: String, val isRefreshing: Boolean = false) : NewsUiState
}

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: ScombzRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        fetchNews(forceRefresh = false)
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
                val news = repository.getNews(forceRefresh)
                _uiState.value = NewsUiState.Success(news, isRefreshing = false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = NewsUiState.Error(e.message ?: "An unknown error occurred", isRefreshing = false)
                e.printStackTrace()
            }
        }
    }
}
