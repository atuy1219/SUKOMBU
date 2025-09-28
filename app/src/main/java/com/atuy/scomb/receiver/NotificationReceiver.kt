package com.atuy.scomb.receiver

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("TASK_ID")
        val taskTitle = intent.getStringExtra("TASK_TITLE")

        // NotificationManagerCompatを使って通知を作成・表示する処理
        // ...
    }
}