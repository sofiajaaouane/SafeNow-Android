package com.example.safefnow2.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.ProfileActivity
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Alert
import com.example.safefnow2.data.local.entity.DeclarationAlert
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbObserve
import com.example.safefnow2.data.repository.GroupsOnlineFirstRepository
import com.example.safefnow2.data.repository.UserOnlineFirstRepository
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.data.sync.SyncScheduler
import com.example.safefnow2.ui.sos.SosUiEvent
import com.example.safefnow2.ui.sos.SosViewModel
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.util.AlertHelper
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.DeviceIdProvider
import com.example.safefnow2.util.GroupPopupHelper
import com.example.safefnow2.util.OnlineWriteGuard
import com.example.safefnow2.util.RequiredPermissions
import com.example.safefnow2.util.SessionManager
import com.example.safefnow2.service.AlwaysListenPrefs
import com.example.safefnow2.service.AlwaysListenService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HomeActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sosViewModel: SosViewModel by viewModels()
    private val userRepo by lazy { UserOnlineFirstRepository(this) }
    private val groupsRepo by lazy { GroupsOnlineFirstRepository(this) }
    private var cachedSenderName: String = "SafeNow"
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        AlertHelper.ensureChannel(this)
        if (AlwaysListenPrefs(this).isEnabled()) {
            AlwaysListenService.start(this)
        }
        startSosIdListenerWhileOpen()
        
        SyncScheduler.enqueueOneTime(this)
        ConnectivityObserver(this).isOnlineFlow()
            .onEach { online ->
                if (online) {
                    SyncScheduler.enqueueOneTime(this)
                    val userId = SessionManager.getCurrentUserId(this@HomeActivity).orEmpty()
                    if (userId.isNotEmpty()) {
                        runCatching {
                            OnlineRepository(
                                DatabaseProvider.get(this@HomeActivity),
                                OnlineWriteGuard(ConnectivityObserver(this@HomeActivity).isOnlineFlow()),
                                RtdbClient()
                            ).ensureDeviceId(userId, DeviceIdProvider.getDeviceId(this@HomeActivity))
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)

        lifecycleScope.launch {
            sosViewModel.events.collect { event ->
                val message = when (event) {
                    is SosUiEvent.Sent -> getString(R.string.toast_sos_sent)
                    is SosUiEvent.PeerMissing -> getString(R.string.toast_sos_peer_missing)
                    is SosUiEvent.Error -> when (event.message) {
                        "contact_device_unknown" -> getString(R.string.toast_sos_contact_no_device)
                        else -> event.message.ifEmpty { "Erreur SOS" }
                    }
                }
                Toast.makeText(this@HomeActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<FrameLayout>(R.id.btnHomeSos).setOnClickListener {
            val userId = SessionManager.getCurrentUserId(this@HomeActivity).orEmpty()
            if (userId.isEmpty()) return@setOnClickListener
            
            lifecycleScope.launch {
                // On tente de récupérer la position avec plus d'insistance
                val location = getCurrentLocation()
                val locationStr = if (location != null) {
                    getReadableAddress(location)
                } else {
                    "Position indisponible (Vérifiez votre GPS)"
                }

                withContext(Dispatchers.IO) {
                    val result = runCatching {
                        OnlineRepository(
                            DatabaseProvider.get(this@HomeActivity),
                            OnlineWriteGuard(ConnectivityObserver(this@HomeActivity).isOnlineFlow()),
                            RtdbClient()
                        ).sendGlobalSosToActiveGroups(userId, cachedSenderName)
                    }
                    withContext(Dispatchers.Main) {
                        if (result.isFailure) {
                            val ex = result.exceptionOrNull()
                            if (ex is com.example.safefnow2.data.repository.OfflineWriteNotAllowed) {
                                Toast.makeText(this@HomeActivity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@HomeActivity, (ex?.message ?: "Erreur SOS"), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val count = result.getOrNull() ?: 0
                            if (count <= 0) {
                                Toast.makeText(this@HomeActivity, "Aucun groupe SOS activé", Toast.LENGTH_SHORT).show()
                            } else {
                                saveAlertToLocalHistory(userId, "SOS GLOBAL", "GLOBAL", null, null, locationStr)
                                Toast.makeText(this@HomeActivity, getString(R.string.toast_sos_sent), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        val tvUserName      = findViewById<TextView>(R.id.tvHomeUserName)
        val tvInitials      = findViewById<TextView>(R.id.tvHomeAvatarInitials)
        val llGroupsStories = findViewById<LinearLayout>(R.id.llGroupsStories)

        findViewById<FrameLayout>(R.id.avatarContainer)?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navProfil)?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navContacts)?.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navAlertes)?.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navGroupes)?.setOnClickListener {
            startActivity(Intent(this, MyGroupsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navHistory)?.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<FrameLayout>(R.id.btnAddGroup)?.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }

        val userId = SessionManager.getCurrentUserId(this) ?: return

        lifecycleScope.launch {
            userRepo.user(userId).collect { u ->
                if (u != null) {
                    tvUserName.text = u.prenom
                    cachedSenderName = "${u.prenom} ${u.nom}".trim().ifEmpty { "SafeNow" }
                    val initials = buildString {
                        u.prenom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
                        u.nom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
                    }
                    if (initials.isNotEmpty()) tvInitials.text = initials
                    sosViewModel.syncDeviceRegistration(
                        displayName = cachedSenderName,
                        phone = u.numTel,
                        appUserId = u.idUser
                    )
                }
            }
        }

        lifecycleScope.launch {
            groupsRepo.pendingInvitesCount(userId).collect { count ->
                findViewById<View>(R.id.badgeNotif)?.visibility =
                    if (count > 0) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            groupsRepo.myGroups(userId).collect { groups ->
                renderGroupsStories(llGroupsStories, groups, userId)
            }
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            // Tente d'abord d'obtenir la position actuelle précise
            val current = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
            // Si échoue, prend la dernière position connue (plus rapide)
            current ?: fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getReadableAddress(location: Location): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@HomeActivity, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: "Adresse introuvable"
                } else {
                    "Lat: ${"%.4f".format(location.latitude)}, Lon: ${"%.4f".format(location.longitude)}"
                }
            } catch (e: Exception) {
                "Lat: ${"%.4f".format(location.latitude)}, Lon: ${"%.4f".format(location.longitude)}"
            }
        }
    }

    private fun saveAlertToLocalHistory(userId: String, typeStr: String, targetType: String, targetName: String?, targetId: String?, location: String? = null) {
        scope.launch(Dispatchers.IO) {
            val db = DatabaseProvider.get(this@HomeActivity)
            val alertId = UUID.randomUUID().toString()
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            val alert = Alert(
                idAlert = alertId,
                createdAt = now,
                typeAlert = typeStr,
                targetType = targetType,
                targetName = targetName,
                targetId = targetId
            )
            db.alertDao().insert(alert)
            
            val decl = DeclarationAlert(
                idUser = userId,
                idAlert = alertId,
                createdAt = now,
                localisation = location,
                status = "SENT"
            )
            db.declarationAlertDao().insert(decl)
        }
    }

    private fun startSosIdListenerWhileOpen() {
        val userId = SessionManager.getCurrentUserId(this)?.trim().orEmpty()
        if (userId.isEmpty()) return
        val rtdb = RtdbClient()
        val ref = rtdb.ref(RtdbPaths.userSosId(userId))
        var lastId: String? = null

        RtdbObserve.observe(ref)
            .onEach { snap ->
                val sosId = snap.getValue(String::class.java)?.trim().orEmpty()
                if (sosId.isEmpty()) return@onEach
                if (sosId == lastId) return@onEach
                lastId = sosId

                val sender = runCatching {
                    rtdb.get(RtdbPaths.userSosSenderName(userId)).getValue(String::class.java)
                }.getOrNull()?.trim().orEmpty()

                saveReceivedAlertToHistory(sender, sosId)

                AlertHelper.startSosIncomingActivity(this, sender, sosId)
            }
            .launchIn(lifecycleScope)
    }

    private fun saveReceivedAlertToHistory(senderName: String, sosId: String) {
        scope.launch(Dispatchers.IO) {
            val db = DatabaseProvider.get(this@HomeActivity)
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            val tempUserId = "received_$senderName"
            if (db.userDao().getById(tempUserId) == null) {
                db.userDao().insert(User(tempUserId, senderName, "", "", ""))
            }

            val alert = Alert(
                idAlert = sosId,
                createdAt = now,
                typeAlert = "SOS REÇU",
                targetType = "RECEIVED",
                targetName = senderName
            )
            db.alertDao().insert(alert)
            
            val decl = DeclarationAlert(
                idUser = tempUserId,
                idAlert = sosId,
                createdAt = now,
                localisation = "Position de l'expéditeur",
                status = "RECEIVED"
            )
            db.declarationAlertDao().insert(decl)
        }
    }

    private fun renderGroupsStories(container: LinearLayout?, groups: List<com.example.safefnow2.data.local.entity.EmergencyGroup>, userId: String) {
        if (container == null) return
        if (container.childCount > 1) {
            container.removeViews(1, container.childCount - 1)
        }

        groups.forEach { group ->
            val storyView = LayoutInflater.from(this@HomeActivity)
                .inflate(R.layout.item_group_story, container, false)

            storyView.findViewById<TextView>(R.id.tvStoryInitials).text =
                group.name.take(2).uppercase()
            storyView.findViewById<TextView>(R.id.tvStoryGroupName).text =
                group.name.take(8)

            storyView.setOnClickListener {
                GroupPopupHelper.show(
                    activity = this@HomeActivity,
                    scope = scope,
                    group = group,
                    userId = userId,
                    onGroupDeleted = { }
                )
            }

            container.addView(storyView)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val online = ConnectivityObserver(this@HomeActivity).isOnlineFlow().first()
            if (!online) {
                startActivity(Intent(this@HomeActivity, NoInternetActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
                return@launch
            }

            if (!RequiredPermissions.allGranted(this@HomeActivity)) {
                startActivity(Intent(this@HomeActivity, PermissionsActivity::class.java).apply {
                    putExtra(PermissionsActivity.EXTRA_REQUEST_ONLY, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
                return@launch
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
