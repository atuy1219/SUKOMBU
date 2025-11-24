package com.atuy.scomb.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.atuy.scomb.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val currentTime = System.currentTimeMillis()
        val scheduledTime = intent.getLongExtra("SCHEDULED_TIME", 0L)
        val delay = if (scheduledTime > 0) currentTime - scheduledTime else 0

        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.getDefault())

        Log.d(TAG, "★★★ onReceive: 通知リクエストを受信しました ★★★")
        Log.d(TAG, "現在時刻: ${sdf.format(Date(currentTime))}")
        if (scheduledTime > 0) {
            Log.d(TAG, "予定時刻: ${sdf.format(Date(scheduledTime))}")
            Log.d(TAG, "遅延時間: ${delay}ms (${delay / 1000}秒)")
        } else {
            Log.d(TAG, "予定時刻: 情報なし")
        }

        val taskId = intent.getStringExtra("TASK_ID") ?: run {
            Log.e(TAG, "onReceive: TASK_IDがありません。処理を中断します。")
            return
        }
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "課題"

        Log.d(TAG, "onReceive: 通知処理を開始します - Title: $taskTitle, ID: $taskId")

        val channelId = "SCOMB_MOBILE_TASK_NOTIFICATION"
        val notificationId = taskId.hashCode()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("締め切りが近い課題")
            .setContentText(taskTitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    notify(notificationId, builder.build())
                    Log.d(TAG, "notify: 通知表示APIを呼び出しました (ID: $notificationId)")
                } catch (e: Exception) {
                    Log.e(TAG, "notify: 通知の表示に失敗しました", e)
                }
            } else {
                Log.w(TAG, "onReceive: 通知権限がありません (POST_NOTIFICATIONS denied)")
            }
        }
    }
}