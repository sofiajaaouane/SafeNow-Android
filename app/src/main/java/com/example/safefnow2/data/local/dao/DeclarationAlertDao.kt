package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.safefnow2.data.local.entity.DeclarationAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeclarationAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(declarationAlert: DeclarationAlert)

    @Update
    suspend fun update(declarationAlert: DeclarationAlert)

    @Delete
    suspend fun delete(declarationAlert: DeclarationAlert)

    @Query("SELECT * FROM declaration_alert WHERE id_user = :idUser AND id_alert = :idAlert")
    suspend fun getById(idUser: String, idAlert: String): DeclarationAlert?

    @Query("SELECT * FROM declaration_alert WHERE id_user = :idUser AND id_alert = :idAlert")
    fun getByIdFlow(idUser: String, idAlert: String): Flow<DeclarationAlert?>

    @Query("SELECT * FROM declaration_alert ORDER BY created_at DESC")
    suspend fun getAll(): List<DeclarationAlert>

    @Query("SELECT * FROM declaration_alert ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<DeclarationAlert>>

    @Query("DELETE FROM declaration_alert WHERE id_user = :idUser")
    suspend fun deleteByUserId(idUser: String)
}
