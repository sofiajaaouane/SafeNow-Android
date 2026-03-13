package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.safefnow2.data.local.entity.Amitier
import kotlinx.coroutines.flow.Flow

@Dao
interface AmitierDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(amitier: Amitier)

    @Update
    suspend fun update(amitier: Amitier)

    @Delete
    suspend fun delete(amitier: Amitier)

    @Query("SELECT * FROM amitier WHERE id_user1 = :idUser1 AND id_user2 = :idUser2")
    suspend fun getById(idUser1: String, idUser2: String): Amitier?

    @Query("SELECT * FROM amitier WHERE id_user1 = :idUser1 AND id_user2 = :idUser2")
    fun getByIdFlow(idUser1: String, idUser2: String): Flow<Amitier?>

    @Query("SELECT * FROM amitier ORDER BY id_user1, id_user2")
    suspend fun getAll(): List<Amitier>

    @Query("SELECT * FROM amitier ORDER BY id_user1, id_user2")
    fun getAllFlow(): Flow<List<Amitier>>
}
