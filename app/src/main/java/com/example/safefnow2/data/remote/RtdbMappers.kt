package com.example.safefnow2.data.remote

import com.example.safefnow2.data.local.entity.Alert
import com.example.safefnow2.data.local.entity.DeclarationAlert
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.Item
import com.example.safefnow2.data.local.entity.User
import com.google.firebase.database.DataSnapshot

private fun DataSnapshot.mapValue(): Map<String, Any?> {
    val v = value ?: return emptyMap()
    if (v is Map<*, *>) {
        return v.entries.associate { (k, any) -> k?.toString().orEmpty() to any }
    }
    @Suppress("UNCHECKED_CAST")
    return (v as? Map<String, Any?>) ?: emptyMap()
}

private fun Map<String, Any?>.str(key: String): String? = (this[key] as? String)?.trim()
private fun Map<String, Any?>.numStr(key: String): String? = (this[key] as? Number)?.toString()
private fun Map<String, Any?>.strAny(vararg keys: String): String? {
    for (k in keys) {
        val v = str(k)
        if (!v.isNullOrEmpty()) return v
    }
    return null
}
private fun Map<String, Any?>.dbl(key: String): Double? {
    val v = this[key]
    return when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
}

fun DataSnapshot.toUser(): User? {
    val m = mapValue()
    val idUser = m.strAny("idUser", "id_user") ?: key?.trim() ?: return null
    if (idUser.isBlank()) return null
    val nom = m.strAny("nom") ?: ""
    val prenom = m.strAny("prenom") ?: ""
    val numTel = m.strAny("numTel", "num_tel", "phone") ?: ""
    val password = m.strAny("password") ?: ""
    return User(
        idUser = idUser,
        nom = nom,
        prenom = prenom,
        numTel = numTel,
        password = password,
        email = m.strAny("email"),
        description = m.strAny("description"),
        bloodType = m.strAny("bloodType", "blood_type"),
    )
}

fun DataSnapshot.toDisease(): Disease? {
    val m = mapValue()
    val idDisease = m.strAny("idDisease", "id_disease") ?: return null
    val name = m.strAny("name") ?: return null
    val idUser = m.strAny("idUser", "id_user") ?: return null
    return Disease(
        idDisease = idDisease,
        name = name,
        description = m.strAny("description"),
        createdAt = m.strAny("createdAt", "created_at") ?: m.numStr("createdAt") ?: m.numStr("created_at"),
        idUser = idUser
    )
}

fun DataSnapshot.toEmergencyGroup(): EmergencyGroup? {
    val m = mapValue()
    val idGroup = m.strAny("idGroup", "id_group") ?: return null
    val name = m.strAny("name") ?: return null
    val idAdmin = m.strAny("idAdmin", "id_admin") ?: return null
    val sosGlobal = ((m["sosGlobal"] ?: m["sos_global"]) as? Number)?.toInt() ?: 1
    return EmergencyGroup(
        idGroup = idGroup,
        name = name,
        description = m.strAny("description"),
        sosGlobal = sosGlobal,
        createdAt = m.strAny("createdAt", "created_at") ?: m.numStr("createdAt") ?: m.numStr("created_at"),
        idAdmin = idAdmin
    )
}

fun DataSnapshot.toItem(): Item? {
    val m = mapValue()
    val idItem = m.strAny("idItem", "id_item") ?: return null
    val type = m.strAny("type") ?: return null
    val name = m.strAny("name") ?: return null
    val idGroup = m.strAny("idGroup", "id_group") ?: return null
    return Item(
        idItem = idItem,
        type = type,
        name = name,
        description = m.strAny("description"),
        createdAt = m.strAny("createdAt", "created_at") ?: m.numStr("createdAt") ?: m.numStr("created_at"),
        idGroup = idGroup
    )
}

fun DataSnapshot.toDeclarationAlert(fallbackIdUser: String? = null): DeclarationAlert? {
    val m = mapValue()
    val idUser = m.strAny("idUser", "id_user") ?: fallbackIdUser?.takeIf { it.isNotBlank() } ?: return null
    val idAlert = m.strAny("idAlert", "id_alert") ?: key?.takeIf { it.isNotBlank() } ?: return null
    return DeclarationAlert(
        idUser = idUser,
        idAlert = idAlert,
        localisation = m.strAny("localisation"),
        latitude = m.dbl("latitude"),
        longitude = m.dbl("longitude"),
        status = m.strAny("status"),
        createdAt = m.strAny("createdAt", "created_at") ?: m.numStr("createdAt") ?: m.numStr("created_at"),
    )
}

fun DataSnapshot.toAlert(): Alert? {
    val m = mapValue()
    val idAlert = m.strAny("idAlert", "id_alert") ?: key?.takeIf { it.isNotBlank() } ?: return null
    val typeAlert = m.strAny("typeAlert", "type_alert") ?: return null
    return Alert(
        idAlert = idAlert,
        createdAt = m.strAny("createdAt", "created_at") ?: m.numStr("createdAt") ?: m.numStr("created_at"),
        typeAlert = typeAlert,
        senderId = m.strAny("senderId", "sender_id"),
        senderName = m.strAny("senderName", "sender_name"),
        targetType = m.strAny("targetType", "target_type"),
        targetName = m.strAny("targetName", "target_name"),
        targetId = m.strAny("targetId", "target_id"),
        senderLocation = m.strAny("senderLocation", "sender_location", "localisation"),
        senderLatitude = m.dbl("senderLatitude") ?: m.dbl("sender_latitude") ?: m.dbl("latitude"),
        senderLongitude = m.dbl("senderLongitude") ?: m.dbl("sender_longitude") ?: m.dbl("longitude"),
        stoppedById = m.strAny("stoppedById", "stopped_by_id"),
        stoppedAt = m.strAny("stoppedAt", "stopped_at"),
        stoppedLocation = m.strAny("stoppedLocation", "stopped_location"),
        stoppedLatitude = m.dbl("stoppedLatitude") ?: m.dbl("stopped_latitude"),
        stoppedLongitude = m.dbl("stoppedLongitude") ?: m.dbl("stopped_longitude"),
    )
}

