package com.example.safefnow2.util

import android.content.Context
import android.provider.Settings

object DeviceIdProvider {
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
    }
}

