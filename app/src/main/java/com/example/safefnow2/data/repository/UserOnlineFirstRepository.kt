package com.example.safefnow2.data.repository

import android.content.Context
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbObserve
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toDisease
import com.example.safefnow2.data.remote.toUser
import com.example.safefnow2.util.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class UserOnlineFirstRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = DatabaseProvider.get(appContext)
    private val rtdb = RtdbClient()
    private val isOnline = ConnectivityObserver(appContext).isOnlineFlow()

    fun user(userId: String): Flow<User?> {
        return OnlineFirst.flow(
            isOnline = isOnline,
            online = {
                RtdbObserve.observe(rtdb.ref(RtdbPaths.user(userId)))
                    .map { it.toUser() }
                    .onEach { u -> if (u != null) db.userDao().insert(u) }
            },
            offline = { db.userDao().getByIdFlow(userId) }
        )
    }

    fun diseases(userId: String): Flow<List<Disease>> {
        return OnlineFirst.flow(
            isOnline = isOnline,
            online = {
                RtdbObserve.observe(rtdb.ref(RtdbPaths.diseases(userId)))
                    .map { snap -> snap.children.mapNotNull { it.toDisease() } }
                    .onEach { list ->
                        db.diseaseDao().deleteByUserId(userId)
                        list.forEach { db.diseaseDao().insert(it) }
                    }
            },
            offline = { db.diseaseDao().getByUserIdFlow(userId) }
        )
    }
}

