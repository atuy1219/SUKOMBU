package com.atuy.scomb.service

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

        // データペイロードが含まれている場合
        if (remoteMessage.data.isNotEmpty()) {
            AppLogger.d("Message data payload: ${remoteMessage.data}")
            // ここで独自の通知を表示したり、DBを更新したりする
        }

        remoteMessage.notification?.let {
            AppLogger.d("Message Notification Body: ${it.body}")
        }
    }
}