package com.example.safefnow2.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    private var instance: SafeNowDatabase? = null

    fun get(context: Context): SafeNowDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SafeNowDatabase::class.java,
                "safenow.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
