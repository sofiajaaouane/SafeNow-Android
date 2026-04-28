package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toUser
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.data.sync.SyncRepository
import com.example.safefnow2.util.AlertHelper
import com.example.safefnow2.util.DeviceIdProvider
import com.example.safefnow2.util.PasswordHasher
import com.example.safefnow2.util.RequiredPermissions
import com.example.safefnow2.util.SessionManager
import com.example.safefnow2.ui.session.SessionViewModel
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionVm: SessionViewModel by viewModels()
    private val onlineFlow by lazy { ConnectivityObserver(this).isOnlineFlow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SessionManager.getCurrentUserId(this) != null) {
            scope.launch {
                if (!onlineFlow.first()) {
                    startActivity(Intent(this@LoginActivity, NoInternetActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                    return@launch
                }

                if (sessionVm.isSessionDeletedOnline()) {
                    SessionManager.clear(this@LoginActivity)
                    setupLoginUi()
                    return@launch
                }

                val next =
                    if (RequiredPermissions.allGranted(this@LoginActivity)) HomeActivity::class.java
                    else PermissionsActivity::class.java
                startActivity(Intent(this@LoginActivity, next).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    if (next == PermissionsActivity::class.java) {
                        putExtra(PermissionsActivity.EXTRA_REQUEST_ONLY, true)
                    }
                })
                finish()
            }
            return
        }
        setupLoginUi()
    }

    private fun setupLoginUi() {
        setContentView(R.layout.login)
        AlertHelper.ensureChannel(this)

        val editPhone = findViewById<EditText>(R.id.editLoginPhone)
        val editPassword = findViewById<EditText>(R.id.editLoginPassword)
        val imgToggle = findViewById<ImageView>(R.id.imgTogglePassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<TextView>(R.id.tvCreateAccount)

        var passwordVisible = false
        imgToggle.setOnClickListener {
            passwordVisible = !passwordVisible
            editPassword.inputType = if (passwordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
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
                if (!onlineFlow.first()) {
                    startActivity(Intent(this@LoginActivity, NoInternetActivity::class.java))
                    return@launch
                }
                val user = withContext(Dispatchers.IO) {
                    val db = DatabaseProvider.get(this@LoginActivity)
                    val local = db.userDao().getByPhone(phone)
                    if (local != null) return@withContext local

                    val rtdb = RtdbClient()
                    val digits = phone.filter { it.isDigit() }
                    val userId =
                        rtdb.get(RtdbPaths.userByPhone(phone)).getValue(String::class.java)
                            ?: (if (digits.isNotEmpty()) rtdb.get(RtdbPaths.userByPhone(digits)).getValue(String::class.java) else null)
                            ?: return@withContext null
                    val userSnap = rtdb.get(RtdbPaths.user(userId))
                    val remoteUser = userSnap.toUser()
                    if (remoteUser != null) {
                        db.userDao().insert(remoteUser)
                        runCatching { SyncRepository(db, rtdb).syncNow(remoteUser.idUser) }
                    }
                    remoteUser
                }
                if (user == null || !PasswordHasher.verify(password, user.password)) {
                    Toast.makeText(this@LoginActivity, "Numero ou mot de passe incorrect", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                SessionManager.setCurrentUserId(this@LoginActivity, user.idUser)
                runCatching {
                    val rtdb = RtdbClient()
                    val onlineRepo = OnlineRepository(
                        DatabaseProvider.get(this@LoginActivity),
                        OnlineWriteGuard(ConnectivityObserver(this@LoginActivity).isOnlineFlow()),
                        rtdb
                    )
                    onlineRepo.ensureUserInRtdb(user)
                    onlineRepo.ensureDeviceId(user.idUser, DeviceIdProvider.getDeviceId(this@LoginActivity))
                }
                val next =
                    if (RequiredPermissions.allGranted(this@LoginActivity)) HomeActivity::class.java
                    else PermissionsActivity::class.java
                startActivity(
                    Intent(this@LoginActivity, next).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        if (next == PermissionsActivity::class.java) {
                            putExtra(PermissionsActivity.EXTRA_REQUEST_ONLY, true)
                        }
                    }
                )
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
