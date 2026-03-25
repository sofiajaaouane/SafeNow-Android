package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.safefnow2.R

class SignUpStep3Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup_step3)

        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val btnPrevious = findViewById<TextView>(R.id.btnPrevious)
        val btnFinish = findViewById<TextView>(R.id.btnFinish)

        btnPrevious.setOnClickListener { finish() }

        btnFinish.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            if (firstName.isEmpty()) {
                Toast.makeText(this, "Entrez votre prenom", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (lastName.isEmpty()) {
                Toast.makeText(this, "Entrez votre nom", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = intent
            startActivity(
                Intent(this, PermissionsActivity::class.java)
                    .putExtra(SignUpStep1Activity.EXTRA_PHONE, i.getStringExtra(SignUpStep1Activity.EXTRA_PHONE))
                    .putExtra(SignUpStep2Activity.EXTRA_PASSWORD_HASH, i.getStringExtra(SignUpStep2Activity.EXTRA_PASSWORD_HASH))
                    .putExtra(SignUpStep2Activity.EXTRA_EMAIL, i.getStringExtra(SignUpStep2Activity.EXTRA_EMAIL))
                    .putExtra(EXTRA_FIRST_NAME, firstName)
                    .putExtra(EXTRA_LAST_NAME, lastName)
            )
        }
    }

    companion object {
        const val EXTRA_FIRST_NAME = "extra_first_name"
        const val EXTRA_LAST_NAME = "extra_last_name"
    }
}
