package com.atuy.scomb.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atuy.scomb.data.AuthManager
import com.atuy.scomb.data.SettingsManager
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class LinkItem(val title: String, val url: String, val appendUsername: Boolean = false)

data class HomeData(
    val upcomingTasks: List<Task>,
    val todaysClasses: List<ClassCell>,
    val recentNews: List<NewsItem>,
    val quickLinks: List<LinkItem>
)

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val homeData: HomeData,
        val showNews: Boolean = true,
        val isRefreshing: Boolean = false
    ) : HomeUiState

    data class Error(val message: String, val isRefreshing: Boolean = false) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScombzRepository,
    private val settingsManager: SettingsManager,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private val baseQuickLinks = listOf(
        LinkItem("ScombZ", "https://scombz.shibaura-it.ac.jp/"),
        LinkItem("シラバス", "https://syllabus.sic.shibaura-it.ac.jp/"),
        LinkItem("時間割検索", "https://timetable.sic.shibaura-it.ac.jp/"),
        LinkItem("学年歴", "https://www.shibaura-it.ac.jp/campus_life/school_calendar"),
        LinkItem("S*gsot", "https://sgsot.sic.shibaura-it.ac.jp", appendUsername = true),
        LinkItem("スーパー英語", "https://supereigo2.sic.shibaura-it.ac.jp/sso/"),
        LinkItem("図書館", "https://lib.shibaura-it.ac.jp")
    )

    private val _openUrlEvent = Channel<String>(Channel.BUFFERED)
    val openUrlEvent = _openUrlEvent.receiveAsFlow()


    init {
        loadHomeData(forceRefresh = false)
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsManager.showHomeNewsFlow.collect { showNews ->
                val currentState = _uiState.value
                if (currentState is HomeUiState.Success) {
                    _uiState.value = currentState.copy(showNews = showNews)
                }
            }
        }
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
                    val currentTerm = DateUtils.getCurrentScombTerm()
                    val timetableDeferred =
                        async {
                            repository.getTimetable(
                                currentTerm.year,
                                currentTerm.term,
                                forceRefresh
                            )
                        }

                    val newsDeferred = async { repository.getNews(forceRefresh) }

                    val showNewsDeferred = async { settingsManager.showHomeNewsFlow.first() }

                    val usernameDeferred = async { authManager.usernameFlow.first() }

                    val tasks = tasksDeferred.await()
                    val timetable = timetableDeferred.await()
                    val news = newsDeferred.await()
                    val showNews = showNewsDeferred.await()
                    val username = usernameDeferred.await() ?: ""

                    val upcomingTasks = tasks
                        .filter { it.deadline > System.currentTimeMillis() && !it.done }
                        .sortedBy { it.deadline }
                        .take(3)

                    val todayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    val todaysClasses = timetable
                        .filter { it.dayOfWeek == todayOfWeek }
                        .sortedBy { it.period }

                    val recentNews = news.take(3)

                    val quickLinks = baseQuickLinks.map { link ->
                        if (link.appendUsername && username.isNotEmpty()) {
                            link.copy(url = link.url + username)
                        } else {
                            link
                        }
                    }

                    _uiState.value = HomeUiState.Success(
                        HomeData(
                            upcomingTasks = upcomingTasks,
                            todaysClasses = todaysClasses,
                            recentNews = recentNews,
                            quickLinks = quickLinks
                        ),
                        showNews = showNews,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = HomeUiState.Error(
                    e.message ?: "不明なエラーが発生しました",
                    isRefreshing = false
                )
                e.printStackTrace()
            }
        }
    }

    fun onTaskClick(task: Task) {
        viewModelScope.launch {
            try {
                val url = repository.getTaskUrl(task)
                _openUrlEvent.send(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}