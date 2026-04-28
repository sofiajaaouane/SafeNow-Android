package com.example.safefnow2.data.repository

import android.content.Context
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.GroupMember
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbObserve
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toEmergencyGroup
import com.example.safefnow2.data.remote.toUser
import com.example.safefnow2.util.ConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GroupMembersOnlineFirstRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = DatabaseProvider.get(appContext)
    private val rtdb = RtdbClient()
    private val isOnline = ConnectivityObserver(appContext).isOnlineFlow()

    fun members(groupId: String): Flow<List<User>> {
        val offlineFlow = roomMembers(groupId)

        return OnlineFirst.flow(
            isOnline = isOnline,
            online = {
                RtdbObserve.observe(rtdb.ref(RtdbPaths.groupMembers(groupId)))
                    .onEach { snap ->
                        withContext(Dispatchers.IO) {
                            // Ensure the group exists locally before inserting group_member rows (FK constraint).
                            val groupSnap = rtdb.get(RtdbPaths.emergencyGroup(groupId))
                            val group = groupSnap.toEmergencyGroup()
                            if (group != null) {
                                // Ensure admin user exists before inserting emergency_group (FK constraint).
                                val adminSnap = rtdb.get(RtdbPaths.user(group.idAdmin))
                                val adminUser = adminSnap.toUser()
                                if (adminUser != null) db.userDao().insert(adminUser)
                                if (db.userDao().getById(group.idAdmin) != null) {
                                db.emergencyGroupDao().insert(group)
                                }
                            }

                            db.groupMemberDao().deleteByGroupId(groupId)
                            val memberIds =
                                snap.children.mapNotNull { it.key?.trim()?.takeIf { k -> k.isNotEmpty() } }

                            // Ensure users exist locally before inserting group_member rows (FK constraint).
                            memberIds.forEach { uid ->
                                val uSnap = rtdb.get(RtdbPaths.user(uid))
                                val user = uSnap.toUser()
                                if (user != null) db.userDao().insert(user)
                            }

                            memberIds.forEach { uid ->
                                if (db.userDao().getById(uid) == null) return@forEach
                                if (db.emergencyGroupDao().getById(groupId) == null) return@forEach
                                runCatching {
                                    db.groupMemberDao().insert(GroupMember(idGroup = groupId, idUser = uid))
                                }
                            }
                        }
                    }
                    .map { Unit }
                    .distinctUntilChanged()
                    .combine(offlineFlow) { _, members -> members }
            },
            offline = { offlineFlow }
        )
            .flowOn(Dispatchers.IO)
    }

    private fun roomMembers(groupId: String): Flow<List<User>> {
        return db.groupMemberDao().getByGroupIdFlow(groupId)
            .combine(db.userDao().getAllFlow()) { members, users ->
                val map = users.associateBy { it.idUser }
                members.mapNotNull { gm -> map[gm.idUser] }
            }
            .map { list ->
                val seen = linkedSetOf<String>()
                list.filter { u -> seen.add(u.idUser) }
            }
    }
}

