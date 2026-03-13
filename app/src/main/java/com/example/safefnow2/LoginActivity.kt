package com.example.safefnow2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.util.PasswordHasher
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        val editPhone = findViewById<EditText>(R.id.editLoginPhone)
        val editPassword = findViewById<EditText>(R.id.editLoginPassword)
        val imgToggle = findViewById<ImageView>(R.id.imgTogglePassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<TextView>(R.id.tvCreateAccount)

        var passwordVisible = false
        imgToggle.setOnClickListener {
            passwordVisible = !passwordVisible
            editPassword.inputType = if (passwordVisible) {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            imgToggle.setImageResource(
                if (passwordVisible) R.drawable.ic_visibility_off_gray else R.drawable.ic_visibility_gray
            )
        }

        btnLogin.setOnClickListener {
            val phone = editPhone.text.toString().trim()
            val password = editPassword.text.toString()
            if (phone.isEmpty()) {
                Toast.makeText(this, "Entrez votre numero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Entrez votre mot de passe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch {
                val user = withContext(Dispatchers.IO) {
                    DatabaseProvider.get(this@LoginActivity).userDao().getByPhone(phone)
                }
                if (user == null || !PasswordHasher.verify(password, user.password)) {
                    Toast.makeText(this@LoginActivity, "Numero ou mot de passe incorrect", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                SessionManager.setCurrentUserId(this@LoginActivity, user.idUser)
                startActivity(Intent(this@LoginActivity, HomeActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
                finish()
            }
        }

        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, SignUpStep1Activity::class.java))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
