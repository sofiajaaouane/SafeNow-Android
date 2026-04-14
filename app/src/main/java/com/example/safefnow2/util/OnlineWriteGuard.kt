package com.example.safefnow2.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class OnlineWriteGuard(
    private val isOnline: Flow<Boolean>,
) {
    suspend fun requireOnline(): Boolean = isOnline.first() == true
}

