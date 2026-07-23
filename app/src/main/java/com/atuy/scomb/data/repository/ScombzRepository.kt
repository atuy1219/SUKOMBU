package com.atuy.scomb.data.repository

import android.content.Context
import android.util.Log
import com.atuy.scomb.R
import com.atuy.scomb.data.db.ClassCell
import com.atuy.scomb.data.db.ClassCellDao
import com.atuy.scomb.data.db.NewsItem
import com.atuy.scomb.data.db.NewsItemDao
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.db.TaskDao
import com.atuy.scomb.data.manager.AuthManager
import com.atuy.scomb.data.network.ApiUpdateClassRequest
import com.atuy.scomb.data.network.LoginRequest
import com.atuy.scomb.data.network.ScombzApiService
import com.atuy.scomb.util.ClientException
import com.atuy.scomb.util.DateUtils
import com.atuy.scomb.util.ServerException
import com.atuy.scomb.util.SessionExpiredException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

class ScombzRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val classCellDao: ClassCellDao,
    private val newsItemDao: NewsItemDao,
    private val apiService: ScombzApiService,
    private val authManager: AuthManager,
    @ApplicationContext private val context: Context
) {
    private suspend fun <T> executeWithAuthHandling(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            Log.e("ScombzRepository", "Error executing repository operation", e)
            when (e) {
                is SessionExpiredException -> {
                    Log.d("ScombzRepository", "Session expired. Clearing auth token.")
                    authManager.clearAuthToken()
                    throw Exception(context.getString(R.string.error_session_expired), e)
                }
                is IOException -> throw Exception(context.getString(R.string.error_network), e)
                is ServerException -> throw Exception(context.getString(R.string.error_server, e.code), e)
                is ClientException -> throw Exception(context.getString(R.string.error_client, e.code), e)
                else -> throw e
            }
        }
    }

    private fun <T> validateResponse(response: Response<T>): T? {
        if (response.isSuccessful) return response.body()

        val code = response.code()
        val errorBody = response.errorBody()?.string()
        Log.e("ScombzRepository", "API Error: $code - $errorBody")
        when (code) {
            401 -> throw SessionExpiredException()
            in 400..499 -> throw ClientException(code, "Client Error: $code")
            in 500..599 -> throw ServerException(code, "Server Error: $code")
            else -> throw Exception("Unexpected error: $code")
        }
    }

    suspend fun login(userId: String, userPw: String): Result<Unit> {
        return try {
            val response = apiService.login(LoginRequest(userId, userPw))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.status == "OK" && body.token != null) {
                    authManager.saveAuthToken(body.token)
                    authManager.saveUsername(userId)
                    Result.success(Unit)
                } else {
                    val statusMsg = body?.status ?: "Unknown status"
                    Result.failure(Exception(context.getString(R.string.error_login_failed, statusMsg)))
                }
            } else {
                Result.failure(
                    Exception(
                        context.getString(
                            R.string.error_login_failed,
                            "Code: ${response.code()}"
                        )
                    )
                )
            }
        } catch (e: IOException) {
            Result.failure(Exception(context.getString(R.string.error_network), e))
        } catch (e: Exception) {
            Result.failure(Exception(context.getString(R.string.error_unknown, e.message), e))
        }
    }

    private suspend fun ensureAuthenticated() {
        if (authManager.authTokenFlow.first() == null) throw SessionExpiredException()
    }

    private suspend fun getOtkey(): Result<String> {
        val response = apiService.getOtkey()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.status == "OK" && body.otkey != null) {
                return Result.success(body.otkey)
            }
        }
        if (response.code() == 401) throw SessionExpiredException()
        return Result.failure(Exception("Failed to get otkey: ${response.code()}"))
    }

    suspend fun getTasksAndSurveys(forceRefresh: Boolean): List<Task> {
        return executeWithAuthHandling {
            if (!forceRefresh) {
                val cachedTasks = taskDao.getAllTasks()
                if (cachedTasks.isNotEmpty()) return@executeWithAuthHandling cachedTasks
            }

            ensureAuthenticated()
            val yearMonth = DateUtils.getCurrentScombTerm().yearApiTerm
            val apiTasks = validateResponse(apiService.getTasks(yearMonth)) ?: emptyList()
            val dbTasks = apiTasks.mapNotNull { it.toDbTask() }

            taskDao.clearApiTasks()
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

            val existingCells = classCellDao.getCells(timetableTitle)
            val customLinksMap = existingCells.associate { it.classId to it.customLinksJson }
            ensureAuthenticated()

            val yearMonth = if (term == "1") "${year}01" else "${year}02"
            val apiClassCells = validateResponse(apiService.getTimetable(yearMonth)) ?: emptyList()
            val dbClassCells = apiClassCells.map {
                it.toDbClassCell(
                    year,
                    term,
                    timetableTitle,
                    existingUserNote = null,
                    existingCustomLinks = customLinksMap[it.id]
                )
            }

            classCellDao.removeTimetable(timetableTitle)
            dbClassCells.forEach { classCellDao.insertClassCell(it) }
            dbClassCells
        }
    }

    suspend fun updateClassInfo(classCell: ClassCell, note: String?, customColorInt: Int?) {
        executeWithAuthHandling {
            ensureAuthenticated()
            val yearMonth = if (classCell.term == "1") {
                "${classCell.year}01"
            } else {
                "${classCell.year}02"
            }
            val colorString = if (customColorInt == null || customColorInt == 0) {
                null
            } else {
                Integer.toUnsignedString(customColorInt)
            }
            val request = ApiUpdateClassRequest(
                classId = classCell.classId,
                note = note,
                customColor = colorString,
                customizedNumberOfCredit = 0
            )
            val result = validateResponse(apiService.updateClass(yearMonth, listOf(request)))
            if (result?.status != "OK") {
                throw Exception("Failed to update class info: Status not OK")
            }

            classCellDao.insertClassCell(
                classCell.copy(note = note, customColorInt = customColorInt)
            )
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
            val apiNews = validateResponse(apiService.getNews()) ?: emptyList()
            val existingNewsMap = newsItemDao.getAllNews().associate { it.newsId to it.unread }
            val dbNews = apiNews.map { apiItem ->
                val newItem = apiItem.toDbNewsItem(currentTerm.yearApiTerm)
                if (existingNewsMap[newItem.newsId] == false) {
                    newItem.copy(unread = false)
                } else {
                    newItem
                }
            }

            newsItemDao.clearAll()
            dbNews.forEach { newsItemDao.insertOrUpdateNewsItem(it) }
            newsItemDao.getAllNews()
        }
    }

    suspend fun markAsRead(newsItem: NewsItem) {
        setNewsUnread(listOf(newsItem), unread = false)
    }

    suspend fun setNewsUnread(newsItems: Collection<NewsItem>, unread: Boolean) {
        if (newsItems.isEmpty()) return
        executeWithAuthHandling {
            newsItems.forEach { item ->
                newsItemDao.insertOrUpdateNewsItem(item.copy(unread = unread))
            }
        }
    }

    suspend fun getTaskUrl(task: Task): String {
        return executeWithAuthHandling {
            ensureAuthenticated()
            val otkeyResult = getOtkey()
            val otkey = otkeyResult.getOrNull() ?: run {
                if (otkeyResult.exceptionOrNull() is SessionExpiredException) {
                    throw SessionExpiredException()
                }
                throw Exception(context.getString(R.string.error_otkey_failed))
            }

            when (task.taskType) {
                0 -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course/report/submission?idnumber=${task.classId}&reportId=${task.reportId}"
                1 -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course/examination/taketop?idnumber=${task.classId}&examinationId=${task.reportId}"
                2 -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course/surveys/take?idnumber=${task.classId}&surveyId=${task.reportId}"
                else -> "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course?idnumber=${task.classId}"
            }
        }
    }

    suspend fun getClassUrl(classId: String): String {
        return executeWithAuthHandling {
            ensureAuthenticated()
            val otkeyResult = getOtkey()
            val otkey = otkeyResult.getOrNull() ?: run {
                if (otkeyResult.exceptionOrNull() is SessionExpiredException) {
                    throw SessionExpiredException()
                }
                throw Exception(context.getString(R.string.error_otkey_failed))
            }
            "https://mobile.scombz.shibaura-it.ac.jp/$otkey/lms/course?idnumber=$classId"
        }
    }

    suspend fun registerFcmToken(token: String) {
        executeWithAuthHandling {
            ensureAuthenticated()
            val result = validateResponse(
                apiService.registerFcm(mapOf("fcm_token" to token))
            )
            if (result?.status != "OK") {
                Log.e("ScombzRepository", "Failed to register FCM token: Status not OK")
            } else {
                Log.d("ScombzRepository", "FCM token registered successfully")
            }
        }
    }
}
