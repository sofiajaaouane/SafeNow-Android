package com.example.safefnow2.data.repository

import android.content.Context
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbObserve
import com.example.safefnow2.data.remote.RtdbPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn

class SosInboxRepository(context: Context) {
    private val appContext = context.applicationContext
    private val rtdb = RtdbClient()

    fun incoming(userId: String): Flow<SosIncoming> {
        val uid = userId.trim()
        if (uid.isEmpty()) return OnlineFirst.value(SosIncoming.EMPTY).filter { false }

        val ref = rtdb.ref(RtdbPaths.userSosId(uid))
        return RtdbObserve.observe(ref)
            .map { snap -> snap.getValue(String::class.java)?.trim().orEmpty() }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .map { sosId ->
                val sender = runCatching {
                    rtdb.get(RtdbPaths.userSosSenderName(uid)).getValue(String::class.java)
                }.getOrNull()?.trim().orEmpty()
                SosIncoming(sosId = sosId, senderName = sender)
            }
            .flowOn(Dispatchers.IO)
    }
}

data class SosIncoming(
    val sosId: String,
    val senderName: String,
) {
    companion object {
        val EMPTY = SosIncoming("", "")
    }
}

