package com.atuy.scomb.data.repository

import com.atuy.scomb.data.SessionManager
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.ClassCellDao
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.NewsItemDao
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.db.TaskDao
import com.atuy.scomb.data.network.ScombzScraper
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class ScombzRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val classCellDao: ClassCellDao,
    private val newsItemDao: NewsItemDao,
    private val scraper: ScombzScraper,
    private val sessionManager: SessionManager
) {
    suspend fun getTasksAndSurveys(forceRefresh: Boolean): List<Task> {
        val sessionId = sessionManager.sessionIdFlow.firstOrNull()
            ?: throw IllegalStateException("Not logged in")

        if (!forceRefresh) {
            val cachedTasks = taskDao.getAllTasks()
            if (cachedTasks.isNotEmpty()) {
                return cachedTasks
            }
        }

        val newTasks = scraper.fetchTasks(sessionId)
        val newSurveys = scraper.fetchSurveys(sessionId)
        val allTasks = (newTasks + newSurveys).distinctBy { it.id }

        for (task in allTasks) {
            taskDao.insertOrUpdateTask(task)
        }
        return allTasks
    }

    suspend fun getTimetable(year: Int, term: String, forceRefresh: Boolean): List<ClassCell> {
        val sessionId = sessionManager.sessionIdFlow.firstOrNull()
            ?: throw IllegalStateException("Not logged in")
        val timetableTitle = "$year-$term"

        if (!forceRefresh) {
            val cachedTimetable = classCellDao.getCells(timetableTitle)
            if (cachedTimetable.isNotEmpty()) {
                return cachedTimetable
            }
        }

        val newTimetable = scraper.fetchTimetable(sessionId, year, term)
        if (forceRefresh) {
            classCellDao.removeTimetable(timetableTitle)
        }
        for (cell in newTimetable) {
            classCellDao.insertClassCell(cell)
        }
        return newTimetable
    }

    suspend fun getNews(forceRefresh: Boolean): List<NewsItem> {
        val sessionId = sessionManager.sessionIdFlow.firstOrNull() ?: throw IllegalStateException("Not logged in")

        if (!forceRefresh) {
            val cachedNews = newsItemDao.getAllNews()
            if (cachedNews.isNotEmpty()) {
                return cachedNews
            }
        }

        val newNews = scraper.fetchNews(sessionId)
        if (forceRefresh) {
            newsItemDao.clearAll()
        }
        for (newsItem in newNews) {
            newsItemDao.insertOrUpdateNewsItem(newsItem)
        }
        return newsItemDao.getAllNews()
    }

    suspend fun markAsRead(newsItem: NewsItem) {
        newsItemDao.insertOrUpdateNewsItem(newsItem.copy(unread = false))
    }
}

