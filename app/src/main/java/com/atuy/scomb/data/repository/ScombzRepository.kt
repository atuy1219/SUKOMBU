// FILE: app/src/main/java/com/atuy/scomb/data/repository/ScombzRepository.kt

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

        val cachedTasks = taskDao.getAllTasks()
        if (cachedTasks.isNotEmpty() && !forceRefresh) {
            return cachedTasks
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

        val cachedTimetable = classCellDao.getCells(timetableTitle)
        if (cachedTimetable.isNotEmpty() && !forceRefresh) {
            return cachedTimetable
        }

        val newTimetable = scraper.fetchTimetable(sessionId, year, term)
        for (cell in newTimetable) {
            classCellDao.insertClassCell(cell)
        }
        return newTimetable
    }

    suspend fun getNews(forceRefresh: Boolean): List<NewsItem> {
        val sessionId = sessionManager.sessionIdFlow.firstOrNull()
            ?: throw IllegalStateException("Not logged in")

        val cachedNews = newsItemDao.getAllNews()
        if (cachedNews.isNotEmpty() && !forceRefresh) {
            return cachedNews
        }

        val newNews = scraper.fetchNews(sessionId)
        for (newsItem in newNews) {
            newsItemDao.insertOrUpdateNewsItem(newsItem)
        }
        return newNews
    }
}