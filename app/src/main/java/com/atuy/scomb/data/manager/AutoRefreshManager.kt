package com.atuy.scomb.data.manager

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atuy.scomb.worker.BackgroundSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoRefreshManager @Inject constructor(
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "AutoRefreshManager"

    // 更新イベントを通知するためのFlow
    private val _refreshEvent = MutableSharedFlow<Unit>()
    val refreshEvent = _refreshEvent.asSharedFlow()

    // デフォルトの更新チェック間隔（フォアグラウンド時）
    private val FOREGROUND_CHECK_INTERVAL = 60 * 60 * 1000L

    fun checkAndTriggerRefresh() {
        scope.launch {
            val lastSyncTime = settingsManager.lastSyncTimeFlow.first()
            val currentTime = System.currentTimeMillis()

            Log.d(
                TAG,
                "Checking refresh: Last=$lastSyncTime, Current=$currentTime, Diff=${currentTime - lastSyncTime}"
            )

            if (currentTime - lastSyncTime > FOREGROUND_CHECK_INTERVAL) {
                Log.d(TAG, "Triggering auto refresh")
                _refreshEvent.emit(Unit)
                // ループ防止のため、トリガーした時点で時刻を更新してしまう
                settingsManager.updateLastSyncTime(currentTime)
            } else {
                Log.d(TAG, "No refresh needed")
            }
        }
    }

    // 手動更新などで更新が完了した際に外部から時刻更新を呼ぶ用
    @Suppress("unused")
    fun updateLastSyncTime() {
        scope.launch {
            settingsManager.updateLastSyncTime(System.currentTimeMillis())
        }
    }

    // WorkManagerを使用したバックグラウンド更新のスケジュール
    fun scheduleAutoRefresh(intervalMinutes: Long) {
        val workManager = WorkManager.getInstance(context)
        val workRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "ScombBackgroundSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d(TAG, "Scheduled auto refresh with interval: $intervalMinutes minutes")
    }

    // バックグラウンド更新のキャンセル
    fun cancelAutoRefresh() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("ScombBackgroundSync")
        Log.d(TAG, "Cancelled auto refresh")
    }
}