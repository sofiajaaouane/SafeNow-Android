package com.example.safefnow2.data.remote

import android.content.Context
import com.example.safefnow2.data.SosDevicePrefs
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class SosRepository(context: Context) {

    private val appContext = context.applicationContext
    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = SosDevicePrefs(appContext)

    suspend fun syncMyDeviceToCloud(displayName: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        val deviceId = prefs.getOrCreateDeviceId()
        val data = hashMapOf(
            "fcmToken" to token,
            "displayName" to displayName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("devices").document(deviceId).set(data, SetOptions.merge()).await()
    }

    suspend fun updateFcmTokenOnly(token: String) {
        val deviceId = prefs.getOrCreateDeviceId()
        val patch = hashMapOf(
            "fcmToken" to token,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("devices").document(deviceId).set(patch, SetOptions.merge()).await()
    }

    suspend fun sendSosToPeer(senderDisplayName: String) {
        val myId = prefs.getOrCreateDeviceId()
        val targetId = prefs.getPeerDeviceId()
        if (targetId.isBlank()) {
            throw IllegalStateException("peer_missing")
        }
        val request = hashMapOf(
            "targetDeviceId" to targetId,
            "senderDeviceId" to myId,
            "senderName" to senderDisplayName,
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("sos_requests").add(request).await()
    }
}
