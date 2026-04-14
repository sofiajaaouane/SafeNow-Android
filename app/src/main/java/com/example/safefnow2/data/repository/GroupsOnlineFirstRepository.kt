package com.example.safefnow2.data.repository

import android.content.Context
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbObserve
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toEmergencyGroup
import com.example.safefnow2.util.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GroupsOnlineFirstRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = DatabaseProvider.get(appContext)
    private val rtdb = RtdbClient()
    private val isOnline = ConnectivityObserver(appContext).isOnlineFlow()

    fun myGroups(userId: String): Flow<List<EmergencyGroup>> {
        return OnlineFirst.flow(
            isOnline = isOnline,
            online = {
                RtdbObserve.observe(rtdb.ref(RtdbPaths.groupMembersByUser(userId)))
                    .transformLatest { snap ->
                        val groupIds = snap.children.mapNotNull { it.key }
                        val groups = groupIds.mapNotNull { gid ->
                            val gSnap = rtdb.get(RtdbPaths.emergencyGroup(gid))
                            gSnap.toEmergencyGroup()
                        }
                        emit(groups)
                    }
                    .onEach { groups -> groups.forEach { db.emergencyGroupDao().insert(it) } }
            },
            offline = { db.emergencyGroupDao().getAllFlow() }
        )
    }

    fun pendingInvitesCount(userId: String): Flow<Int> {
        return OnlineFirst.flow(
            isOnline = isOnline,
            online = {
                RtdbObserve.observe(rtdb.ref(RtdbPaths.friendshipIn(userId)))
                    .map { snap ->
                        snap.children.count { it.child("status").getValue(String::class.java) == "PENDING" }
                    }
            },
            offline = { db.amitierDao().getPendingRequestsCountFlow(userId) }
        )
    }
}

