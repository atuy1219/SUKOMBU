package com.atuy.scomb.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.atuy.scomb.data.db.Task
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) {
    companion object {
        val tasksStateKey = stringPreferencesKey("widget_tasks_json")
        val loadingStateKey = stringPreferencesKey("widget_loading_state")
    }

    private val adapter = moshi.adapter<List<Task>>(
        Types.newParameterizedType(List::class.java, Task::class.java)
    )

    suspend fun update(tasks: List<Task>) {
        val now = System.currentTimeMillis()
        val json = adapter.toJson(tasks.filter { !it.done && it.deadline > now }
            .sortedBy { it.deadline }.take(5))
        val ids = GlanceAppWidgetManager(context).getGlanceIds(TaskWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[tasksStateKey] = json
                prefs[loadingStateKey] = "success"
            }
            TaskWidget().update(context, id)
        }
    }
}
