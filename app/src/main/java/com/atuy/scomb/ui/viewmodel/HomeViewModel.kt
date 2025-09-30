package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.repository.ScombzRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    data class Success(val homeData: HomeData) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScombzRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadHomeData()
    }

    // publicメソッドに変更して外部から呼び出し可能にする
    fun loadHomeData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                // 課題、時間割、お知らせを並行して取得
                coroutineScope {
                    val tasksDeferred = async { repository.getTasksAndSurveys(forceRefresh) }

                    val calendar = Calendar.getInstance()
                    val year = if (calendar.get(Calendar.MONTH) < 3) calendar.get(Calendar.YEAR) - 1 else calendar.get(Calendar.YEAR)
                    val term = if (calendar.get(Calendar.MONTH) in 3..8) "1" else "2"
                    val timetableDeferred = async { repository.getTimetable(year, term, forceRefresh) }

                    val newsDeferred = async { repository.getNews(forceRefresh) }

                    // 全てのデータ取得を待つ
                    val tasks = tasksDeferred.await()
                    val timetable = timetableDeferred.await()
                    val news = newsDeferred.await()

                    // --- 取得したデータを画面表示用に加工 ---

                    // 締め切りが未来の課題を5件まで取得
                    val upcomingTasks = tasks
                        .filter { it.deadline > System.currentTimeMillis() && !it.done }
                        .take(5)

                    // 今日の曜日（日:1, 月:2 ... 土:7）を取得し、ScombZの形式 (月:0...) に変換
                    val todayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    val todaysClasses = timetable
                        .filter { it.dayOfWeek == todayOfWeek }
                        .sortedBy { it.period }

                    // 最新のお知らせを5件まで取得
                    val recentNews = news.take(5)

                    _uiState.value = HomeUiState.Success(
                        HomeData(
                            upcomingTasks = upcomingTasks,
                            todaysClasses = todaysClasses,
                            recentNews = recentNews
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "不明なエラーが発生しました")
                e.printStackTrace()
            }
        }
    }
}
