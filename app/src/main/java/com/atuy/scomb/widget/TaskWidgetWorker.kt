package com.atuy.scomb.widget

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
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TaskWidgetWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ScombzRepository,
    private val moshi: Moshi
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "TaskWidgetWorker"
        val tasksStateKey = stringPreferencesKey("widget_tasks_json")
        val loadingStateKey = stringPreferencesKey("widget_loading_state")
    }

    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(TaskWidget::class.java)

        if (glanceIds.isEmpty()) {
            Log.d(TAG, "No widgets to update.")
            return Result.success()
        }

        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[loadingStateKey] = "loading"
            }
            TaskWidget().update(context, glanceId)
        }

        return try {
            Log.d(TAG, "Fetching tasks for widget...")
            val tasks = repository.getTasksAndSurveys(forceRefresh = true)
            Log.d(TAG, "Fetched ${tasks.size} tasks.")

            val upcomingTasks = tasks
                .filter { !it.done && it.deadline > System.currentTimeMillis() }
                .sortedBy { it.deadline }
                .take(5)

            val listType = Types.newParameterizedType(List::class.java, Task::class.java)
            val adapter = moshi.adapter<List<Task>>(listType)
            val tasksJson = adapter.toJson(upcomingTasks)

            Log.d(TAG, "Updating ${glanceIds.size} widgets with new data.")
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[tasksStateKey] = tasksJson
                    prefs[loadingStateKey] = "success"
                }
                TaskWidget().update(context, glanceId)
            }

            Log.d(TAG, "Widget update complete.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch tasks for widget", e)
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[loadingStateKey] = "error: ${e.message?.take(100)}" // メッセージが長すぎないように
                }
                TaskWidget().update(context, glanceId)
            }
            Result.failure()
        }
    }
}