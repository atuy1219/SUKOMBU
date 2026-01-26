package com.atuy.scomb.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.atuy.scomb.MainActivity
import com.atuy.scomb.R
import com.atuy.scomb.data.repository.ScombzRepository
import com.atuy.scomb.util.AppLogger
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScombMessagingService : FirebaseMessagingService() {

    companion object {
        private const val NEWS_NOTIFICATION_CHANNEL_ID = "SCOMB_MOBILE_NEWS_NOTIFICATION"
    }

    @Inject
    lateinit var scombzRepository: ScombzRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppLogger.d("Refreshed token: $token")
        // トークンが更新された場合も、サーバーへ再送信が必要です
        serviceScope.launch {
            try {
                scombzRepository.registerFcmToken(token)
            } catch (e: Exception) {
                AppLogger.e("Failed to update token on server: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 必要ならScopeをキャンセルするが、Serviceが死ぬ時はプロセスごと死ぬことが多いので
        // 厳密なキャンセルは必須ではないかもしれないが、マナーとして Job.cancel() ぐらいは想定できる
        // ここでは簡単なフィールド初期化で済ませているため、onDestroyで明示的にキャンセルはしていないが、
        // SupervisorJobを使っているので問題ない範囲。
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        AppLogger.d("From: ${remoteMessage.from}")

        val data = remoteMessage.data
        val urlFromData = data["url"] ?: data["newsUrl"]
        val urlFromNotification = remoteMessage.notification?.link?.toString()
        val newsUrl = urlFromData ?: urlFromNotification

        val title = data["title"] ?: remoteMessage.notification?.title ?: getString(R.string.app_name)
        val body = data["body"] ?: remoteMessage.notification?.body ?: "新しいお知らせがあります"

        if (newsUrl.isNullOrBlank()) {
            AppLogger.d("Message data payload: ${remoteMessage.data}")
            remoteMessage.notification?.let { AppLogger.d("Message Notification Body: ${it.body}") }
            return
        }

        showNewsNotification(title, body, newsUrl)
    }

    private fun showNewsNotification(title: String, body: String, url: String) {
        val notificationId = url.hashCode()
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_url", url)
            putExtra("notification_type", "news")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NEWS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        } else {
            AppLogger.w("Notification permission not granted; skipping news notification")
        }
    }
}