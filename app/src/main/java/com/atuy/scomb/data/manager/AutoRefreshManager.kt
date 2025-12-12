package com.atuy.scomb.data.manager

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoRefreshManager @Inject constructor(
    private val settingsManager: SettingsManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "AutoRefreshManager"

    // 更新イベントを通知するためのFlow
    private val _refreshEvent = MutableSharedFlow<Unit>()
    val refreshEvent = _refreshEvent.asSharedFlow()

    // 1時間 (ミリ秒)
    private val REFRESH_INTERVAL = 60 * 60 * 1000L

    fun checkAndTriggerRefresh() {
        scope.launch {
            val lastSyncTime = settingsManager.lastSyncTimeFlow.first()
            val currentTime = System.currentTimeMillis()

            Log.d(TAG, "Checking refresh: Last=$lastSyncTime, Current=$currentTime, Diff=${currentTime - lastSyncTime}")

            if (currentTime - lastSyncTime > REFRESH_INTERVAL) {
                Log.d(TAG, "Triggering auto refresh")
                _refreshEvent.emit(Unit)
                // ループ防止のため、トリガーした時点で時刻を更新してしまう
                // ※ 本来は更新完了後にすべきだが、複数ViewModelで整合性を取るのが難しいためここで更新
                settingsManager.updateLastSyncTime(currentTime)
            } else {
                Log.d(TAG, "No refresh needed")
            }
        }
    }

    // 手動更新などで更新が完了した際に外部から時刻更新を呼ぶ用
    fun updateLastSyncTime() {
        scope.launch {
            settingsManager.updateLastSyncTime(System.currentTimeMillis())
        }
    }
}