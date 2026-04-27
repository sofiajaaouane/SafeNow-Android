package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.RequiredPermissions
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NoInternetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_internet)

        findViewById<Button>(R.id.btnRefreshInternet).setOnClickListener {
            lifecycleScope.launch {
                val online = ConnectivityObserver(this@NoInternetActivity).isOnlineFlow().first()
                if (!online) {
                    Toast.makeText(this@NoInternetActivity, "CONNECTION TO WIFI REQUIRED", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val hasSession = SessionManager.getCurrentUserId(this@NoInternetActivity) != null
                val next = if (!hasSession) {
                    LoginActivity::class.java
                } else if (!RequiredPermissions.allGranted(this@NoInternetActivity)) {
                    PermissionsActivity::class.java
                } else {
                    HomeActivity::class.java
                }

                startActivity(Intent(this@NoInternetActivity, next).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    if (next == PermissionsActivity::class.java) {
                        putExtra(PermissionsActivity.EXTRA_REQUEST_ONLY, true)
                    }
                })
                finish()
            }
        }
    }
}

