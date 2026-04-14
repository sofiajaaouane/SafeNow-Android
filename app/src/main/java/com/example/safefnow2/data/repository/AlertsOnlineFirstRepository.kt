package com.example.safefnow2.data.repository

import android.content.Context
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbObserve
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toUser
import com.example.safefnow2.util.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AlertsOnlineFirstRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = DatabaseProvider.get(appContext)
    private val rtdb = RtdbClient()
    private val isOnline = ConnectivityObserver(appContext).isOnlineFlow()

    fun pendingReceivedUsers(currentUserId: String): Flow<List<User>> {
        return OnlineFirst.flow(
            isOnline = isOnline,
            online = {
                RtdbObserve.observe(rtdb.ref(RtdbPaths.friendshipIn(currentUserId)))
                    .transformLatest { snap ->
                        val senderIds = snap.children.mapNotNull { child ->
                            val id = child.key ?: return@mapNotNull null
                            val status = child.child("status").getValue(String::class.java) ?: "PENDING"
                            if (status == "PENDING") id else null
                        }
                        val users = senderIds.mapNotNull { id ->
                            val uSnap = rtdb.get(RtdbPaths.user(id))
                            uSnap.toUser()
                        }
                        emit(users)
                    }
                    .onEach { users -> users.forEach { db.userDao().insert(it) } }
            },
            offline = { db.amitierDao().getPendingReceivedRequestsFlow(currentUserId) }
        )
    }

    fun pendingSentUsers(currentUserId: String): Flow<List<User>> {
        return OnlineFirst.flow(
            isOnline = isOnline,
            online = {
                RtdbObserve.observe(rtdb.ref(RtdbPaths.friendshipOut(currentUserId)))
                    .transformLatest { snap ->
                        val receiverIds = snap.children.mapNotNull { child ->
                            val id = child.key ?: return@mapNotNull null
                            val status = child.child("status").getValue(String::class.java) ?: "PENDING"
                            if (status == "PENDING") id else null
                        }
                        val users = receiverIds.mapNotNull { id ->
                            val uSnap = rtdb.get(RtdbPaths.user(id))
                            uSnap.toUser()
                        }
                        emit(users)
                    }
                    .onEach { users -> users.forEach { db.userDao().insert(it) } }
            },
            offline = { db.amitierDao().getPendingSentRequestsFlow(currentUserId) }
        )
    }
}

