package com.atuy.scomb.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.atuy.scomb.R
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.data.manager.SettingsManager
import com.atuy.scomb.receiver.NotificationReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ScheduleNotificationsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "ScheduleNotifications"
    }

    suspend operator fun invoke(tasks: List<Task>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "canScheduleExactAlarms is false. Requesting permission.")
            Toast.makeText(
                context,
                "通知には「アラームとリマインダー」の権限が必要です",
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

        val notificationTimingsMinutes =
            settingsManager.notificationTimingsFlow.first().mapNotNull { it.toIntOrNull() }
        Log.d(
            TAG,
            "Scheduling notifications for ${tasks.size} tasks with timings: $notificationTimingsMinutes"
        )


        tasks.forEach { task ->
            if (task.done) return@forEach

            notificationTimingsMinutes.forEach { minutes ->
                val triggerAtMillis = task.deadline - (minutes * 60 * 1000L)

                if (triggerAtMillis > System.currentTimeMillis()) {
                    scheduleAlarm(
                        task.id,
                        task.title,
                        task.url,
                        task.deadline,
                        triggerAtMillis,
                        minutes
                    )
                }
            }
        }
    }

    private fun cancelAllScheduledNotifications(tasks: List<Task>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 既存のプリセット値に対してキャンセルを試みる
        val defaultTimings = setOf(10, 30, 60, 120, 1440, 2880, 4320)

        tasks.forEach { task ->
            defaultTimings.forEach { minutes ->
                cancelAlarm(task.id, minutes, alarmManager)
            }
        }
    }

    private fun cancelAlarm(taskId: String, minutes: Int, alarmManager: AlarmManager) {
        val requestCode = (taskId + minutes.toString()).hashCode()
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled old notification for task $taskId (ID: $requestCode)")
        }
    }

    fun scheduleTestNotification() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Test notification: canScheduleExactAlarms is false.")
            Toast.makeText(
                context,
                "通知には「アラームとリマインダー」の権限が必要です",
                Toast.LENGTH_LONG
            ).show()
            Intent().also { intent ->
                intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            return
        }

        val triggerAtMillis = System.currentTimeMillis() + 60 * 1000L // 1分後
        val testDeadline = System.currentTimeMillis() + 60 * 60 * 1000L

        scheduleAlarm(
            "test_notification_id",
            "これはテスト通知です",
            "https://scombz.shibaura-it.ac.jp/",
            testDeadline,
            triggerAtMillis,
            1 // 1分前通知として扱う
        )

        Toast.makeText(
            context,
            context.getString(R.string.settings_test_notification_scheduled),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun scheduleAlarm(
        taskId: String,
        title: String,
        url: String,
        deadline: Long,
        triggerAtMillis: Long,
        minutes: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("TASK_ID", taskId)
            putExtra("TASK_TITLE", title)
            putExtra("TASK_URL", url)
            putExtra("TASK_DEADLINE", deadline)
            putExtra("SCHEDULED_TIME", triggerAtMillis)
            putExtra("NOTIFICATION_MINUTES_BEFORE", minutes) // 何分前通知かを追加
        }

        val requestCode =
            if (taskId == "test_notification_id") "test_notification".hashCode() else (taskId + minutes.toString()).hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            Log.d(TAG, "Scheduled notification for '$title' at $triggerAtMillis (ID: $requestCode)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule alarm: Permission denied", e)
        }
    }
}