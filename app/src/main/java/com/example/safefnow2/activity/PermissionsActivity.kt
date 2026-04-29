package com.example.safefnow2.activity

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
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.DeviceIdProvider
import com.example.safefnow2.util.OnlineWriteGuard
import com.example.safefnow2.util.RequiredPermissions
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class PermissionsActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val onlineRepo by lazy {
        val isOnline = ConnectivityObserver(this).isOnlineFlow()
        OnlineRepository(DatabaseProvider.get(this), OnlineWriteGuard(isOnline), RtdbClient())
    }

    private var pendingPermissionCheckId: Int? = null

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
        val checkNotification = findViewById<CheckBox>(R.id.checkNotification)
        val checkPhone = findViewById<CheckBox>(R.id.checkPhone)

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

        checkPhone.setOnClickListener {
            if (!checkPhone.isChecked) return@setOnClickListener
            checkPhone.isChecked = false
            pendingPermissionCheckId = R.id.checkPhone
            permissionCheckLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
        }

        updateCheckboxStates(checkGps, checkNotification, checkPhone)

        findViewById<Button>(R.id.btnContinuePermissions).setOnClickListener {
            if (intent.getBooleanExtra(EXTRA_REQUEST_ONLY, false)) {
                if (!RequiredPermissions.allGranted(this)) {
                    Toast.makeText(this, getString(R.string.permissions_toast_enable_all), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                goHome()
            } else {
                createAccountAndGoHome()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scope.launch {
            val online = ConnectivityObserver(this@PermissionsActivity).isOnlineFlow().first()
            if (!online) {
                startActivity(Intent(this@PermissionsActivity, NoInternetActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
                return@launch
            }
        }
        val checkGps = findViewById<CheckBox>(R.id.checkGps)
        val checkNotification = findViewById<CheckBox>(R.id.checkNotification)
        val checkPhone = findViewById<CheckBox>(R.id.checkPhone)
        updateCheckboxStates(checkGps, checkNotification, checkPhone)
    }

    private fun allRequiredPermissionsGranted(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
        return true
    }

    private fun updateCheckboxStates(
        checkGps: CheckBox,
        checkNotification: CheckBox,
        checkPhone: CheckBox
    ) {
        checkGps.isChecked = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        checkNotification.isChecked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        checkPhone.isChecked = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    }

    private fun createAccountAndGoHome() {
        val phone = intent.getStringExtra(SignUpStep1Activity.EXTRA_PHONE) ?: ""
        val passwordHash = intent.getStringExtra(SignUpStep2Activity.EXTRA_PASSWORD_HASH) ?: ""
        val email = intent.getStringExtra(SignUpStep2Activity.EXTRA_EMAIL)
        val firstName = intent.getStringExtra(SignUpStep3Activity.EXTRA_FIRST_NAME) ?: ""
        val lastName = intent.getStringExtra(SignUpStep3Activity.EXTRA_LAST_NAME) ?: ""

        if (phone.isEmpty() || passwordHash.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, getString(R.string.common_missing_data), Toast.LENGTH_SHORT).show()
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

            val precheck = withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(this@PermissionsActivity)
                val normalizedEmail = email?.trim()?.lowercase()?.ifEmpty { null }

                if (db.userDao().getByPhone(phone) != null) return@withContext "PHONE_TAKEN"
                if (normalizedEmail != null && db.userDao().getByEmail(normalizedEmail) != null) return@withContext "EMAIL_TAKEN"

                val online = ConnectivityObserver(this@PermissionsActivity).isOnlineFlow().first()
                if (!online) return@withContext "OFFLINE"

                val rtdb = RtdbClient()
                val digits = phone.filter { it.isDigit() }
                val byFull = rtdb.get(RtdbPaths.userByPhone(phone)).getValue(String::class.java)
                val byDigits = if (digits.isNotEmpty()) {
                    rtdb.get(RtdbPaths.userByPhone(digits)).getValue(String::class.java)
                } else null
                if (!byFull.isNullOrEmpty() || !byDigits.isNullOrEmpty()) return@withContext "PHONE_TAKEN"

                if (normalizedEmail != null) {
                    val usersSnap = rtdb.get("users")
                    val exists = usersSnap.children.any { child ->
                        val remoteEmail = child.child("email").getValue(String::class.java)
                            ?.trim()
                            ?.lowercase()
                            .orEmpty()
                        remoteEmail == normalizedEmail
                    }
                    if (exists) return@withContext "EMAIL_TAKEN"
                }
                null
            }

            when (precheck) {
                "OFFLINE" -> {
                    Toast.makeText(this@PermissionsActivity, getString(R.string.common_offline), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                "PHONE_TAKEN" -> {
                    Toast.makeText(this@PermissionsActivity, getString(R.string.permissions_phone_already_used), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                "EMAIL_TAKEN" -> {
                    Toast.makeText(this@PermissionsActivity, getString(R.string.permissions_email_already_used), Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }

            val result = withContext(Dispatchers.IO) { runCatching { onlineRepo.createAccount(user.copy(email = email?.trim()?.lowercase()?.ifEmpty { null })) } }
            if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                Toast.makeText(this@PermissionsActivity, getString(R.string.common_offline), Toast.LENGTH_SHORT).show()
                return@launch
            }
            runCatching {
                onlineRepo.ensureDeviceId(idUser, DeviceIdProvider.getDeviceId(this@PermissionsActivity))
            }
            SessionManager.setCurrentUserId(this@PermissionsActivity, idUser)
            goHome()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(this@PermissionsActivity, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REQUEST_ONLY = "extra_request_only"
    }
}
