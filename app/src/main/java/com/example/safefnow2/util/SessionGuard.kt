package com.example.safefnow2.util

import android.content.Context
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SessionGuard {
    suspend fun isSessionUserDeleted(context: Context): Boolean {
        val userId = SessionManager.getCurrentUserId(context)?.trim().orEmpty()
        if (userId.isEmpty()) return false

        return withContext(Dispatchers.IO) {
            runCatching {
                val snap = RtdbClient().get(RtdbPaths.user(userId))
                !snap.exists()
            }.getOrDefault(false)
        }
    }
}

