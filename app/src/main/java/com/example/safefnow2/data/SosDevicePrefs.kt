package com.example.safefnow2.data

import android.content.Context
import java.util.UUID

class SosDevicePrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun getPeerDeviceId(): String = prefs.getString(KEY_PEER_ID, "") ?: ""

    fun setPeerDeviceId(id: String) {
        prefs.edit().putString(KEY_PEER_ID, id.trim()).apply()
    }

    companion object {
        private const val PREFS = "sos_device_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PEER_ID = "peer_device_id"
    }
}
