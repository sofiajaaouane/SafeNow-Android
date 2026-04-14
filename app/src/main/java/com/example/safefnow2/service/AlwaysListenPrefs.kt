package com.example.safefnow2.service

import android.content.Context

class AlwaysListenPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("safenow_always_listen", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
    }
}

