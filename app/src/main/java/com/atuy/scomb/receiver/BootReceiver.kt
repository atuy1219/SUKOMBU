package com.atuy.scomb.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.atuy.scomb.data.db.TaskDao
import com.atuy.scomb.domain.ScheduleNotificationsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var scheduleNotificationsUseCase: ScheduleNotificationsUseCase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "端末の起動を検知しました。通知を再スケジュールします。")

            val pendingResult = goAsync()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            scope.launch {
                try {
                    val tasks = taskDao.getAllTasks()

                    scheduleNotificationsUseCase(tasks)

                    Log.d("BootReceiver", "${tasks.size} 件の課題データを用いて通知を再設定しました。")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "通知の再スケジュールに失敗しました", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}