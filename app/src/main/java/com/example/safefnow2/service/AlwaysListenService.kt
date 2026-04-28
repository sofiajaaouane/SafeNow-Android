package com.example.safefnow2.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.safefnow2.data.repository.SosInboxRepository
import com.example.safefnow2.util.AlertHelper
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AlwaysListenService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inboxRepo by lazy { SosInboxRepository(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification())
        startSosListenerIfLoggedIn()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startSosListenerIfLoggedIn() {
        val userId = SessionManager.getCurrentUserId(this)?.trim().orEmpty()
        if (userId.isEmpty()) return

        AlertHelper.ensureChannel(this)
        inboxRepo.incoming(userId)
            .onEach { incoming ->
                AlertHelper.startSosIncomingActivity(this, incoming.senderName, incoming.sosId)
            }
            .launchIn(scope)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SafeNow background",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SafeNow running")
            .setContentText("Listening in background")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "safenow_background"
        private const val NOTIF_ID = 2001

        fun start(context: Context) {
            val i = Intent(context, AlwaysListenService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlwaysListenService::class.java))
        }
    }
}
