package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.safefnow2.data.local.entity.GroupMember
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(groupMember: GroupMember)

    @Delete
    suspend fun delete(groupMember: GroupMember)

    @Query("SELECT * FROM group_member WHERE id_group = :idGroup AND id_user = :idUser")
    suspend fun getById(idGroup: String, idUser: String): GroupMember?

    @Query("SELECT * FROM group_member WHERE id_group = :idGroup AND id_user = :idUser")
    fun getByIdFlow(idGroup: String, idUser: String): Flow<GroupMember?>

    @Query("SELECT * FROM group_member ORDER BY id_group, id_user")
    suspend fun getAll(): List<GroupMember>

    @Query("SELECT * FROM group_member ORDER BY id_group, id_user")
    fun getAllFlow(): Flow<List<GroupMember>>

    @Query("SELECT * FROM group_member WHERE id_group = :idGroup")
    suspend fun getByGroupId(idGroup: String): List<GroupMember>

    @Query("SELECT * FROM group_member WHERE id_user = :idUser")
    suspend fun getByUserId(idUser: String): List<GroupMember>

    @Query("DELETE FROM group_member WHERE id_group = :idGroup")
    suspend fun deleteByGroupId(idGroup: String)
}
