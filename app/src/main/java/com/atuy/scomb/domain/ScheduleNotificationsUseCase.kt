package com.atuy.scomb.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.receiver.NotificationReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ScheduleNotificationsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(tasks: List<Task>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        tasks.forEach { task ->
            // 完了済みのタスクは通知しない
            if (task.done) return@forEach

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("TASK_ID", task.id)
                putExtra("TASK_TITLE", task.title)
                putExtra("TASK_URL", task.url)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 締め切り1時間前に通知をセット
            val triggerAtMillis = task.deadline - 3600 * 1000
            if (triggerAtMillis > System.currentTimeMillis()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }
}