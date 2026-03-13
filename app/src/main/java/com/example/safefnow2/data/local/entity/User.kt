package com.example.safefnow2.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
    @PrimaryKey
    @ColumnInfo(name = "id_user")
    val idUser: String,
    @ColumnInfo(name = "nom")
    val nom: String,
    @ColumnInfo(name = "prenom")
    val prenom: String,
    @ColumnInfo(name = "num_tel")
    val numTel: String,
    @ColumnInfo(name = "password")
    val password: String,
    @ColumnInfo(name = "email")
    val email: String? = null,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "blood_type")
    val bloodType: String? = null
)
