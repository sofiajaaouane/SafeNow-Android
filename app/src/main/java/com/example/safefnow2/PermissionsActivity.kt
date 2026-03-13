package com.example.safefnow2

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class PermissionsActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        createAccountAndGoHome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        findViewById<Button>(R.id.btnContinuePermissions).setOnClickListener {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun createAccountAndGoHome() {
        val phone = intent.getStringExtra(SignUpStep1Activity.EXTRA_PHONE) ?: ""
        val passwordHash = intent.getStringExtra(SignUpStep2Activity.EXTRA_PASSWORD_HASH) ?: ""
        val email = intent.getStringExtra(SignUpStep2Activity.EXTRA_EMAIL)
        val firstName = intent.getStringExtra(SignUpStep3Activity.EXTRA_FIRST_NAME) ?: ""
        val lastName = intent.getStringExtra(SignUpStep3Activity.EXTRA_LAST_NAME) ?: ""

        if (phone.isEmpty() || passwordHash.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "Donnees manquantes", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val idUser = UUID.randomUUID().toString()
            val user = User(
                idUser = idUser,
                nom = lastName,
                prenom = firstName,
                numTel = phone,
                password = passwordHash,
                email = email,
                description = null,
                bloodType = null
            )
            withContext(Dispatchers.IO) {
                DatabaseProvider.get(this@PermissionsActivity).userDao().insert(user)
            }
            SessionManager.setCurrentUserId(this@PermissionsActivity, idUser)
            startActivity(
                Intent(this@PermissionsActivity, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
