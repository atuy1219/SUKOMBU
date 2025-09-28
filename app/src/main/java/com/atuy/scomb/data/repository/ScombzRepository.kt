package com.atuy.scomb.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.atuy.scomb.receiver.NotificationReceiver // 後で作成

class ScombzRepository(
    private val taskDao: TaskDao,
    private val scraper: ScombzScraper,
    private val context: Context // Contextをコンストラクタで受け取る
) {

    // DBからタスクを取得し、なければネットワークから取得してDBに保存＆通知予約
    suspend fun getAllTasks(sessionId: String, forceRefresh: Boolean = false): List<Task> {
        val cachedTasks = taskDao.getAllTasks()
        if (cachedTasks.isNotEmpty() && !forceRefresh) {
            return cachedTasks
        }

        val newTasks = scraper.fetchTasks(sessionId)
        for (task in newTasks) {
            taskDao.insertTask(task)
            // 取得した新しいタスクの通知を予約する
            scheduleNotification(task)
        }
        return newTasks
    }

    // ユーザーが質問した関数をここに配置
    private fun scheduleNotification(task: Task) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            // 通知タップ時にどのタスクか識別できるよう情報を渡す
            putExtra("TASK_ID", task.id)
            putExtra("TASK_TITLE", task.title)
            putExtra("TASK_URL", task.url)
        }

        // 同じタスクIDのPendingIntentは上書きされるので、重複予約の心配はない
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(), // 各タスクにユニークなIDを割り当てる
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 締め切り1時間前に設定
        val triggerAtMillis = task.deadline - 3600 * 1000

        // 未来の時刻の場合のみ予約
        if (triggerAtMillis > System.currentTimeMillis()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }
}