package com.example.safefnow2.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "disease",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id_user"],
            childColumns = ["id_user"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Disease(
    @PrimaryKey
    @ColumnInfo(name = "id_disease")
    val idDisease: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: String? = null,
    @ColumnInfo(name = "id_user")
    val idUser: String
)
