package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.ProfileActivity
import com.example.safefnow2.data.local.DatabaseProvider
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
import com.example.safefnow2.util.SessionManager
import com.example.safefnow2.service.AlwaysListenPrefs
import com.example.safefnow2.service.AlwaysListenService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sosViewModel: SosViewModel by viewModels()
    private val userRepo by lazy { UserOnlineFirstRepository(this) }
    private val groupsRepo by lazy { GroupsOnlineFirstRepository(this) }
    private var cachedSenderName: String = "SafeNow"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        AlertHelper.ensureChannel(this)
        if (AlwaysListenPrefs(this).isEnabled()) {
            AlwaysListenService.start(this)
        }
        startSosIdListenerWhileOpen()
        // Keep as safety refresh, but UI is RTDB-first when online.
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
            lifecycleScope.launch(Dispatchers.IO) {
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
                            Toast.makeText(this@HomeActivity, getString(R.string.toast_sos_sent), Toast.LENGTH_SHORT).show()
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

                AlertHelper.startSosIncomingActivity(this, sender)
            }
            .launchIn(lifecycleScope)
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
        // Live collectors handle updates.
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}