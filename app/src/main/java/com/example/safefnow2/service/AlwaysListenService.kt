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
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbObserve
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.repository.SosInboxRepository
import com.example.safefnow2.data.sync.SyncRepository
import com.example.safefnow2.util.AlertHelper
import com.example.safefnow2.util.SessionManager
import com.google.firebase.database.DataSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AlwaysListenService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inboxRepo by lazy { SosInboxRepository(this) }
    private val rtdb by lazy { RtdbClient() }
    private val syncRepo by lazy { SyncRepository(DatabaseProvider.get(this), rtdb) }
    private var syncJob: kotlinx.coroutines.Job? = null
    private var groupMembersWatches: MutableMap<String, kotlinx.coroutines.Job> = mutableMapOf()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification())
        startSosListenerIfLoggedIn()
        startRealtimeSyncIfLoggedIn()
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

    private fun startRealtimeSyncIfLoggedIn() {
        val userId = SessionManager.getCurrentUserId(this)?.trim().orEmpty()
        if (userId.isEmpty()) return

        fun scheduleSync() {
            syncJob?.cancel()
            syncJob = scope.launch {
                delay(500)
                runCatching { syncRepo.syncNow(userId) }
            }
        }

        fun updateGroupMemberWatches(membershipSnap: DataSnapshot) {
            val groupIds = linkedSetOf<String>()
            for (child in membershipSnap.children) {
                val gid = child.key?.trim().orEmpty()
                if (gid.isNotEmpty()) groupIds.add(gid)
            }

            val it = groupMembersWatches.keys.iterator()
            while (it.hasNext()) {
                val existing = it.next()
                if (!groupIds.contains(existing)) {
                    groupMembersWatches[existing]?.cancel()
                    it.remove()
                }
            }

            groupIds.forEach { gid ->
                if (groupMembersWatches.containsKey(gid)) return@forEach
                val job =
                    RtdbObserve.observe(rtdb.ref(RtdbPaths.groupMembers(gid)))
                        .onEach { scheduleSync() }
                        .launchIn(scope)
                groupMembersWatches[gid] = job
            }
        }

        // Initial sync.
        scheduleSync()

        // Watch key RTDB paths and refresh Room cache when they change.
        RtdbObserve.observe(rtdb.ref(RtdbPaths.groupMembersByUser(userId)))
            .onEach { snap ->
                scheduleSync()
                updateGroupMemberWatches(snap)
            }
            .launchIn(scope)

        RtdbObserve.observe(rtdb.ref(RtdbPaths.declarationAlerts(userId)))
            .onEach { scheduleSync() }
            .launchIn(scope)

        RtdbObserve.observe(rtdb.ref(RtdbPaths.friendshipOut(userId)))
            .onEach { scheduleSync() }
            .launchIn(scope)

        RtdbObserve.observe(rtdb.ref(RtdbPaths.friendshipIn(userId)))
            .onEach { scheduleSync() }
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
