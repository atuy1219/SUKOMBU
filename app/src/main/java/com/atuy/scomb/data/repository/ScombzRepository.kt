package com.atuy.scomb.data.repository

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
            if (response.isSuccessful && response.body()?.status == "OK") {
                val token = response.body()?.token
                if (token != null) {
                    authManager.saveAuthToken(token)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Login successful but no token received."))
                }
            } else {
                Result.failure(Exception("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getToken(): String {
        return authManager.authTokenFlow.first() ?: throw SessionExpiredException()
    }

    suspend fun getTasksAndSurveys(forceRefresh: Boolean): List<Task> {
        if (!forceRefresh) {
            val cachedTasks = taskDao.getAllTasks()
            if (cachedTasks.isNotEmpty()) return cachedTasks
        }

        val token = getToken()
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val yearMonth = String.format("%d%02d", year, month)

        val response = apiService.getTasks(token, yearMonth)
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch tasks: ${response.code()}")
        }

        val apiTasks = response.body() ?: emptyList()
        val dbTasks = apiTasks.map { it.toDbTask() }

        dbTasks.forEach { taskDao.insertOrUpdateTask(it) }
        return dbTasks
    }

    suspend fun getTimetable(year: Int, term: String, forceRefresh: Boolean): List<ClassCell> {
        val timetableTitle = "$year-$term"
        if (!forceRefresh) {
            val cachedTimetable = classCellDao.getCells(timetableTitle)
            if (cachedTimetable.isNotEmpty()) return cachedTimetable
        }

        val token = getToken()
        val yearMonth = if(term == "1") "${year}04" else "${year}10"

        val response = apiService.getTimetable(token, yearMonth)
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch timetable: ${response.code()}")
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

        val token = getToken()
        val response = apiService.getNews(token)
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch news: ${response.code()}")
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
