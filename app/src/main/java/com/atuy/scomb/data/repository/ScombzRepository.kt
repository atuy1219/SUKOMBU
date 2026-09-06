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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import javax.inject.Singleton
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

@Singleton
class ScombzRepository @Inject constructor(
    private val cache: ScombCache,
    private val notifications: com.atuy.scomb.domain.ScheduleNotificationsUseCase,
    private val widgets: com.atuy.scomb.widget.TaskWidgetUpdater,
    private val taskDao: TaskDao,
    private val classCellDao: ClassCellDao,
    private val newsItemDao: NewsItemDao,
    private val apiService: ScombzApiService,
    private val authManager: AuthManager,
    @ApplicationContext private val context: Context
) {
    private val sessionMutex = Mutex()
    private var tasksLoaded = false
    private var newsLoaded = false
    private val loadedTimetables = mutableSetOf<String>()

    private suspend fun <T> executeWithAuthHandling(block: suspend () -> T): T = sessionMutex.withLock {
        try {
            ensureAuthenticated()
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("ScombzRepository", "Error executing repository operation", e)
            when (e) {
                is SessionExpiredException -> {
                    Log.d("ScombzRepository", "Session expired. Clearing auth token.")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        authManager.invalidateAuthToken()
                        updateTaskSurfaces(emptyList())
                    }
                    throw SessionExpiredException(context.getString(R.string.error_session_expired))
                }
                is IOException -> throw Exception(context.getString(R.string.error_network), e)
                is ServerException -> throw Exception(context.getString(R.string.error_server, e.code), e)
                is ClientException -> throw e
                else -> throw e
            }
        }
    }

    private fun <T> validateResponse(response: Response<T>): T? {
        if (response.isSuccessful) return response.body()
            ?: throw IOException("Empty API response")

        val code = response.code()
        response.errorBody()?.close()
        Log.e("ScombzRepository", "API Error: $code")
        when (code) {
            401 -> throw SessionExpiredException()
            in 400..499 -> throw ClientException(code, "Client Error: $code")
            in 500..599 -> throw ServerException(code, "Server Error: $code")
            else -> throw Exception("Unexpected error: $code")
        }
    }

    suspend fun login(userId: String, userPw: String): Result<Unit> = sessionMutex.withLock {
        try {
            val response = apiService.login(LoginRequest(userId, userPw))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.status == "OK" && !body.token.isNullOrBlank()) {
                    if (authManager.usernameFlow.first() != userId) {
                        cache.clear()
                        resetCacheState()
                        updateTaskSurfaces(emptyList())
                    }
                    authManager.saveSession(body.token, userId)
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
            if (e is CancellationException) throw e
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
                if (tasksLoaded || cachedTasks.isNotEmpty()) return@executeWithAuthHandling cachedTasks
            }

            ensureAuthenticated()
            val yearMonth = DateUtils.getCurrentScombTerm().yearApiTerm
            val apiTasks = validateResponse(apiService.getTasks(yearMonth)) ?: emptyList()
            val dbTasks = apiTasks.map { apiTask ->
                apiTask.toDbTask()?.takeIf { it.deadline > 0 }
                    ?: throw IllegalStateException("Invalid task response; cached tasks retained")
            }

            val tasks = cache.replaceTasks(dbTasks)
            tasksLoaded = true
            updateTaskSurfaces(tasks)
            tasks
        }
    }

    suspend fun getTimetable(year: Int, term: String, forceRefresh: Boolean): List<ClassCell> {
        return executeWithAuthHandling {
            val timetableTitle = "$year-$term"
            if (!forceRefresh) {
                val cachedTimetable = classCellDao.getCells(timetableTitle)
                if (timetableTitle in loadedTimetables || cachedTimetable.isNotEmpty()) return@executeWithAuthHandling cachedTimetable
            }

            val yearMonth = if (term == "1") "${year}01" else "${year}02"
            val apiClassCells = validateResponse(apiService.getTimetable(yearMonth)) ?: emptyList()
            val dbClassCells = apiClassCells.map {
                it.toDbClassCell(
                    year,
                    term,
                    timetableTitle,
                    existingUserNote = null,
                    existingCustomLinks = null
                )
            }

            cache.replaceTimetable(timetableTitle, dbClassCells).also {
                loadedTimetables.add(timetableTitle)
            }
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

            classCellDao.updateDetails(classCell.classId, classCell.timetableTitle, note, customColorInt)
        }
    }

    suspend fun getNews(forceRefresh: Boolean): List<NewsItem> {
        return executeWithAuthHandling {
            if (!forceRefresh) {
                val cachedNews = newsItemDao.getAllNews()
                if (newsLoaded || cachedNews.isNotEmpty()) return@executeWithAuthHandling cachedNews
            }

            ensureAuthenticated()
            val currentTerm = DateUtils.getCurrentScombTerm()
            val apiNews = validateResponse(apiService.getNews()) ?: emptyList()
            cache.replaceNews(apiNews.map { it.toDbNewsItem(currentTerm.yearApiTerm) }).also {
                newsLoaded = true
            }
        }
    }

    suspend fun markAsRead(newsItem: NewsItem) {
        setNewsUnread(listOf(newsItem), unread = false)
    }

    suspend fun setNewsUnread(newsItems: Collection<NewsItem>, unread: Boolean) {
        if (newsItems.isEmpty()) return
        executeWithAuthHandling {
            newsItemDao.setUnread(newsItems.map { it.newsId }, unread)
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

    suspend fun rescheduleNotifications() = executeWithAuthHandling {
        notifications(taskDao.getAllTasks())
    }

    suspend fun logout() = sessionMutex.withLock {
        clearSession()
    }

    private fun resetCacheState() {
        tasksLoaded = false
        newsLoaded = false
        loadedTimetables.clear()
    }

    private suspend fun clearSession() = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        authManager.clearAuthToken()
        cache.clear()
        resetCacheState()
        updateTaskSurfaces(emptyList())
    }

    private suspend fun updateTaskSurfaces(tasks: List<Task>) {
        try {
            notifications(tasks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ScombzRepository", "Could not update reminders", e)
        }
        try {
            widgets.update(tasks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ScombzRepository", "Could not update widgets", e)
        }
    }

    suspend fun updateCustomLinks(cell: ClassCell, json: String) = executeWithAuthHandling {
        classCellDao.updateLinks(cell.classId, cell.timetableTitle, json)
    }

    suspend fun registerFcmToken(token: String) {
        executeWithAuthHandling {
            ensureAuthenticated()
            val result = validateResponse(
                apiService.registerFcm(mapOf("fcm_token" to token))
            )
            if (result?.status != "OK") {
                throw IOException("Failed to register FCM token")
            } else {
                Log.d("ScombzRepository", "FCM token registered successfully")
            }
        }
    }
}
