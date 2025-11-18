package com.atuy.scomb.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class TaskWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TaskWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val workRequest = OneTimeWorkRequestBuilder<TaskWidgetWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}