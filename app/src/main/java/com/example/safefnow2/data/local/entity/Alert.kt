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
    val typeAlert: String
)
