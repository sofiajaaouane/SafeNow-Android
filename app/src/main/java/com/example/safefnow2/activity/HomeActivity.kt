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
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.data.sync.SyncScheduler
import com.example.safefnow2.ui.sos.SosUiEvent
import com.example.safefnow2.ui.sos.SosViewModel
import com.example.safefnow2.util.AlertHelper
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.DeviceIdProvider
import com.example.safefnow2.util.GroupPopupHelper
import com.example.safefnow2.util.OnlineWriteGuard
import com.example.safefnow2.util.SessionManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        AlertHelper.ensureChannel(this)
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
            val userId = SessionManager.getCurrentUserId(this) ?: return@setOnClickListener
            scope.launch {
                val user = withContext(Dispatchers.IO) {
                    DatabaseProvider.get(this@HomeActivity).userDao().getById(userId)
                }
                val name = user?.let { "${it.prenom} ${it.nom}".trim() }.orEmpty().ifEmpty { "SafeNow" }
                sosViewModel.sendSos(name)
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

        scope.launch {
            val user = withContext(Dispatchers.IO) {
                DatabaseProvider.get(this@HomeActivity).userDao().getById(userId)
            }
            user?.let {
                tvUserName.text = it.prenom
                val initials = buildString {
                    it.prenom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
                    it.nom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
                }
                if (initials.isNotEmpty()) tvInitials.text = initials
                sosViewModel.syncDeviceRegistration(
                    displayName = "${it.prenom} ${it.nom}".trim().ifEmpty { "SafeNow" },
                    phone = it.numTel,
                    appUserId = it.idUser
                )
                loadGroupsStories(llGroupsStories, userId)
            }
        }

        checkPendingNotifications(userId)
    }

    private fun checkPendingNotifications(userId: String) {
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                DatabaseProvider.get(this@HomeActivity)
                    .amitierDao().getPendingRequestsCount(userId)
            }
            findViewById<View>(R.id.badgeNotif)?.visibility =
                if (count > 0) View.VISIBLE else View.GONE
        }
    }

    private fun loadGroupsStories(container: LinearLayout?, userId: String) {
        if (container == null) return

        scope.launch {
            val groups = withContext(Dispatchers.IO) {
                val memberEntries = DatabaseProvider.get(this@HomeActivity)
                    .groupMemberDao().getByUserId(userId)
                memberEntries.mapNotNull { entry ->
                    DatabaseProvider.get(this@HomeActivity)
                        .emergencyGroupDao().getById(entry.idGroup)
                }
            }

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
                        scope    = scope,
                        group    = group,
                        userId   = userId,
                        onGroupDeleted = { loadGroupsStories(container, userId) }
                    )
                }

                container.addView(storyView)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val userId = SessionManager.getCurrentUserId(this) ?: return
        checkPendingNotifications(userId)
        loadGroupsStories(findViewById(R.id.llGroupsStories), userId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}