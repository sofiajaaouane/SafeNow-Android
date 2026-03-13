package com.example.safefnow2.util

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREFS_NAME = "safenow_session"
    private const val KEY_USER_ID = "current_user_id"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setCurrentUserId(context: Context, userId: String) {
        prefs(context).edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getCurrentUserId(context: Context): String? {
        return prefs(context).getString(KEY_USER_ID, null)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_USER_ID).apply()
    }
}
