package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.safefnow2.data.local.entity.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact): Long

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM contact ORDER BY full_name")
    fun getAllFlow(): Flow<List<Contact>>

    @Query("SELECT * FROM contact ORDER BY full_name")
    suspend fun getAll(): List<Contact>

    @Query("SELECT * FROM contact WHERE id = :id")
    suspend fun getById(id: Long): Contact?
}
