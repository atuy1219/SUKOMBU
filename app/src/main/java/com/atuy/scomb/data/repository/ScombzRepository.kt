package com.atuy.scomb.data.repository

import android.util.Log
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

        // Note: This doesn't clear old tasks, it just updates/inserts new ones.
        // Consider a clearing strategy if tasks can be removed from the source.
        for (task in allTasks) {
            taskDao.insertOrUpdateTask(task)
        }
        return allTasks
    }

    suspend fun getTimetable(year: Int, term: String, forceRefresh: Boolean): List<ClassCell> {
        val sessionId = sessionManager.sessionIdFlow.firstOrNull()
            ?: throw IllegalStateException("Not logged in")
        val timetableTitle = "$year-$term"
        Log.d("Repository", "getTimetable called for $timetableTitle, forceRefresh=$forceRefresh")

        if (forceRefresh) {
            Log.d("Repository", "Forcing refresh, removing old timetable for $timetableTitle")
            classCellDao.removeTimetable(timetableTitle)
        } else {
            val cachedTimetable = classCellDao.getCells(timetableTitle)
            if (cachedTimetable.isNotEmpty()) {
                Log.d("Repository", "Returning ${cachedTimetable.size} cached cells for $timetableTitle")
                return cachedTimetable
            }
        }

        Log.d("Repository", "No cache found or refresh forced, fetching new timetable for $timetableTitle")
        val newTimetable = scraper.fetchTimetable(sessionId, year, term)
        for (cell in newTimetable) {
            classCellDao.insertClassCell(cell)
        }
        Log.d("Repository", "Fetched and cached ${newTimetable.size} new cells for $timetableTitle")
        return newTimetable
    }

    suspend fun getNews(forceRefresh: Boolean): List<NewsItem> {
        val sessionId = sessionManager.sessionIdFlow.firstOrNull()
            ?: throw IllegalStateException("Not logged in")

        if (forceRefresh) {
            newsItemDao.clearAll()
        } else {
            val cachedNews = newsItemDao.getAllNews()
            if (cachedNews.isNotEmpty()) {
                return cachedNews
            }
        }

        val newNews = scraper.fetchNews(sessionId)
        for (newsItem in newNews) {
            newsItemDao.insertOrUpdateNewsItem(newsItem)
        }
        return newNews
    }
}

