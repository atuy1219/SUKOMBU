package com.atuy.scomb.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.atuy.scomb.MainActivity
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
        val context = LocalContext.current
        // ウィジェット全体の背景設定
        // Android 12以降はシステムが角丸を適用するが、それ以前のためにcornerRadiusを設定
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .appWidgetBackground()
                .cornerRadius(16.dp)
        ) {
            WidgetHeader()

            when {
                loadingState == "loading" -> {
                    Box(
                        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.widget_loading),
                            style = TextStyle(color = GlanceTheme.colors.onSurface)
                        )
                    }
                }

                loadingState.startsWith("error") -> {
                    Box(
                        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.widget_error_message),
                            style = TextStyle(
                                color = GlanceTheme.colors.error,
                                fontSize = 12.sp,
                                textAlign = androidx.glance.text.TextAlign.Center
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
                            text = context.getString(R.string.widget_no_tasks),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
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
        val context = LocalContext.current
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.widget_header_title),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
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
                    contentDescription = context.getString(R.string.widget_update_desc),
                    modifier = GlanceModifier.width(20.dp).height(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }

    @Composable
    private fun TaskList(tasks: List<Task>) {
        LazyColumn(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp)
        ) {
            items(tasks, { task: Task -> task.id.hashCode().toLong() }) { task ->
                TaskWidgetItem(task)
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun TaskWidgetItem(task: Task) {
        // タスクの種類に応じた色設定
        val accentColor = when (task.taskType) {
            0 -> GlanceTheme.colors.primary      // 課題
            1 -> GlanceTheme.colors.error        // テスト
            2 -> GlanceTheme.colors.secondary    // アンケート
            else -> GlanceTheme.colors.tertiary  // その他
        }

        val remaining = DateUtils.formatRemainingTime(task.deadline)
        val isOverdue = task.deadline < System.currentTimeMillis()
        val deadlineColor = if (isOverdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant) // カード背景色
                .cornerRadius(12.dp)
                .clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(ActionParameters.Key<String>("destination") to "tasks")
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左側のカラーバー
            Box(
                modifier = GlanceModifier
                    .width(6.dp)
                    .height(64.dp) // カードの高さに合わせるか固定
                    .background(accentColor)
            ) {}

            // コンテンツ部分
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                // タイトルと種類
                Text(
                    text = task.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(4.dp))

                // 科目名
                Text(
                    text = task.className,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(2.dp))

                // 締め切り情報
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateUtils.timeToString(task.deadline),
                        style = TextStyle(
                            color = deadlineColor,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = remaining,
                        style = TextStyle(
                            color = deadlineColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
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