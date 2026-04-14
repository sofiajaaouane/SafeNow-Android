package com.example.safefnow2.data.remote

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await

class RtdbClient(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(),
) {
    fun ref(path: String): DatabaseReference = db.getReference(path)

    suspend fun setValue(path: String, value: Any?) {
        ref(path).setValue(value).await()
    }

    suspend fun updateChildren(path: String, values: Map<String, Any?>) {
        ref(path).updateChildren(values).await()
    }

    suspend fun get(path: String): DataSnapshot {
        return ref(path).get().await()
    }

    fun serverTimestamp(): Any = ServerValue.TIMESTAMP
}

