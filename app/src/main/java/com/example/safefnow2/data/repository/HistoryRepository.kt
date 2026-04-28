package com.example.safefnow2.data.repository

import android.content.Context
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Alert
import com.example.safefnow2.data.local.entity.DeclarationAlert
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.sync.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class HistoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = DatabaseProvider.get(appContext)
    private val rtdb = RtdbClient()

    fun history(userId: String): Flow<List<AlertWithUser>> {
        val declFlow = db.declarationAlertDao().getAllByUserFlow(userId)
        val alertsFlow = db.alertDao().getAllFlow()
        val usersFlow = db.userDao().getAllFlow()

        return combine(declFlow, alertsFlow, usersFlow) { decls, alerts, users ->
            val alertMap = alerts.associateBy { it.idAlert }
            val userMap = users.associateBy { it.idUser }
            decls.mapNotNull { decl ->
                val alert = alertMap[decl.idAlert]
                val sender = alert?.senderId?.let { sid -> userMap[sid] }
                AlertWithUser(alert = alert, sender = sender, declaration = decl)
            }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    suspend fun syncNow(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.success(Unit)
        return withContext(Dispatchers.IO) {
            runCatching { SyncRepository(db, rtdb).syncNow(userId) }
        }
    }
}

data class AlertWithUser(
    val alert: Alert?,
    val sender: User?,
    val declaration: DeclarationAlert,
)

