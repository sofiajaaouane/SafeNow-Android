package com.example.safefnow2.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "declaration_alert",
    primaryKeys = ["id_user", "id_alert"],
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id_user"],
            childColumns = ["id_user"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Alert::class,
            parentColumns = ["id_alert"],
            childColumns = ["id_alert"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DeclarationAlert(
    @ColumnInfo(name = "id_user")
    val idUser: String,
    @ColumnInfo(name = "id_alert")
    val idAlert: String,
    @ColumnInfo(name = "localisation")
    val localisation: String? = null,
    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,
    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,
    @ColumnInfo(name = "status")
    val status: String? = null,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: String? = null
)
