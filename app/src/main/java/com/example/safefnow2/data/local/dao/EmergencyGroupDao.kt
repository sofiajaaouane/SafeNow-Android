package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.safefnow2.data.local.entity.EmergencyGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(emergencyGroup: EmergencyGroup)

    @Update
    suspend fun update(emergencyGroup: EmergencyGroup)

    @Delete
    suspend fun delete(emergencyGroup: EmergencyGroup)

    @Query("SELECT * FROM emergency_group WHERE id_group = :idGroup")
    suspend fun getById(idGroup: String): EmergencyGroup?

    @Query("SELECT * FROM emergency_group WHERE id_group = :idGroup")
    fun getByIdFlow(idGroup: String): Flow<EmergencyGroup?>

    @Query("SELECT * FROM emergency_group ORDER BY name")
    suspend fun getAll(): List<EmergencyGroup>

    @Query("SELECT * FROM emergency_group ORDER BY name")
    fun getAllFlow(): Flow<List<EmergencyGroup>>
}
