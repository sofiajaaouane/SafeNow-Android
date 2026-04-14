package com.example.safefnow2.data.remote

import android.content.Context
import com.example.safefnow2.data.SosDevicePrefs
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class SosRepository(context: Context) {

    private val appContext = context.applicationContext
    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = SosDevicePrefs(appContext)
    private val rtdb = RtdbClient()

    private fun normalizePhoneDigits(phone: String): String =
        phone.filter { it.isDigit() }

    suspend fun syncMyDeviceToCloud(displayName: String, phone: String, appUserId: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        val deviceId = prefs.getOrCreateDeviceId()
        val digits = normalizePhoneDigits(phone)
        prefs.rememberSyncedPhone(phone)
        val data = hashMapOf(
            "fcmToken" to token,
            "displayName" to displayName,
            "phone" to phone.trim(),
            "phoneDigits" to digits,
            "appUserId" to appUserId,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("devices").document(deviceId).set(data, SetOptions.merge()).await()
        if (digits.isNotEmpty()) {
            val index = hashMapOf(
                "deviceId" to deviceId,
                "displayName" to displayName,
                "fcmToken" to token,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("phone_index").document(digits).set(index, SetOptions.merge()).await()
        }
    }

    suspend fun updateFcmTokenOnly(token: String) {
        val deviceId = prefs.getOrCreateDeviceId()
        val patch = hashMapOf(
            "fcmToken" to token,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("devices").document(deviceId).set(patch, SetOptions.merge()).await()
    }

    suspend fun lookupDeviceIdByPhone(phone: String): String? {
        val digits = normalizePhoneDigits(phone)
        if (digits.isEmpty()) return null
        val snap = firestore.collection("phone_index").document(digits).get().await()
        if (!snap.exists()) return null
        return snap.getString("deviceId")?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun sendSosToPeer(senderDisplayName: String) {
        val targetId = prefs.getPeerDeviceId()
        if (targetId.isBlank()) {
            throw IllegalStateException("peer_missing")
        }
        val hintDigits = prefs.getPeerPhoneDigits().ifBlank { null }
        val hintName = prefs.getPeerDisplayName().ifBlank { null }
        sendSosToTarget(targetId, senderDisplayName, hintDigits, hintName)
    }

    suspend fun sendSosToContactPhone(
        contactPhone: String,
        senderDisplayName: String,
        contactDisplayName: String
    ) {
        val digits = normalizePhoneDigits(contactPhone)
        if (digits.isEmpty()) throw IllegalStateException("contact_device_unknown")

        val targetUserId = resolveUserIdByPhone(contactPhone)
            ?: resolveUserIdByPhone(digits)
            ?: throw IllegalStateException("contact_device_unknown")

        val sosId = UUID.randomUUID().toString()
        writeSosIdToRtdb(targetUserId = targetUserId, sosId = sosId, senderName = senderDisplayName)
    }

    private suspend fun resolveUserIdByPhone(phoneKey: String): String? {
        val key = phoneKey.trim()
        if (key.isEmpty()) return null
        val snap = rtdb.get(RtdbPaths.userByPhone(key))
        return snap.getValue(String::class.java)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun writeSosIdToRtdb(targetUserId: String, sosId: String, senderName: String) {
        val updates = mapOf(
            RtdbPaths.userSosId(targetUserId) to sosId,
            RtdbPaths.userSosSenderName(targetUserId) to senderName,
            RtdbPaths.userSosCreatedAt(targetUserId) to rtdb.serverTimestamp()
        )
        rtdb.updateChildren("", updates)
    }

    private fun sendViaBackend(contactPhone: String, senderName: String) {
        val base = com.example.safefnow2.BuildConfig.SOS_BACKEND_URL.trim().let { if (it.endsWith("/")) it else "$it/" }
        val url = URL(base + "sos/send")
        val json = """{"targetPhone":"${contactPhone.replace("\"", "")}","senderName":"${senderName.replace("\"", "")}"}"""
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("backend_failed_$code")
        }
    }

    suspend fun sendSosToTarget(
        targetDeviceId: String,
        senderDisplayName: String,
        targetPhoneDigitsHint: String?,
        targetDisplayNameHint: String?
    ) {
        val myId = prefs.getOrCreateDeviceId()
        val targetId = targetDeviceId.trim()
        if (targetId.isEmpty()) {
            throw IllegalStateException("peer_missing")
        }
        val request = hashMapOf(
            "targetDeviceId" to targetId,
            "targetPhoneDigits" to (targetPhoneDigitsHint ?: ""),
            "senderDeviceId" to myId,
            "senderName" to senderDisplayName,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val ref = firestore.collection("sos_requests").add(request).await()
        val requestId = ref.id
        writeSosHistoryForBothParties(
            requestId = requestId,
            senderDeviceId = myId,
            targetDeviceId = targetId,
            senderDisplayName = senderDisplayName,
            targetPhoneDigitsHint = targetPhoneDigitsHint,
            targetDisplayNameHint = targetDisplayNameHint
        )
    }

    private suspend fun writeSosHistoryForBothParties(
        requestId: String,
        senderDeviceId: String,
        targetDeviceId: String,
        senderDisplayName: String,
        targetPhoneDigitsHint: String?,
        targetDisplayNameHint: String?
    ) {
        var senderDigits = prefs.getMyPhoneDigits()
        if (senderDigits.isEmpty()) {
            val sDoc = firestore.collection("devices").document(senderDeviceId).get().await()
            senderDigits = sDoc.getString("phoneDigits")
                ?: normalizePhoneDigits(sDoc.getString("phone") ?: "")
        }
        var targetDigits = targetPhoneDigitsHint?.filter { it.isDigit() } ?: ""
        var targetName = targetDisplayNameHint
        if (targetDigits.isEmpty() || targetName.isNullOrBlank()) {
            val tDoc = firestore.collection("devices").document(targetDeviceId).get().await()
            if (tDoc.exists()) {
                if (targetDigits.isEmpty()) {
                    targetDigits = tDoc.getString("phoneDigits")
                        ?: normalizePhoneDigits(tDoc.getString("phone") ?: "")
                }
                if (targetName.isNullOrBlank()) {
                    targetName = tDoc.getString("displayName") ?: ""
                }
            }
        }
        if (senderDigits.isEmpty() || targetDigits.isEmpty()) {
            return
        }
        val sent = hashMapOf(
            "ownerPhoneDigits" to senderDigits,
            "role" to "sent",
            "peerPhoneDigits" to targetDigits,
            "peerDisplayName" to (targetName ?: ""),
            "peerDeviceId" to targetDeviceId,
            "requestId" to requestId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val received = hashMapOf(
            "ownerPhoneDigits" to targetDigits,
            "role" to "received",
            "peerPhoneDigits" to senderDigits,
            "peerDisplayName" to senderDisplayName,
            "peerDeviceId" to senderDeviceId,
            "requestId" to requestId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("sos_history").document("${requestId}_sender").set(sent).await()
        firestore.collection("sos_history").document("${requestId}_receiver").set(received).await()
    }

    suspend fun loadSosHistoryForMyPhone(): List<SosHistoryEntry> {
        val digits = prefs.getMyPhoneDigits()
        if (digits.isEmpty()) return emptyList()
        val snap = firestore.collection("sos_history")
            .whereEqualTo("ownerPhoneDigits", digits)
            .get()
            .await()
        return snap.documents.map { doc ->
            SosHistoryEntry(
                requestId = doc.getString("requestId") ?: doc.id,
                role = doc.getString("role") ?: "",
                peerDisplayName = doc.getString("peerDisplayName") ?: "",
                peerPhoneDigits = doc.getString("peerPhoneDigits") ?: "",
                createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
            )
        }.sortedByDescending { it.createdAtMillis }
    }
}

data class SosHistoryEntry(
    val requestId: String,
    val role: String,
    val peerDisplayName: String,
    val peerPhoneDigits: String,
    val createdAtMillis: Long
)
