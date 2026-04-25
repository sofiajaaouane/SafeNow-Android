package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignUpStep2Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup_step2)

        val phone = intent.getStringExtra(SignUpStep1Activity.EXTRA_PHONE) ?: ""
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val ivToggle = findViewById<ImageView>(R.id.ivTogglePassword)
        val btnPrevious = findViewById<TextView>(R.id.btnPrevious)
        val btnNext = findViewById<TextView>(R.id.btnNext)

        var passwordVisible = false
        ivToggle.setOnClickListener {
            passwordVisible = !passwordVisible
            etPassword.inputType = if (passwordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

        btnPrevious.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            val password = etPassword.text.toString()
            val email = etEmail.text.toString().trim()
            if (password.isEmpty()) {
                Toast.makeText(this, "Entrez un mot de passe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 7) {
                Toast.makeText(this, "Le mot de passe doit contenir au moins 7 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val normalizedEmail = email.trim().lowercase().ifEmpty { "" }
                if (normalizedEmail.isNotEmpty()) {
                    val isTaken = withContext(Dispatchers.IO) {
                        val db = DatabaseProvider.get(this@SignUpStep2Activity)
                        if (db.userDao().getByEmail(normalizedEmail) != null) return@withContext true

                        val online = ConnectivityObserver(this@SignUpStep2Activity).isOnlineFlow().first()
                        if (!online) return@withContext false

                        val rtdb = RtdbClient()
                        val usersSnap = rtdb.get("users")
                        usersSnap.children.any { child ->
                            val remoteEmail = child.child("email").getValue(String::class.java)
                                ?.trim()
                                ?.lowercase()
                                .orEmpty()
                            remoteEmail == normalizedEmail
                        }
                    }
                    if (isTaken) {
                        Toast.makeText(this@SignUpStep2Activity, "Cet email est deja utilise", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                val hash = PasswordHasher.hash(password)
                startActivity(
                    Intent(this@SignUpStep2Activity, SignUpStep3Activity::class.java)
                        .putExtra(SignUpStep1Activity.EXTRA_PHONE, phone)
                        .putExtra(EXTRA_PASSWORD_HASH, hash)
                        .putExtra(EXTRA_EMAIL, normalizedEmail.ifEmpty { null })
                )
            }
        }
    }

    companion object {
        const val EXTRA_PASSWORD_HASH = "extra_password_hash"
        const val EXTRA_EMAIL = "extra_email"
    }
}
