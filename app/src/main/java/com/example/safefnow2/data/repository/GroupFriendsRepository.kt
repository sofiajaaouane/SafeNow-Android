package com.example.safefnow2.data.repository

import android.content.Context
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toUser

class GroupFriendsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = DatabaseProvider.get(appContext)

    suspend fun loadAvailableFriends(currentUserId: String, groupId: String): List<User> {
        val tryOnline = runCatching {
            val rtdb = RtdbClient()
            val outSnap = rtdb.get(RtdbPaths.friendshipOut(currentUserId))
            val inSnap = rtdb.get(RtdbPaths.friendshipIn(currentUserId))
            val membersSnap = rtdb.get(RtdbPaths.groupMembers(groupId))

            val existingMemberIds = membersSnap.children.mapNotNull { it.key }.toSet()

            fun collectAcceptedIds(snap: com.google.firebase.database.DataSnapshot): Set<String> {
                val ids = linkedSetOf<String>()
                snap.children.forEach { child ->
                    val otherId = child.key ?: return@forEach
                    val status = child.child("status").getValue(String::class.java) ?: "PENDING"
                    if (status == "ACCEPTED") ids.add(otherId)
                }
                return ids
            }

            val accepted = linkedSetOf<String>()
            accepted.addAll(collectAcceptedIds(outSnap))
            accepted.addAll(collectAcceptedIds(inSnap))

            val users = accepted
                .filter { it !in existingMemberIds && it != currentUserId }
                .mapNotNull { id ->
                    val uSnap = rtdb.get(RtdbPaths.user(id))
                    uSnap.toUser()
                }

            users.forEach { u -> db.userDao().insert(u) }
            users
        }

        if (tryOnline.isSuccess) return tryOnline.getOrThrow()

        val allFriends = db.amitierDao().getAcceptedFriends(currentUserId)
        val existingIds = db.groupMemberDao().getByGroupId(groupId).map { it.idUser }.toSet()
        return allFriends.filter { it.idUser !in existingIds }
    }
}

