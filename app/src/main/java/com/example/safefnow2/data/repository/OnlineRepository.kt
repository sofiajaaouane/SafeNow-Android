package com.example.safefnow2.data.repository

import com.example.safefnow2.data.local.SafeNowDatabase
import com.example.safefnow2.data.local.entity.Amitier
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.GroupMember
import com.example.safefnow2.data.local.entity.Item
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.sync.SyncRepository
import com.example.safefnow2.util.OnlineWriteGuard
import java.util.UUID

class OnlineRepository(
    private val database: SafeNowDatabase,
    private val guard: OnlineWriteGuard,
    private val rtdb: RtdbClient,
) {
    private val syncRepo = SyncRepository(database, rtdb)

    private fun phoneDigits(phone: String): String = phone.filter { it.isDigit() }

    private fun anyToBool(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() == 1
            is String -> {
                val v = value.trim().lowercase()
                v == "true" || v == "1" || v == "yes"
            }
            else -> false
        }
    }

    suspend fun ensureUserInRtdb(user: User) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val digits = phoneDigits(user.numTel)
        val updates = mapOf(
            RtdbPaths.user(user.idUser) to user.toMap(rtdb),
            RtdbPaths.userByPhone(user.numTel) to user.idUser,
            (if (digits.isNotEmpty()) RtdbPaths.userByPhone(digits) else "x") to user.idUser,
        )
        rtdb.updateChildren("", updates.filterKeys { it != "x" })
    }

    suspend fun updateProfile(user: User, diseases: List<Disease>) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()

        val oldPhone = database.userDao().getById(user.idUser)?.numTel
        val oldDigits = oldPhone?.let { phoneDigits(it) }.orEmpty()
        val newDigits = phoneDigits(user.numTel)

        val updates = mutableMapOf<String, Any?>()
        updates[RtdbPaths.user(user.idUser)] = user.toMap(rtdb)
        updates[RtdbPaths.userByPhone(user.numTel)] = user.idUser
        if (newDigits.isNotEmpty()) updates[RtdbPaths.userByPhone(newDigits)] = user.idUser
        if (!oldPhone.isNullOrEmpty() && oldPhone != user.numTel) {
            updates[RtdbPaths.userByPhone(oldPhone)] = null
        }
        if (oldDigits.isNotEmpty() && oldDigits != newDigits) {
            updates[RtdbPaths.userByPhone(oldDigits)] = null
        }
        updates[RtdbPaths.diseases(user.idUser)] = null
        diseases.forEach { d ->
            updates[RtdbPaths.disease(user.idUser, d.idDisease)] = d.toMap(rtdb)
        }
        rtdb.updateChildren("", updates)
        syncRepo.syncNow(user.idUser)
    }

    suspend fun createAccount(user: User) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val digits = phoneDigits(user.numTel)
        val updates = mapOf(
            RtdbPaths.user(user.idUser) to user.toMap(rtdb),
            RtdbPaths.userByPhone(user.numTel) to user.idUser,
            (if (digits.isNotEmpty()) RtdbPaths.userByPhone(digits) else "x") to user.idUser,
        )
        rtdb.updateChildren("", updates.filterKeys { it != "x" })
        syncRepo.syncNow(user.idUser)
    }

    suspend fun ensureDeviceId(userId: String, deviceId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        if (deviceId.isEmpty()) return
        val currentSnap = rtdb.get(RtdbPaths.userDeviceId(userId))
        val current = currentSnap.getValue(String::class.java).orEmpty()
        if (current != deviceId) {
            rtdb.setValue(RtdbPaths.userDeviceId(userId), deviceId)
        }
    }

    suspend fun deleteAccount(userId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val phone = database.userDao().getById(userId)?.numTel
        val updates = mapOf(
            RtdbPaths.user(userId) to null,
            (phone?.let { RtdbPaths.userByPhone(it) } ?: "x") to null,
            RtdbPaths.diseases(userId) to null,
            RtdbPaths.friendshipOut(userId) to null,
            RtdbPaths.friendshipIn(userId) to null,
            RtdbPaths.groupMembersByUser(userId) to null,
            RtdbPaths.declarationAlerts(userId) to null,
        )
        rtdb.updateChildren("", updates.filterKeys { it != "x" })
    }

    suspend fun createGroup(group: EmergencyGroup, adminId: String, necessities: List<Item>) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()

        val updates = mutableMapOf<String, Any?>()
        updates[RtdbPaths.emergencyGroup(group.idGroup)] = group.toMap(rtdb)
        updates[RtdbPaths.groupMember(group.idGroup, adminId)] = true
        updates[RtdbPaths.groupMembership(adminId, group.idGroup)] = true
        necessities.forEach { item ->
            updates[RtdbPaths.groupItem(group.idGroup, item.idItem)] = item.toMap(rtdb)
        }
        rtdb.updateChildren("", updates)
        syncRepo.syncNow(adminId)
    }

    suspend fun setGroupActive(group: EmergencyGroup, active: Boolean, currentUserId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val updated = group.copy(sosGlobal = if (active) 1 else 0)
        rtdb.setValue(RtdbPaths.emergencyGroup(group.idGroup), updated.toMap(rtdb))
        syncRepo.syncNow(currentUserId)
    }

    suspend fun deleteGroup(groupId: String, currentUserId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val updates = mapOf(
            RtdbPaths.emergencyGroup(groupId) to null,
            RtdbPaths.groupMembers(groupId) to null,
            RtdbPaths.groupItems(groupId) to null,
        )
        rtdb.updateChildren("", updates)
        syncRepo.syncNow(currentUserId)
    }

    suspend fun addMember(groupId: String, memberUserId: String, currentUserId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val updates = mapOf(
            RtdbPaths.groupMember(groupId, memberUserId) to true,
            RtdbPaths.groupMembership(memberUserId, groupId) to true,
        )
        rtdb.updateChildren("", updates)
        syncRepo.syncNow(currentUserId)
    }

    suspend fun removeMember(groupId: String, memberUserId: String, currentUserId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val updates = mapOf(
            RtdbPaths.groupMember(groupId, memberUserId) to null,
            RtdbPaths.groupMembership(memberUserId, groupId) to null,
        )
        rtdb.updateChildren("", updates)
        syncRepo.syncNow(currentUserId)
    }

    suspend fun sendGroupSos(groupId: String, senderName: String, groupAdminId: String, currentUserId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        if (currentUserId != groupAdminId) throw IllegalStateException("not_admin")
        val membersSnap = rtdb.get(RtdbPaths.groupMembers(groupId))
        val memberIds = membersSnap.children.mapNotNull { it.key }.filter { it.isNotBlank() }
        if (memberIds.isEmpty()) return

        val sosId = UUID.randomUUID().toString()
        val updates = mutableMapOf<String, Any?>()
        memberIds.filter { it != currentUserId }.forEach { uid ->
            updates[RtdbPaths.userSosId(uid)] = sosId
            updates[RtdbPaths.userSosSenderName(uid)] = senderName
            updates[RtdbPaths.userSosCreatedAt(uid)] = rtdb.serverTimestamp()
        }
        if (updates.isNotEmpty()) {
            rtdb.updateChildren("", updates)
        }
    }

    suspend fun sendGlobalSosToActiveGroups(currentUserId: String, senderName: String): Int {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val membershipSnap = rtdb.get(RtdbPaths.groupMembersByUser(currentUserId))
        val groupIds = membershipSnap.children.mapNotNull { it.key }.filter { it.isNotBlank() }
        if (groupIds.isEmpty()) return 0

        val sosId = UUID.randomUUID().toString()
        val updates = mutableMapOf<String, Any?>()
        val receivers = linkedSetOf<String>()

        for (groupId in groupIds) {
            val gSnap = rtdb.get(RtdbPaths.emergencyGroup(groupId))
            val sosGlobalVal: Any? =
                gSnap.child("sosGlobal").getValue(Any::class.java)
                    ?: gSnap.child("sos_global").getValue(Any::class.java)
            if (!anyToBool(sosGlobalVal)) continue

            val membersSnap = rtdb.get(RtdbPaths.groupMembers(groupId))
            membersSnap.children.mapNotNull { it.key }
                .filter { it.isNotBlank() && it != currentUserId }
                .forEach { uid ->
                    receivers.add(uid)
                    updates[RtdbPaths.userSosId(uid)] = sosId
                    updates[RtdbPaths.userSosSenderName(uid)] = senderName
                    updates[RtdbPaths.userSosCreatedAt(uid)] = rtdb.serverTimestamp()
                }
        }

        if (updates.isNotEmpty()) {
            rtdb.updateChildren("", updates)
        }
        return receivers.size
    }

    suspend fun sendFriendRequest(edge: Amitier, currentUserId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val out = mapOf("status" to edge.status, "updatedAt" to rtdb.serverTimestamp())
        val inMap = mapOf("status" to edge.status, "updatedAt" to rtdb.serverTimestamp())
        val updates = mapOf(
            RtdbPaths.friendshipEdgeOut(edge.idUser1, edge.idUser2) to out,
            RtdbPaths.friendshipEdgeIn(edge.idUser2, edge.idUser1) to inMap,
        )
        rtdb.updateChildren("", updates)
        syncRepo.syncNow(currentUserId)
    }

    suspend fun acceptFriendRequest(senderId: String, receiverId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val updates = mapOf(
            RtdbPaths.friendshipEdgeOut(senderId, receiverId) to mapOf("status" to "ACCEPTED", "updatedAt" to rtdb.serverTimestamp()),
            RtdbPaths.friendshipEdgeIn(receiverId, senderId) to mapOf("status" to "ACCEPTED", "updatedAt" to rtdb.serverTimestamp()),
        )
        rtdb.updateChildren("", updates)
        syncRepo.syncNow(receiverId)
    }

    suspend fun rejectFriendRequest(senderId: String, receiverId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val updates = mapOf(
            RtdbPaths.friendshipEdgeOut(senderId, receiverId) to null,
            RtdbPaths.friendshipEdgeIn(receiverId, senderId) to null,
        )
        rtdb.updateChildren("", updates)
        syncRepo.syncNow(receiverId)
    }

    suspend fun deleteFriendEdge(edge: Amitier, currentUserId: String) {
        if (!guard.requireOnline()) throw OfflineWriteNotAllowed()
        val updates = mapOf(
            RtdbPaths.friendshipEdgeOut(edge.idUser1, edge.idUser2) to null,
            RtdbPaths.friendshipEdgeIn(edge.idUser2, edge.idUser1) to null,
        )
        rtdb.updateChildren("", updates)
        syncRepo.syncNow(currentUserId)
    }
}

