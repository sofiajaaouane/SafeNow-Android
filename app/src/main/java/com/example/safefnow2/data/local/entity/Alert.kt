package com.example.safefnow2.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert")
data class Alert(
    @PrimaryKey
    @ColumnInfo(name = "id_alert")
    val idAlert: String,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: String? = null,
    @ColumnInfo(name = "type_alert")
    val typeAlert: String,
    @ColumnInfo(name = "sender_id")
    val senderId: String? = null,
    @ColumnInfo(name = "sender_name")
    val senderName: String? = null,
    @ColumnInfo(name = "target_type")
    val targetType: String? = null, // "GLOBAL", "GROUP", or "RECEIVED"
    @ColumnInfo(name = "target_name")
    val targetName: String? = null,
    @ColumnInfo(name = "target_id")
    val targetId: String? = null,   // ID du groupe ou de l'utilisateur cible
    @ColumnInfo(name = "sender_location")
    val senderLocation: String? = null,
    @ColumnInfo(name = "sender_latitude")
    val senderLatitude: Double? = null,
    @ColumnInfo(name = "sender_longitude")
    val senderLongitude: Double? = null,
    @ColumnInfo(name = "stopped_by_id")
    val stoppedById: String? = null,
    @ColumnInfo(name = "stopped_at")
    val stoppedAt: String? = null,
    @ColumnInfo(name = "stopped_location")
    val stoppedLocation: String? = null
    ,
    @ColumnInfo(name = "stopped_latitude")
    val stoppedLatitude: Double? = null,
    @ColumnInfo(name = "stopped_longitude")
    val stoppedLongitude: Double? = null
)
