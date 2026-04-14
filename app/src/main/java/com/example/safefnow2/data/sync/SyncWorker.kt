package com.example.safefnow2.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.util.SessionManager

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val userId = SessionManager.getCurrentUserId(applicationContext) ?: return Result.success()
        val db = DatabaseProvider.get(applicationContext)
        val repo = SyncRepository(db, RtdbClient())
        return runCatching {
            repo.syncNow(userId)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}