class OfflineWriteNotAllowed : Exception("offline_write_not_allowed")

private fun User.toMap(rtdb: RtdbClient): Map<String, Any?> = mapOf(
    "idUser" to idUser,
    "nom" to nom,
    "prenom" to prenom,
    "numTel" to numTel,
    "password" to password,
    "email" to email,
    "description" to description,
    "bloodType" to bloodType,
    "updatedAt" to rtdb.serverTimestamp(),
)

private fun Disease.toMap(rtdb: RtdbClient): Map<String, Any?> = mapOf(
    "idDisease" to idDisease,
    "name" to name,
    "description" to description,
    "createdAt" to createdAt,
    "idUser" to idUser,
    "updatedAt" to rtdb.serverTimestamp(),
)

private fun EmergencyGroup.toMap(rtdb: RtdbClient): Map<String, Any?> = mapOf(
    "idGroup" to idGroup,
    "name" to name,
    "description" to description,
    "sosGlobal" to sosGlobal,
    "createdAt" to createdAt,
    "idAdmin" to idAdmin,
    "updatedAt" to rtdb.serverTimestamp(),
)

private fun Item.toMap(rtdb: RtdbClient): Map<String, Any?> = mapOf(
    "idItem" to idItem,
    "type" to type,
    "name" to name,
    "description" to description,
    "createdAt" to createdAt,
    "idGroup" to idGroup,
    "updatedAt" to rtdb.serverTimestamp(),
)

