package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.safefnow2.data.local.entity.Alert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: Alert)

    @Update
    suspend fun update(alert: Alert)

    @Delete
    suspend fun delete(alert: Alert)

    @Query("SELECT * FROM alert WHERE id_alert = :idAlert")
    suspend fun getById(idAlert: String): Alert?

    @Query("SELECT * FROM alert WHERE id_alert = :idAlert")
    fun getByIdFlow(idAlert: String): Flow<Alert?>

    @Query("SELECT * FROM alert ORDER BY created_at DESC")
    suspend fun getAll(): List<Alert>

    @Query("SELECT * FROM alert ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<Alert>>
}
