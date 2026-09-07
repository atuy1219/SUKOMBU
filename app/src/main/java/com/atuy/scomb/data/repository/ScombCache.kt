package com.atuy.scomb.data.repository

import androidx.room.withTransaction
import com.atuy.scomb.data.db.AppDatabase
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.Task
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScombCache @Inject constructor(private val database: AppDatabase) {
    suspend fun replaceTasks(tasks: List<Task>): List<Task> = database.withTransaction {
        database.taskDao().clearApiTasks()
        database.taskDao().insertTasks(tasks)
        database.taskDao().getAllTasks()
    }

    suspend fun replaceTimetable(title: String, cells: List<ClassCell>): List<ClassCell> =
        database.withTransaction {
            val dao = database.classCellDao()
            val existing = dao.getCells(title).filter { !it.isUserClassCell }.associateBy { it.classId }
            val merged = cells.map { cell ->
                val local = existing[cell.classId]
                cell.copy(userNote = local?.userNote, customLinksJson = local?.customLinksJson)
            }
            dao.removeApiTimetable(title)
            dao.insertCells(merged)
            dao.getCells(title)
        }

    suspend fun replaceNews(news: List<NewsItem>): List<NewsItem> = database.withTransaction {
        val dao = database.newsItemDao()
        val existing = dao.getAllNews().associateBy { it.newsId }
        val merged = news.map { item ->
            existing[item.newsId]?.let { item.copy(unread = it.unread) } ?: item
        }
        dao.clearAll()
        dao.insertNews(merged)
        dao.getAllNews()
    }

    suspend fun clear() = database.withTransaction {
        database.taskDao().clearAll()
        database.classCellDao().clearAll()
        database.newsItemDao().clearAll()
    }
}
