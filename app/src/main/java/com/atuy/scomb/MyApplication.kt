package com.atuy.scomb

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atuy.scomb.data.manager.SettingsManager
import com.atuy.scomb.util.AppLogger
import com.atuy.scomb.worker.BackgroundSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var httpLoggingInterceptor: HttpLoggingInterceptor

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeDebugMode()
        setupBackgroundSync()
    }

    private fun setupBackgroundSync() {
        applicationScope.launch {
            // 1時間ごとの定期実行を設定
            val syncRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                1, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(this@MyApplication).enqueueUniquePeriodicWork(
                "ScombBackgroundSync",
                ExistingPeriodicWorkPolicy.KEEP, // 既存のジョブがあれば維持する
                syncRequest
            )
        }
    }

    private fun observeDebugMode() {
        applicationScope.launch {
            settingsManager.debugModeFlow.collect { isDebugEnabled ->
                AppLogger.setEnabled(isDebugEnabled)

                httpLoggingInterceptor.level = if (isDebugEnabled) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannel() {
        val channelId = "SCOMB_MOBILE_TASK_NOTIFICATION"
        val name = "課題の通知"
        val descriptionText = "課題の締め切りが近づくと通知します"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}