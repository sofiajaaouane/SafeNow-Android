package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.safefnow2.R
import com.example.safefnow2.ProfileActivity
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

        // ── Vues ─────────────────────────────────────────────────────────────
        val tvUserName = findViewById<TextView>(R.id.tvHomeUserName)
        val tvInitials = findViewById<TextView>(R.id.tvHomeAvatarInitials)

        findViewById<LinearLayout>(R.id.navContacts).setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        // ── Avatar cliquable dans la top bar → ProfileActivity ───────────────
        val avatarLayout = findViewById<FrameLayout>(R.id.avatarContainer)
        avatarLayout?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // ── Icone Profil dans la bottom nav bar → ProfileActivity ────────────
        val navProfil = findViewById<LinearLayout>(R.id.navProfil)
        navProfil?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // ── Chargement des données du user connecté ───────────────────────────
        val userId = SessionManager.getCurrentUserId(this) ?: return
        scope.launch {
            val user = withContext(Dispatchers.IO) {
                DatabaseProvider.get(this@HomeActivity).userDao().getById(userId)
            }
            user?.let {
                // Affiche le prénom dans le "Bonjour,"
                tvUserName.text = it.prenom

                // Génère les initiales pour l'avatar
                val initials = buildString {
                    it.prenom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
                    it.nom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
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