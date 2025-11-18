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
import com.atuy.scomb.util.DateUtils
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
    private suspend fun <T> executeWithAuthHandling(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            if (e is SessionExpiredException) {
                Log.d("ScombzRepository", "Session expired. Clearing auth token.")
                authManager.clearAuthToken()
            }
            throw e
        }
    }

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

    private suspend fun getOtkey(): Result<String> {
        ensureAuthenticated()
        return try {
            val response = apiService.getOtkey()
            if (response.isSuccessful && response.body() != null && response.body()!!.status == "OK" && response.body()!!.otkey != null) {
                Result.success(response.body()!!.otkey!!)
            } else {
                if (response.code() == 401) throw SessionExpiredException()
                Result.failure(Exception("otkeyの取得に失敗しました: ${response.message()} (Code: ${response.code()})"))
            }
        } catch (e: Exception) {
            // getOtkey内でキャッチしたSessionExpiredExceptionも上位に投げる前に処理が必要だが、
            // ここでは Result.failure に包んでいるため、呼び出し元で executeWithAuthHandling が機能するように例外を再スローするか、
            // 呼び出し元で Result をチェックして例外を投げる必要がある。
            // 今回は executeWithAuthHandling で一括管理するため、そのまま throw する形に修正。
            if (e is SessionExpiredException) throw e
            Result.failure(e)
        }
    }

    suspend fun getTasksAndSurveys(forceRefresh: Boolean): List<Task> {
        return executeWithAuthHandling {
            if (!forceRefresh) {
                val cachedTasks = taskDao.getAllTasks()
                if (cachedTasks.isNotEmpty()) return@executeWithAuthHandling cachedTasks
            }

            ensureAuthenticated()
            val otkeyResult = getOtkey()
            if (otkeyResult.isFailure) {
                throw otkeyResult.exceptionOrNull() ?: Exception("otkeyの取得に失敗しました")
            }
            val otkey = otkeyResult.getOrNull() ?: throw Exception("otkeyがnullです")
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
            val dbTasks = apiTasks.mapNotNull { it.toDbTask(otkey) }

            dbTasks.forEach { taskDao.insertOrUpdateTask(it) }
            dbTasks
        }
    }

    suspend fun getTimetable(year: Int, term: String, forceRefresh: Boolean): List<ClassCell> {
        return executeWithAuthHandling {
            val timetableTitle = "$year-$term"
            if (!forceRefresh) {
                val cachedTimetable = classCellDao.getCells(timetableTitle)
                if (cachedTimetable.isNotEmpty()) return@executeWithAuthHandling cachedTimetable
            }

            ensureAuthenticated()
            val otkeyResult = getOtkey()
            if (otkeyResult.isFailure) {
                throw otkeyResult.exceptionOrNull() ?: Exception("otkeyの取得に失敗しました")
            }
            val otkey = otkeyResult.getOrNull() ?: throw Exception("otkeyがnullです")
            val yearMonth = if (term == "1") "${year}01" else "${year}02"

            val response = apiService.getTimetable(yearMonth)
            if (!response.isSuccessful) {
                if (response.code() == 401) throw SessionExpiredException()
                val errorBody = response.errorBody()?.string()
                Log.e("ScombzRepository", "Timetable fetch failed: ${response.code()} - $errorBody")
                throw Exception("時間割の取得に失敗しました: ${response.code()}")
            }

            val apiClassCells = response.body() ?: emptyList()
            val dbClassCells = apiClassCells.map { it.toDbClassCell(year, term, timetableTitle, otkey) }

            classCellDao.removeTimetable(timetableTitle)
            dbClassCells.forEach { classCellDao.insertClassCell(it) }
            dbClassCells
        }
    }

    suspend fun getNews(forceRefresh: Boolean): List<NewsItem> {
        return executeWithAuthHandling {
            if (!forceRefresh) {
                val cachedNews = newsItemDao.getAllNews()
                if (cachedNews.isNotEmpty()) return@executeWithAuthHandling cachedNews
            }

            ensureAuthenticated()
            val currentTerm = DateUtils.getCurrentScombTerm()
            val otkeyResult = getOtkey()
            if (otkeyResult.isFailure) {
                throw otkeyResult.exceptionOrNull() ?: Exception("otkeyの取得に失敗しました")
            }
            val otkey = otkeyResult.getOrNull() ?: throw Exception("otkeyがnullです")
            val response = apiService.getNews()
            if (!response.isSuccessful) {
                if (response.code() == 401) throw SessionExpiredException()
                val errorBody = response.errorBody()?.string()
                Log.e("ScombzRepository", "News fetch failed: ${response.code()} - $errorBody")
                throw Exception("お知らせの取得に失敗しました: ${response.code()}")
            }

            val apiNews = response.body() ?: emptyList()
            val dbNews = apiNews.map { it.toDbNewsItem(otkey, currentTerm.yearApiTerm) }

            newsItemDao.clearAll()
            dbNews.forEach { newsItemDao.insertOrUpdateNewsItem(it) }
            newsItemDao.getAllNews()
        }
    }

    suspend fun markAsRead(newsItem: NewsItem) {
        newsItemDao.insertOrUpdateNewsItem(newsItem.copy(unread = false))
    }

    suspend fun getTaskUrl(task: Task): String {
        return executeWithAuthHandling {
            ensureAuthenticated()
            val otkeyResult = getOtkey()
            if (otkeyResult.isFailure) {
                throw otkeyResult.exceptionOrNull() ?: Exception("otkeyの取得に失敗しました")
            }
            val otkey = otkeyResult.getOrNull() ?: throw Exception("otkeyがnullです")

            when (task.taskType) {
                0 -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course/report/submission?idnumber=${task.classId}&reportId=${task.reportId}" // 課題
                1 -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course/examination/taketop?idnumber=${task.classId}&examinationId=${task.reportId}" // テスト
                2 -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course/surveys/take?idnumber=${task.classId}&surveyId=${task.reportId}" // アンケート
                else -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course?idnumber=${task.classId}"
            }
        }
    }
    suspend fun getClassUrl(classId: String): String {
        return executeWithAuthHandling {
            ensureAuthenticated()
            val otkeyResult = getOtkey()
            if (otkeyResult.isFailure) {
                throw otkeyResult.exceptionOrNull() ?: Exception("otkeyの取得に失敗しました")
            }
            val otkey = otkeyResult.getOrNull() ?: throw Exception("otkeyがnullです")

            "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course?idnumber=$classId"
        }
    }
}