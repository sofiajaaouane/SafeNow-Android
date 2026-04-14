package com.example.safefnow2.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object OnlineFirst {
    fun <T> flow(
        isOnline: Flow<Boolean>,
        online: () -> Flow<T>,
        offline: () -> Flow<T>,
    ): Flow<T> {
        return isOnline.flatMapLatest { onlineNow ->
            if (onlineNow) online() else offline()
        }
    }

    fun <T> value(value: T): Flow<T> = flowOf(value)
}

