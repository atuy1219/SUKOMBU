package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeData(
    val upcomingTasks: List<Task>,
    val todaysClasses: List<ClassCell>,
    val recentNews: List<NewsItem>
)

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val homeData: HomeData, val isRefreshing: Boolean = false) : HomeUiState
    data class Error(val message: String, val isRefreshing: Boolean = false) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScombzRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadHomeData(forceRefresh = false)
    }

    fun loadHomeData(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val currentState = _uiState.value
            if (forceRefresh && currentState is HomeUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            } else {
                _uiState.value = HomeUiState.Loading
            }

            try {
                coroutineScope {
                    val tasksDeferred = async { repository.getTasksAndSurveys(forceRefresh) }

                    val calendar = Calendar.getInstance()
                    val year =
                        if (calendar.get(Calendar.MONTH) < 3) calendar.get(Calendar.YEAR) - 1 else calendar.get(
                            Calendar.YEAR
                        )
                    val term = if (calendar.get(Calendar.MONTH) in 3..8) "1" else "2"
                    val timetableDeferred =
                        async { repository.getTimetable(year, term, forceRefresh) }

                    val newsDeferred = async { repository.getNews(forceRefresh) }

                    val tasks = tasksDeferred.await()
                    val timetable = timetableDeferred.await()
                    val news = newsDeferred.await()

                    val upcomingTasks = tasks
                        .filter { it.deadline > System.currentTimeMillis() && !it.done }
                        .take(3)

                    val todayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    val todaysClasses = timetable
                        .filter { it.dayOfWeek == todayOfWeek }
                        .sortedBy { it.period }

                    val recentNews = news.take(3)

                    _uiState.value = HomeUiState.Success(
                        HomeData(
                            upcomingTasks = upcomingTasks,
                            todaysClasses = todaysClasses,
                            recentNews = recentNews
                        ),
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = HomeUiState.Error(e.message ?: "不明なエラーが発生しました", isRefreshing = false)
                e.printStackTrace()
            }
        }
    }
}
