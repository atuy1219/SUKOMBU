package com.atuy.scomb.data.manager

import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.util.DateUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoRefreshManager @Inject constructor(
    private val settingsManager: SettingsManager,
    private val repository: ScombzRepository,
    private val authManager: AuthManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val events = MutableSharedFlow<Unit>()
    val refreshEvent = events.asSharedFlow()

    fun checkAndTriggerRefresh() {
        scope.launch {
            refreshMutex.withLock {
                if (authManager.authTokenFlow.first() == null) return@withLock
                val lastSync = settingsManager.lastSyncTimeFlow.first()
                val interval = settingsManager.autoRefreshIntervalFlow.first().coerceAtLeast(1) * 60_000L
                val now = System.currentTimeMillis()
                if (now >= lastSync && now - lastSync < interval) return@withLock
                try {
                    val term = DateUtils.getCurrentScombTerm()
                    repository.getTasksAndSurveys(true)
                    repository.getTimetable(term.year, term.term, true)
                    repository.getNews(true)
                    settingsManager.updateLastSyncTime(System.currentTimeMillis())
                    events.emit(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("AutoRefreshManager", "Refresh failed", e)
                }
            }
        }
    }
}
