package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.util.AlertHelper
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        AlertHelper.ensureChannel(this)

        val tvUserName = findViewById<TextView>(R.id.tvHomeUserName)
        val tvInitials = findViewById<TextView>(R.id.tvHomeAvatarInitials)

        findViewById<LinearLayout>(R.id.navContacts).setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        val userId = SessionManager.getCurrentUserId(this) ?: return
        scope.launch {
            val user = withContext(Dispatchers.IO) {
                DatabaseProvider.get(this@HomeActivity).userDao().getById(userId)
            }
            user?.let {
                tvUserName.text = "${it.prenom} ${it.nom}"
                val initials = buildString {
                    it.prenom.firstOrNull()?.uppercaseChar()?.let { append(it) }
                    it.nom.firstOrNull()?.uppercaseChar()?.let { append(it) }
                }
                if (initials.isNotEmpty()) tvInitials.text = initials
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
