package com.example.safefnow2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.util.AlertHelper
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.*

class HomeActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        AlertHelper.ensureChannel(this)

        val tvUserName = findViewById<TextView>(R.id.tvHomeUserName)
        val tvInitials = findViewById<TextView>(R.id.tvHomeAvatarInitials)

        val avatarLayout = findViewById<FrameLayout>(R.id.avatarContainer)
        avatarLayout?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        val navProfil = findViewById<LinearLayout>(R.id.navProfil)
        navProfil?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        val navAlertes = findViewById<LinearLayout>(R.id.navAlertes)
        navAlertes?.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        val userId = SessionManager.getCurrentUserId(this) ?: return

        scope.launch {
            val user = withContext(Dispatchers.IO) {
                DatabaseProvider.get(this@HomeActivity).userDao().getById(userId)
            }

            user?.let {
                tvUserName.text = it.prenom

                val initials = buildString {
                    it.prenom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
                    it.nom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
                }

                if (initials.isNotEmpty()) {
                    tvInitials.text = initials
                }
            }
        }

        checkPendingNotifications(userId)
    }

    override fun onResume() {
        super.onResume()
        val userId = SessionManager.getCurrentUserId(this) ?: return
        checkPendingNotifications(userId)
    }

    private fun checkPendingNotifications(userId: String) {
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                DatabaseProvider.get(this@HomeActivity)
                    .amitierDao()
                    .getPendingRequestsCount(userId)
            }

            val badgeNotif = findViewById<View>(R.id.badgeNotif)
            badgeNotif?.visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}