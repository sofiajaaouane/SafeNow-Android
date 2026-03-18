package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.safefnow2.R
import com.example.safefnow2.util.PasswordHasher

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
            val hash = PasswordHasher.hash(password)
            startActivity(
                Intent(this, SignUpStep3Activity::class.java)
                    .putExtra(SignUpStep1Activity.EXTRA_PHONE, phone)
                    .putExtra(EXTRA_PASSWORD_HASH, hash)
                    .putExtra(EXTRA_EMAIL, email.ifEmpty { null })
            )
        }
    }

    companion object {
        const val EXTRA_PASSWORD_HASH = "extra_password_hash"
        const val EXTRA_EMAIL = "extra_email"
    }
}
