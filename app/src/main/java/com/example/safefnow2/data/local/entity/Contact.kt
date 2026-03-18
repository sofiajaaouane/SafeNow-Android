package com.example.safefnow2.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Represents another user saved as a contact by the current user.
@Entity(tableName = "contact")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "full_name")
    val fullName: String,
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String
)
