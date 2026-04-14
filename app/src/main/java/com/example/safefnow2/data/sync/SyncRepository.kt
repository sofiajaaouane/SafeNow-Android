package com.example.safefnow2.data.sync

import com.example.safefnow2.data.local.SafeNowDatabase
import com.example.safefnow2.data.local.entity.Amitier
import com.example.safefnow2.data.local.entity.DeclarationAlert
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.GroupMember
import com.example.safefnow2.data.local.entity.Item
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toAlert
import com.example.safefnow2.data.remote.toDeclarationAlert
import com.example.safefnow2.data.remote.toDisease
import com.example.safefnow2.data.remote.toEmergencyGroup
import com.example.safefnow2.data.remote.toItem
import com.example.safefnow2.data.remote.toUser

class SyncRepository(
    private val database: SafeNowDatabase,
    private val rtdb: RtdbClient,
) {
    suspend fun syncNow(currentUserId: String) {
        syncUser(currentUserId)
        syncDiseases(currentUserId)
        syncFriendships(currentUserId)
        syncGroups(currentUserId)
        syncDeclarationAlerts(currentUserId)
    }

    private suspend fun syncUser(userId: String) {
        val snap = rtdb.get(RtdbPaths.user(userId))
        val user = snap.toUser() ?: return
        database.userDao().insert(user)
    }

    private suspend fun syncDiseases(userId: String) {
        val snap = rtdb.get(RtdbPaths.diseases(userId))
        val list = mutableListOf<Disease>()
        for (child in snap.children) {
            val disease = child.toDisease() ?: continue
            list.add(disease)
        }
        database.diseaseDao().deleteByUserId(userId)
        list.forEach { database.diseaseDao().insert(it) }
    }

    private suspend fun syncFriendships(userId: String) {
        val outSnap = rtdb.get(RtdbPaths.friendshipOut(userId))
        for (child in outSnap.children) {
            val otherId = child.key ?: continue
            val status = child.child("status").getValue(String::class.java) ?: "PENDING"
            database.amitierDao().insert(Amitier(idUser1 = userId, idUser2 = otherId, status = status))
            syncUser(otherId)
        }

        val inSnap = rtdb.get(RtdbPaths.friendshipIn(userId))
        for (child in inSnap.children) {
            val senderId = child.key ?: continue
            val status = child.child("status").getValue(String::class.java) ?: "PENDING"
            database.amitierDao().insert(Amitier(idUser1 = senderId, idUser2 = userId, status = status))
            syncUser(senderId)
        }
    }

    private suspend fun syncGroups(userId: String) {
        val membershipSnap = rtdb.get(RtdbPaths.groupMembersByUser(userId))
        val groupIds = membershipSnap.children.mapNotNull { it.key }

        for (groupId in groupIds) {
            val groupSnap = rtdb.get(RtdbPaths.emergencyGroup(groupId))
            val group = groupSnap.toEmergencyGroup() ?: continue
            database.emergencyGroupDao().insert(group)

            val membersSnap = rtdb.get(RtdbPaths.groupMembers(groupId))
            database.groupMemberDao().deleteByGroupId(groupId)
            for (m in membersSnap.children) {
                val memberUserId = m.key ?: continue
                database.groupMemberDao().insert(GroupMember(idGroup = groupId, idUser = memberUserId))
                syncUser(memberUserId)
            }

            val itemsSnap = rtdb.get(RtdbPaths.groupItems(groupId))
            database.itemDao().deleteByGroupId(groupId)
            for (itSnap in itemsSnap.children) {
                val item = itSnap.toItem() ?: continue
                database.itemDao().insert(item)
            }
        }
    }

    private suspend fun syncDeclarationAlerts(userId: String) {
        val declSnap = rtdb.get(RtdbPaths.declarationAlerts(userId))
        database.declarationAlertDao().deleteByUserId(userId)

        for (child in declSnap.children) {
            val decl = child.toDeclarationAlert() ?: continue
            database.declarationAlertDao().insert(decl)

            val alertId = decl.idAlert
            val alertSnap = rtdb.get(RtdbPaths.alert(alertId))
            val alert = alertSnap.toAlert()
            if (alert != null) {
                database.alertDao().insert(alert)
            }
        }
    }
}

