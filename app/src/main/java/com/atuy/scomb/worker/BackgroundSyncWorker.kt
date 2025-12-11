package com.atuy.scomb.worker

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.domain.ScheduleNotificationsUseCase
import com.atuy.scomb.widget.TaskWidget
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BackgroundSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ScombzRepository,
    private val scheduleNotificationsUseCase: ScheduleNotificationsUseCase,
    private val moshi: Moshi
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BackgroundSyncWorker"
        // TaskWidgetWorkerと同じキーを使用
        val tasksStateKey = stringPreferencesKey("widget_tasks_json")
        val loadingStateKey = stringPreferencesKey("widget_loading_state")
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background sync...")

        return try {
            // 1. 課題一覧の更新 (APIから取得してDB保存)
            // バックグラウンドなので強制リフレッシュ
            val tasks = repository.getTasksAndSurveys(forceRefresh = true)
            Log.d(TAG, "Fetched ${tasks.size} tasks.")

            // 2. 通知の再スケジュール
            scheduleNotificationsUseCase(tasks)
            Log.d(TAG, "Notifications scheduled.")

            // 3. ウィジェットの更新
            updateWidgets(tasks)
            Log.d(TAG, "Widgets updated.")

            // バックグラウンド更新では「フォアグラウンド更新用の時刻(lastSyncTime)」は更新しない
            // (時間割やお知らせは更新していないため)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed", e)
            // エラー時でもウィジェットのエラー表示更新を試みる
            updateWidgetsError(e.message ?: "Unknown Error")
            Result.retry()
        }
    }

    private suspend fun updateWidgets(tasks: List<Task>) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(TaskWidget::class.java)

        if (glanceIds.isEmpty()) return

        val upcomingTasks = tasks
            .filter { !it.done && it.deadline > System.currentTimeMillis() }
            .sortedBy { it.deadline }
            .take(5)

        val listType = Types.newParameterizedType(List::class.java, Task::class.java)
        val adapter = moshi.adapter<List<Task>>(listType)
        val tasksJson = adapter.toJson(upcomingTasks)

        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[tasksStateKey] = tasksJson
                prefs[loadingStateKey] = "success"
            }
            TaskWidget().update(context, glanceId)
        }
    }

    private suspend fun updateWidgetsError(errorMessage: String) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(TaskWidget::class.java)

        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[loadingStateKey] = "error: ${errorMessage.take(100)}"
            }
            TaskWidget().update(context, glanceId)
        }
    }
}