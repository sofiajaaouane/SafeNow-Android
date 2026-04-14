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

    fun getPeerPhoneDigits(): String = prefs.getString(KEY_PEER_PHONE_DIGITS, "") ?: ""

    fun setPeerPhoneDigits(digits: String) {
        prefs.edit().putString(KEY_PEER_PHONE_DIGITS, digits.filter { it.isDigit() }).apply()
    }

    fun getPeerDisplayName(): String = prefs.getString(KEY_PEER_DISPLAY, "") ?: ""

    fun setPeerDisplayName(name: String) {
        prefs.edit().putString(KEY_PEER_DISPLAY, name.trim()).apply()
    }

    fun clearPeerPhoneHint() {
        prefs.edit().remove(KEY_PEER_PHONE_DIGITS).remove(KEY_PEER_DISPLAY).apply()
    }

    fun getMyPhoneDigits(): String = prefs.getString(KEY_MY_PHONE_DIGITS, "") ?: ""

    private fun setMyPhoneDigits(digits: String) {
        prefs.edit().putString(KEY_MY_PHONE_DIGITS, digits.filter { it.isDigit() }).apply()
    }

    fun rememberSyncedPhone(phone: String) {
        setMyPhoneDigits(phone.filter { it.isDigit() })
    }

    companion object {
        private const val PREFS = "sos_device_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PEER_ID = "peer_device_id"
        private const val KEY_PEER_PHONE_DIGITS = "peer_phone_digits"
        private const val KEY_PEER_DISPLAY = "peer_display_name"
        private const val KEY_MY_PHONE_DIGITS = "my_phone_digits"
    }
}
