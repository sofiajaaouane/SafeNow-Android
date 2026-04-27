package com.example.safefnow2

import android.app.Application
import com.google.firebase.FirebaseApp

class SafeNowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
