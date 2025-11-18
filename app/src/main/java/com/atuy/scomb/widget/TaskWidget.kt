package com.atuy.scomb.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.atuy.scomb.R
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.util.DateUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class TaskWidget : GlanceAppWidget() {

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val tasksJson = prefs[TaskWidgetWorker.tasksStateKey]
                val loadingState = prefs[TaskWidgetWorker.loadingStateKey] ?: "loading"

                val tasks = remember(tasksJson) {
                    if (tasksJson.isNullOrBlank()) {
                        emptyList()
                    } else {
                        try {
                            val listType =
                                Types.newParameterizedType(List::class.java, Task::class.java)
                            val adapter = moshi.adapter<List<Task>>(listType)
                            adapter.fromJson(tasksJson) ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
                WidgetContent(loadingState, tasks)
            }
        }
    }


    @Composable
    private fun WidgetContent(loadingState: String, tasks: List<Task>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
        ) {
            WidgetHeader()

            when {
                loadingState == "loading" -> {
                    Box(
                        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "読み込み中...",
                            style = TextStyle(color = GlanceTheme.colors.onBackground)
                        )
                    }
                }

                loadingState.startsWith("error") -> {
                    Box(
                        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "エラーが発生しました。\nアプリを開いてログインしてください。",
                            style = TextStyle(
                                color = GlanceTheme.colors.error,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                // データなし
                tasks.isEmpty() -> {
                    Box(
                        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "直近の課題はありません",
                            style = TextStyle(color = GlanceTheme.colors.onBackground)
                        )
                    }
                }

                else -> {
                    TaskList(tasks)
                }
            }
        }
    }

    @Composable
    private fun WidgetHeader() {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(GlanceTheme.colors.background),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "直近の課題",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 18.sp
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                modifier = GlanceModifier
                    .clickable(onClick = actionRunCallback<UpdateWidgetAction>())
                    .padding(8.dp)
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "更新",
                    modifier = GlanceModifier.width(24.dp).height(24.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onBackground)
                )
            }
        }
    }

    @Composable
    private fun TaskList(tasks: List<Task>) {
        LazyColumn(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            items(tasks, { task: Task -> task.id.hashCode().toLong() }) { task ->
                TaskWidgetItem(task)
            }
        }
    }

    @Composable
    private fun TaskWidgetItem(task: Task) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
            // TODO: ここに actionStartActivity を追加して、クリックでアプリの課題詳細を開く
        ) {
            Text(
                text = task.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 14.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = task.className,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))

            val remaining = DateUtils.formatRemainingTime(task.deadline)
            val isOverdue = task.deadline < System.currentTimeMillis()

            Text(
                text = "${DateUtils.timeToString(task.deadline)} ($remaining)",
                style = TextStyle(
                    color = if (isOverdue)
                        ColorProvider(Color(0xFFB00020))
                    else
                        GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }
}


class UpdateWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val workRequest = OneTimeWorkRequestBuilder<TaskWidgetWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}