package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.safefnow2.data.local.entity.Disease
import kotlinx.coroutines.flow.Flow

@Dao
interface DiseaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(disease: Disease)

    @Update
    suspend fun update(disease: Disease)

    @Delete
    suspend fun delete(disease: Disease)

    @Query("SELECT * FROM disease WHERE id_disease = :idDisease")
    suspend fun getById(idDisease: String): Disease?

    @Query("SELECT * FROM disease WHERE id_disease = :idDisease")
    fun getByIdFlow(idDisease: String): Flow<Disease?>

    @Query("SELECT * FROM disease ORDER BY name")
    suspend fun getAll(): List<Disease>

    @Query("SELECT * FROM disease ORDER BY name")
    fun getAllFlow(): Flow<List<Disease>>

    @Query("SELECT * FROM disease WHERE id_user = :idUser ORDER BY name")
    suspend fun getByUserId(idUser: String): List<Disease>

    @Query("SELECT * FROM disease WHERE id_user = :idUser ORDER BY name")
    fun getByUserIdFlow(idUser: String): Flow<List<Disease>>
    // Supprimer toutes les maladies d'un utilisateur
    @Query("DELETE FROM disease WHERE id_user = :idUser")
    suspend fun deleteByUserId(idUser: String)

    @Query("SELECT COUNT(*) FROM disease WHERE id_user = :idUser AND name = :diseaseName")
    suspend fun countDiseaseForUser(idUser: String, diseaseName: String): Int








}
