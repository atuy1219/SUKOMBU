package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NewsUiState {
    object Loading : NewsUiState
    data class Success(val news: List<NewsItem>) : NewsUiState
    data class Error(val message: String) : NewsUiState
}

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: ScombzRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState

    init {
        fetchNews(forceRefresh = false)
    }

    fun fetchNews(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.value = NewsUiState.Loading
            try {
                val news = repository.getNews(forceRefresh)
                _uiState.value = NewsUiState.Success(news)
            } catch (e: Exception) {
                _uiState.value = NewsUiState.Error(e.message ?: "An unknown error occurred")
                e.printStackTrace()
            }
        }
    }
}