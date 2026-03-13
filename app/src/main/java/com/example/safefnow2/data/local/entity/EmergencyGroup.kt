package com.example.safefnow2.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "emergency_group",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id_user"],
            childColumns = ["id_admin"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EmergencyGroup(
    @PrimaryKey
    @ColumnInfo(name = "id_group")
    val idGroup: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "sos_global")
    val sosGlobal: Int = 1,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: String? = null,
    @ColumnInfo(name = "id_admin")
    val idAdmin: String
)
