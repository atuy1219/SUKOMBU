package com.atuy.scomb

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atuy.scomb.data.manager.SettingsManager
import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.util.AppLogger
import com.atuy.scomb.worker.BackgroundSyncWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
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
    lateinit var scombzRepository: ScombzRepository

    @Inject
    lateinit var httpLoggingInterceptor: HttpLoggingInterceptor

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initializeSpoofedFirebase()
        createNotificationChannel()
        observeDebugMode()
        setupBackgroundSync()
    }

    private fun initializeSpoofedFirebase() {
        try {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyA-iig91cMSha-mzuqqk3gO0b3DTWjbqnM")
                .setApplicationId("1:290422795353:android:87f07b3dd69e2db9d61548")
                .setProjectId("scombappfcm")
                .setGcmSenderId("290422795353")
                .setStorageBucket("scombappfcm.firebasestorage.app")
                .build()

            if (FirebaseApp.getApps(this).isNotEmpty()) {
                FirebaseApp.getInstance().delete()
            }

            FirebaseApp.initializeApp(this, options)
            AppLogger.d("Spoofed Firebase Initialized successfully")

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    AppLogger.e("FCM Token generation failed: ${task.exception}")
                    return@addOnCompleteListener
                }

                val token = task.result
                AppLogger.d("🎉 Spoofed Token: $token")

                // TODO: ここで取得したトークンをScombZのAPIサーバーへ送信・登録する処理を呼び出す
                applicationScope.launch {
                    try {
                        scombzRepository.registerFcmToken(token)
                    } catch (e: Exception) {
                        AppLogger.e("Failed to register FCM token: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to initialize spoofed Firebase: ${e.message}")
        }
    }
    private fun setupBackgroundSync() {
        applicationScope.launch {
            val syncRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                1, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(this@MyApplication).enqueueUniquePeriodicWork(
                "ScombBackgroundSync",
                ExistingPeriodicWorkPolicy.KEEP,
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
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}