package com.atuy.scomb.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
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
import androidx.glance.unit.ColorProvider
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
                            val listType = Types.newParameterizedType(List::class.java, Task::class.java)
                            val adapter = moshi.adapter<List<Task>>(listType)
                            adapter.fromJson(tasksJson) ?: emptyList()
                        } catch (_: Exception) {
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

        Scaffold(
            modifier = GlanceModifier.fillMaxSize(),
            backgroundColor = GlanceTheme.colors.widgetBackground,
            horizontalPadding = 12.dp,
            titleBar = { WidgetTitleBar() }
        ) {
            when {
                loadingState == "loading" -> StatusContainer(
                    text = context.getString(R.string.widget_loading),
                    containerColor = GlanceTheme.colors.primaryContainer,
                    contentColor = GlanceTheme.colors.onPrimaryContainer
                )

                loadingState.startsWith("error") -> StatusContainer(
                    text = context.getString(R.string.widget_error_message),
                    containerColor = GlanceTheme.colors.errorContainer,
                    contentColor = GlanceTheme.colors.onErrorContainer
                )

                tasks.isEmpty() -> StatusContainer(
                    text = context.getString(R.string.widget_no_tasks),
                    containerColor = GlanceTheme.colors.secondaryContainer,
                    contentColor = GlanceTheme.colors.onSecondaryContainer
                )

                else -> TaskList(tasks)
            }
        }
    }

    @Composable
    private fun WidgetTitleBar() {
        val context = LocalContext.current

        TitleBar(
            startIcon = ImageProvider(R.drawable.ic_launcher_monochrome),
            title = context.getString(R.string.widget_header_title),
            iconColor = GlanceTheme.colors.primary,
            textColor = GlanceTheme.colors.onSurface,
            actions = {
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = context.getString(R.string.widget_update_desc),
                    onClick = actionRunCallback<UpdateWidgetAction>(),
                    backgroundColor = GlanceTheme.colors.primaryContainer,
                    contentColor = GlanceTheme.colors.onPrimaryContainer
                )
            }
        )
    }

    @Composable
    private fun StatusContainer(
        text: String,
        containerColor: ColorProvider,
        contentColor: ColorProvider
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(containerColor)
                .cornerRadius(24.dp)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.glance.text.TextAlign.Center
                )
            )
        }
    }

    @Composable
    private fun TaskList(tasks: List<Task>) {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(tasks, { task: Task -> task.id.hashCode().toLong() }) { task ->
                TaskWidgetItem(task)
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun TaskWidgetItem(task: Task) {
        val accentColor = when (task.taskType) {
            0 -> GlanceTheme.colors.primary
            1 -> GlanceTheme.colors.error
            2 -> GlanceTheme.colors.secondary
            else -> GlanceTheme.colors.tertiary
        }

        val remaining = DateUtils.formatRemainingTime(task.deadline)
        val isOverdue = task.deadline < System.currentTimeMillis()
        val containerColor = if (isOverdue) {
            GlanceTheme.colors.errorContainer
        } else {
            GlanceTheme.colors.secondaryContainer
        }
        val contentColor = if (isOverdue) {
            GlanceTheme.colors.onErrorContainer
        } else {
            GlanceTheme.colors.onSecondaryContainer
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(containerColor)
                .cornerRadius(20.dp)
                .clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(ActionParameters.Key<String>("destination") to "tasks")
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .width(6.dp)
                    .height(76.dp)
                    .background(accentColor)
                    .cornerRadius(3.dp)
            ) {}

            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = task.title,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = task.className,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateUtils.timeToString(task.deadline),
                        style = TextStyle(
                            color = contentColor,
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Box(
                        modifier = GlanceModifier
                            .background(
                                if (isOverdue) GlanceTheme.colors.error else GlanceTheme.colors.primary
                            )
                            .cornerRadius(20.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = remaining,
                            style = TextStyle(
                                color = if (isOverdue) {
                                    GlanceTheme.colors.onError
                                } else {
                                    GlanceTheme.colors.onPrimary
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
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
