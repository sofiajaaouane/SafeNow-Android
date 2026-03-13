package com.example.safefnow2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    private var pendingPermissionCheckId: Int? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        createAccountAndGoHome()
    }

    private val permissionCheckLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        pendingPermissionCheckId?.let { checkId ->
            findViewById<CheckBox>(checkId).isChecked = result.values.all { it }
        }
        pendingPermissionCheckId = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        val checkGps = findViewById<CheckBox>(R.id.checkGps)
        val checkGpsBackground = findViewById<CheckBox>(R.id.checkGpsBackground)
        val checkPhone = findViewById<CheckBox>(R.id.checkPhone)
        val checkNotification = findViewById<CheckBox>(R.id.checkNotification)

        checkGps.setOnClickListener {
            if (!checkGps.isChecked) return@setOnClickListener
            checkGps.isChecked = false
            pendingPermissionCheckId = R.id.checkGps
            permissionCheckLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        checkGpsBackground.setOnClickListener {
            if (!checkGpsBackground.isChecked) return@setOnClickListener
            checkGpsBackground.isChecked = false
            pendingPermissionCheckId = R.id.checkGpsBackground
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            permissionCheckLauncher.launch(perms.toTypedArray())
        }

        checkPhone.setOnClickListener {
            if (!checkPhone.isChecked) return@setOnClickListener
            checkPhone.isChecked = false
            pendingPermissionCheckId = R.id.checkPhone
            permissionCheckLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
        }

        checkNotification.setOnClickListener {
            if (!checkNotification.isChecked) return@setOnClickListener
            checkNotification.isChecked = false
            pendingPermissionCheckId = R.id.checkNotification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionCheckLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            } else {
                checkNotification.isChecked = true
                pendingPermissionCheckId = null
            }
        }

        updateCheckboxStates(checkGps, checkGpsBackground, checkPhone, checkNotification)

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

    private fun updateCheckboxStates(
        checkGps: CheckBox,
        checkGpsBackground: CheckBox,
        checkPhone: CheckBox,
        checkNotification: CheckBox
    ) {
        checkGps.isChecked = hasLocation()
        checkGpsBackground.isChecked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        checkPhone.isChecked = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        checkNotification.isChecked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
