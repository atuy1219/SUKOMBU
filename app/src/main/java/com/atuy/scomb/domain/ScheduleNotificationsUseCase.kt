package com.atuy.scomb.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.atuy.scomb.data.SettingsManager
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.receiver.NotificationReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ScheduleNotificationsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    suspend operator fun invoke(tasks: List<Task>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(
                context,
                "通知には「アラーム＆リマインダー」の権限が必要です",
                Toast.LENGTH_LONG
            ).show()
            Intent().also { intent ->
                intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            return
        }

        cancelAllScheduledNotifications(tasks)

        val notificationTimingsMinutes = settingsManager.notificationTimingsFlow.first().mapNotNull { it.toIntOrNull() }
        Log.d("ScheduleNotifications", "Scheduling notifications for ${tasks.size} tasks with timings: $notificationTimingsMinutes")


        tasks.forEach { task ->
            if (task.done) return@forEach

            notificationTimingsMinutes.forEach { minutes ->
                val triggerAtMillis = task.deadline - (minutes * 60 * 1000L)

                if (triggerAtMillis > System.currentTimeMillis()) {
                    val intent = Intent(context, NotificationReceiver::class.java).apply {
                        putExtra("TASK_ID", task.id)
                        putExtra("TASK_TITLE", task.title)
                        putExtra("TASK_URL", task.url)
                    }

                    val requestCode = (task.id + minutes.toString()).hashCode()

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d("ScheduleNotifications", "Scheduled notification for ${task.title} at $triggerAtMillis (ID: $requestCode)")
                }
            }
        }
    }

    private fun cancelAllScheduledNotifications(tasks: List<Task>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val allPossibleTimings = setOf(10, 30, 60, 120, 1440, 2880)

        tasks.forEach { task ->
            allPossibleTimings.forEach { minutes ->
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("TASK_ID", task.id)
                    putExtra("TASK_TITLE", task.title)
                    putExtra("TASK_URL", task.url)
                }
                val requestCode = (task.id + minutes.toString()).hashCode()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )

                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.d("ScheduleNotifications", "Cancelled old notification for ${task.title} (ID: $requestCode)")
                }
            }
        }
    }
}
