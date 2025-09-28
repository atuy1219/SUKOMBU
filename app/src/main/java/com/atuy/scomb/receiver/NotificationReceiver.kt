package com.atuy.scomb.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atuy.scomb.R

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("TASK_ID") ?: return
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "課題"
        val taskUrl = intent.getStringExtra("TASK_URL") ?: ""

        val channelId = "SCOMB_MOBILE_TASK_NOTIFICATION"
        val notificationId = taskId.hashCode()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 適切なアイコンへ変更
            .setContentTitle("締め切りが近い課題")
            .setContentText(taskTitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
}
