package com.example.safefnow2.fcm

import com.example.safefnow2.data.remote.SosRepository
import com.example.safefnow2.util.AlertHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeNowFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val repo = SosRepository(this)
        serviceScope.launch {
            try {
                repo.updateFcmTokenOnly(token)
            } catch (_: Exception) {
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        AlertHelper.ensureChannel(this)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "SOS"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(com.example.safefnow2.R.string.sos_notification_default_body)
        AlertHelper.showAlertNotification(this, title, body, message.hashCode())
        AlertHelper.vibrate(this)
    }
}
