package com.example.safefnow2.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_member",
    primaryKeys = ["id_group", "id_user"],
    foreignKeys = [
        ForeignKey(
            entity = EmergencyGroup::class,
            parentColumns = ["id_group"],
            childColumns = ["id_group"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["id_user"],
            childColumns = ["id_user"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GroupMember(
    @ColumnInfo(name = "id_group")
    val idGroup: String,
    @ColumnInfo(name = "id_user")
    val idUser: String
)
