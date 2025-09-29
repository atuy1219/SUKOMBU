package com.atuy.scomb.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.atuy.scomb.data.db.Task
import com.atuy.scomb.receiver.NotificationReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ScheduleNotificationsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    operator fun invoke(tasks: List<Task>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // 権限がない場合、ユーザーに設定を促す
                Toast.makeText(context, "通知には「アラーム＆リマインダー」の権限が必要です", Toast.LENGTH_LONG).show()
                Intent().also { intent ->
                    intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                return // 権限がないので、この先の処理は中断
            }
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