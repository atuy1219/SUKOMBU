package com.atuy.scomb.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.atuy.scomb.data.SessionManager
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.ClassCellDao
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.NewsItemDao
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.db.TaskDao
import com.atuy.scomb.data.network.ScombzScraper
import com.atuy.scomb.receiver.NotificationReceiver
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class ScombzRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val classCellDao: ClassCellDao,
    private val newsItemDao: NewsItemDao, // NewsItemDaoを追加
    private val scraper: ScombzScraper,
    private val sessionManager: SessionManager,
    private val context: Context
) {
    /**
     * 課題とアンケートを全て取得する
     */
    suspend fun getTasksAndSurveys(forceRefresh: Boolean): List<Task> {
        val sessionId = sessionManager.sessionIdFlow.firstOrNull() ?: throw Exception("Not logged in")

        val cachedTasks = taskDao.getAllTasks()
        if (cachedTasks.isNotEmpty() && !forceRefresh) {
            return cachedTasks
        }

        // ネットワークから課題とアンケートの両方を取得
        val newTasks = scraper.fetchTasks(sessionId)
        val newSurveys = scraper.fetchSurveys(sessionId)
        val allTasks = (newTasks + newSurveys).distinctBy { it.id }

        for (task in allTasks) {
            taskDao.insertOrUpdateTask(task)
            // 未完了のタスクのみ通知を予約
            if (!task.done) {
                scheduleNotification(task)
            }
        }
        return allTasks
    }

    /**
     * 指定された年度・学期の時間割を取得する
     */
    suspend fun getTimetable(year: Int, term: String, forceRefresh: Boolean): List<ClassCell> {
        val sessionId = sessionManager.sessionIdFlow.firstOrNull() ?: throw Exception("Not logged in")
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

    /**
     * お知らせ一覧を取得する
     */
    suspend fun getNews(forceRefresh: Boolean): List<NewsItem> {
        val sessionId = sessionManager.sessionIdFlow.firstOrNull() ?: throw Exception("Not logged in")

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

    private fun scheduleNotification(task: Task) {
        // (前回実装したコード)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("TASK_ID", task.id)
            putExtra("TASK_TITLE", task.title)
            putExtra("TASK_URL", task.url)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = task.deadline - 3600 * 1000 // 1 hour before
        if (triggerAtMillis > System.currentTimeMillis()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }
}