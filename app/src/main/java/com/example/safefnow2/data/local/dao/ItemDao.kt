package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.safefnow2.data.local.entity.Item
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item)

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * FROM items WHERE id_item = :idItem")
    suspend fun getById(idItem: String): Item?

    @Query("SELECT * FROM items WHERE id_item = :idItem")
    fun getByIdFlow(idItem: String): Flow<Item?>

    @Query("SELECT * FROM items ORDER BY name")
    suspend fun getAll(): List<Item>

    @Query("SELECT * FROM items ORDER BY name")
    fun getAllFlow(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id_group = :idGroup ORDER BY name")
    suspend fun getByGroupId(idGroup: String): List<Item>

    @Query("SELECT * FROM items WHERE id_group = :idGroup ORDER BY name")
    fun getByGroupIdFlow(idGroup: String): Flow<List<Item>>
}
