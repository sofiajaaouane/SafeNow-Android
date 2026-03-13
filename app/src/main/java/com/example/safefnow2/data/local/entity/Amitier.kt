package com.example.safefnow2.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "amitier",
    primaryKeys = ["id_user1", "id_user2"],
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id_user"],
            childColumns = ["id_user1"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["id_user"],
            childColumns = ["id_user2"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Amitier(
    @ColumnInfo(name = "id_user1")
    val idUser1: String,
    @ColumnInfo(name = "id_user2")
    val idUser2: String,
    @ColumnInfo(name = "status")
    val status: String = "PENDING"
)
