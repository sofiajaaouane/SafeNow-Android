package com.example.safefnow2.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.safefnow2.data.local.entity.Amitier
import com.example.safefnow2.data.local.entity.User
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



    // Récupérer les invitations REÇUES en attente (PENDING)
    @Query("""
        SELECT u.* FROM user u
        INNER JOIN amitier a ON a.id_user1 = u.id_user
        WHERE a.id_user2 = :userId AND a.status = 'PENDING'
        ORDER BY u.nom, u.prenom
    """)
    suspend fun getPendingReceivedRequests(userId: String): List<User>

    @Query("""
        SELECT u.* FROM user u
        INNER JOIN amitier a ON a.id_user1 = u.id_user
        WHERE a.id_user2 = :userId AND a.status = 'PENDING'
        ORDER BY u.nom, u.prenom
    """)
    fun getPendingReceivedRequestsFlow(userId: String): Flow<List<User>>

    // Récupérer les invitations ENVOYÉES en attente
    @Query("""
        SELECT u.* FROM user u
        INNER JOIN amitier a ON a.id_user2 = u.id_user
        WHERE a.id_user1 = :userId AND a.status = 'PENDING'
        ORDER BY u.nom, u.prenom
    """)
    suspend fun getPendingSentRequests(userId: String): List<User>

    @Query("""
        SELECT u.* FROM user u
        INNER JOIN amitier a ON a.id_user2 = u.id_user
        WHERE a.id_user1 = :userId AND a.status = 'PENDING'
        ORDER BY u.nom, u.prenom
    """)
    fun getPendingSentRequestsFlow(userId: String): Flow<List<User>>

    // Accepter une invitation (mettre à jour le status)
    @Query("""
        UPDATE amitier 
        SET status = 'ACCEPTED' 
        WHERE id_user1 = :senderId AND id_user2 = :receiverId
    """)
    suspend fun acceptRequest(senderId: String, receiverId: String)

    // Refuser une invitation (supprimer l'entrée)
    @Query("""
        DELETE FROM amitier 
        WHERE id_user1 = :senderId AND id_user2 = :receiverId
    """)
    suspend fun rejectRequest(senderId: String, receiverId: String)

    // Récupérer tous les amis acceptés
    @Query("""
        SELECT u.* FROM user u
        INNER JOIN amitier a ON (
            (a.id_user1 = :userId AND a.id_user2 = u.id_user) OR
            (a.id_user2 = :userId AND a.id_user1 = u.id_user)
        )
        WHERE a.status = 'ACCEPTED'
        ORDER BY u.nom, u.prenom
    """)
    suspend fun getAcceptedFriends(userId: String): List<User>

    // Compter les invitations en attente
    @Query("""
        SELECT COUNT(*) FROM amitier 
        WHERE id_user2 = :userId AND status = 'PENDING'
    """)
    suspend fun getPendingRequestsCount(userId: String): Int

    @Query("""
        SELECT COUNT(*) FROM amitier 
        WHERE id_user2 = :userId AND status = 'PENDING'
    """)
    fun getPendingRequestsCountFlow(userId: String): Flow<Int>

    // Vérifier si deux utilisateurs sont déjà amis
    @Query("""
        SELECT COUNT(*) FROM amitier 
        WHERE ((id_user1 = :userId1 AND id_user2 = :userId2) OR 
               (id_user1 = :userId2 AND id_user2 = :userId1))
        AND status = 'ACCEPTED'
    """)
    suspend fun areFriends(userId1: String, userId2: String): Int


}
