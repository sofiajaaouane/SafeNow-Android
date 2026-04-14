package com.example.safefnow2.data.remote

object RtdbPaths {
    fun user(userId: String) = "users/$userId"
    fun userByPhone(phone: String) = "usersByPhone/$phone"
    fun userDeviceId(userId: String) = "users/$userId/deviceId"
    fun diseases(userId: String) = "diseases/$userId"
    fun disease(userId: String, diseaseId: String) = "diseases/$userId/$diseaseId"

    fun emergencyGroup(groupId: String) = "emergencyGroups/$groupId"
    fun groupMembers(groupId: String) = "groupMembers/$groupId"
    fun groupMember(groupId: String, userId: String) = "groupMembers/$groupId/$userId"
    fun groupMembersByUser(userId: String) = "groupMembersByUser/$userId"
    fun groupMembership(userId: String, groupId: String) = "groupMembersByUser/$userId/$groupId"

    fun groupItems(groupId: String) = "items/$groupId"
    fun groupItem(groupId: String, itemId: String) = "items/$groupId/$itemId"

    fun friendshipOut(senderId: String) = "amitier_out/$senderId"
    fun friendshipIn(receiverId: String) = "amitier_in/$receiverId"
    fun friendshipEdgeOut(senderId: String, receiverId: String) = "amitier_out/$senderId/$receiverId"
    fun friendshipEdgeIn(receiverId: String, senderId: String) = "amitier_in/$receiverId/$senderId"

    fun alert(alertId: String) = "alerts/$alertId"
    fun declarationAlerts(userId: String) = "declarationAlerts/$userId"
    fun declarationAlert(userId: String, alertId: String) = "declarationAlerts/$userId/$alertId"
}

