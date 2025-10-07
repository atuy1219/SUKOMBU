package com.atuy.scomb.data.repository

import android.util.Log
import com.atuy.scomb.data.AuthManager
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.ClassCellDao
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.NewsItemDao
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.db.TaskDao
import com.atuy.scomb.data.network.ScombzApiService
import com.atuy.scomb.util.SessionExpiredException
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject

class ScombzRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val classCellDao: ClassCellDao,
    private val newsItemDao: NewsItemDao,
    private val apiService: ScombzApiService,
    private val authManager: AuthManager
) {
    suspend fun login(userId: String, userPw: String): Result<Unit> {
        return try {
            val response = apiService.login(com.atuy.scomb.data.network.LoginRequest(userId, userPw))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                if (body.status == "OK" && body.token != null) {
                    authManager.saveAuthToken(body.token)
                    Result.success(Unit)
                } else {
                    val errorMessage = if (body.status != "OK") body.status else "ログインに失敗しました: トークンがありません"
                    Result.failure(Exception(errorMessage))
                }
            } else {
                Result.failure(Exception("ログインに失敗しました: ${response.message()} (Code: ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun ensureAuthenticated() {
        if (authManager.authTokenFlow.first() == null) {
            throw SessionExpiredException()
        }
    }

    suspend fun getTasksAndSurveys(forceRefresh: Boolean): List<Task> {
        if (!forceRefresh) {
            val cachedTasks = taskDao.getAllTasks()
            if (cachedTasks.isNotEmpty()) return cachedTasks
        }

        ensureAuthenticated()
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val yearMonth = String.format("%d%02d", year, month)

        val response = apiService.getTasks(yearMonth)
        if (!response.isSuccessful) {
            if (response.code() == 401) throw SessionExpiredException()
            val errorBody = response.errorBody()?.string()
            Log.e("ScombzRepository", "Task fetch failed: ${response.code()} - $errorBody")
            throw Exception("課題の取得に失敗しました: ${response.code()}")
        }

        val apiTasks = response.body() ?: emptyList()
        val dbTasks = apiTasks.mapNotNull { it.toDbTask() }

        dbTasks.forEach { taskDao.insertOrUpdateTask(it) }
        return dbTasks
    }

    suspend fun getTimetable(year: Int, term: String, forceRefresh: Boolean): List<ClassCell> {
        val timetableTitle = "$year-$term"
        if (!forceRefresh) {
            val cachedTimetable = classCellDao.getCells(timetableTitle)
            if (cachedTimetable.isNotEmpty()) return cachedTimetable
        }

        ensureAuthenticated()
        // APIの `yearMonth` は前期が "04", 後期が "10" 始まりと仮定
        val yearMonth = if(term == "1") "${year}04" else "${year}10"

        val response = apiService.getTimetable(yearMonth)
        if (!response.isSuccessful) {
            if (response.code() == 401) throw SessionExpiredException()
            val errorBody = response.errorBody()?.string()
            Log.e("ScombzRepository", "Timetable fetch failed: ${response.code()} - $errorBody")
            throw Exception("時間割の取得に失敗しました: ${response.code()}")
        }

        val apiClassCells = response.body() ?: emptyList()
        val dbClassCells = apiClassCells.map { it.toDbClassCell(year, term, timetableTitle) }

        classCellDao.removeTimetable(timetableTitle)
        dbClassCells.forEach { classCellDao.insertClassCell(it) }
        return dbClassCells
    }

    suspend fun getNews(forceRefresh: Boolean): List<NewsItem> {
        if (!forceRefresh) {
            val cachedNews = newsItemDao.getAllNews()
            if (cachedNews.isNotEmpty()) return cachedNews
        }

        ensureAuthenticated()
        val response = apiService.getNews()
        if (!response.isSuccessful) {
            if (response.code() == 401) throw SessionExpiredException()
            val errorBody = response.errorBody()?.string()
            Log.e("ScombzRepository", "News fetch failed: ${response.code()} - $errorBody")
            throw Exception("お知らせの取得に失敗しました: ${response.code()}")
        }

        val apiNews = response.body() ?: emptyList()
        val dbNews = apiNews.map { it.toDbNewsItem() }

        newsItemDao.clearAll()
        dbNews.forEach { newsItemDao.insertOrUpdateNewsItem(it) }
        return newsItemDao.getAllNews()
    }

    suspend fun markAsRead(newsItem: NewsItem) {
        newsItemDao.insertOrUpdateNewsItem(newsItem.copy(unread = false))
    }
}

