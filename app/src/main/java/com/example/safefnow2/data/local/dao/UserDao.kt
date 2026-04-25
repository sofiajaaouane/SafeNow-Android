package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.safefnow2.data.local.entity.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("SELECT * FROM user WHERE id_user = :idUser")
    suspend fun getById(idUser: String): User?

    @Query("SELECT * FROM user WHERE num_tel = :numTel")
    suspend fun getByPhone(numTel: String): User?

    @Query("SELECT * FROM user WHERE email = :email")
    suspend fun getByEmail(email: String): User?

    @Query("SELECT * FROM user WHERE id_user = :idUser")
    fun getByIdFlow(idUser: String): Flow<User?>

    @Query("SELECT * FROM user ORDER BY nom, prenom")
    suspend fun getAll(): List<User>

    @Query("SELECT * FROM user ORDER BY nom, prenom")
    fun getAllFlow(): Flow<List<User>>
}
