package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import com.example.safefnow2.R
import com.example.safefnow2.ProfileActivity
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.util.AlertHelper
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        AlertHelper.ensureChannel(this)

        // ── Views ─────────────────────────────────────────────────────────────
        val tvUserName      = findViewById<TextView>(R.id.tvHomeUserName)
        val tvInitials      = findViewById<TextView>(R.id.tvHomeAvatarInitials)
        val llGroupsStories = findViewById<LinearLayout>(R.id.llGroupsStories)

        findViewById<LinearLayout>(R.id.navContacts).setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        // ── Avatar cliquable dans la top bar → ProfileActivity ───────────────
        val avatarLayout = findViewById<FrameLayout>(R.id.avatarContainer)
        avatarLayout?.setOnClickListener {
        // ── Avatar → ProfileActivity ──────────────────────────────────────────
        findViewById<FrameLayout>(R.id.avatarContainer)?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // ── Profil nav → ProfileActivity ──────────────────────────────────────
        val navProfil = findViewById<LinearLayout>(R.id.navProfil)
        navProfil?.setOnClickListener {

        // ── Groupes nav → MyGroupsActivity ────────────────────────────────────
        findViewById<LinearLayout>(R.id.navGroupes)?.setOnClickListener {
            startActivity(Intent(this, MyGroupsActivity::class.java))
        }

        // ── Ajouter button → CreateGroupActivity ──────────────────────────────
        findViewById<FrameLayout>(R.id.btnAddGroup)?.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }

        // ── Load user data + real groups ──────────────────────────────────────
        val navAlertes = findViewById<LinearLayout>(R.id.navAlertes)
        navAlertes?.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        val userId = SessionManager.getCurrentUserId(this) ?: return

        scope.launch {
            val user = withContext(Dispatchers.IO) {
                DatabaseProvider.get(this@HomeActivity).userDao().getById(userId)
            }
            user?.let {
                // Affiche le prénom dans le "Bonjour,"
                tvUserName.text = it.prenom

                // Génère les initiales pour l'avatar
                val initials = buildString {
                    it.prenom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
                    it.nom.firstOrNull()?.uppercaseChar()?.let { c -> append(c) }
                }
                if (initials.isNotEmpty()) tvInitials.text = initials
                loadGroupsStories(llGroupsStories, userId)
            }
        }

        checkPendingNotifications(userId)
    }



    private fun checkPendingNotifications(userId: String) {
        scope.launch {
            val count =
                    withContext(Dispatchers.IO) {
                        DatabaseProvider.get(this@HomeActivity).amitierDao().getPendingRequestsCount(
                                userId
                        )
                    }

            val badgeNotif = findViewById<View>(R.id.badgeNotif)
            badgeNotif?.visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD REAL GROUPS IN THE STORIES ROW
    // ─────────────────────────────────────────────────────────────────────────
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

            // Keep only "Ajouter" button (first child), remove the rest
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

                // Click on group circle → show popup
                storyView.setOnClickListener {
                    showGroupPopup(group, container, userId)
                }

                container.addView(storyView)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHOW GROUP POPUP
    // ─────────────────────────────────────────────────────────────────────────
    private fun showGroupPopup(
        group: EmergencyGroup,
        storiesContainer: LinearLayout,
        userId: String
    ) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_group, null)

        val dialog = AlertDialog.Builder(this)
            .setView(popupView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // ── 1. Fill group name and avatar ─────────────────────────────────────
        popupView.findViewById<TextView>(R.id.tvPopupGroupName).text = group.name
        popupView.findViewById<TextView>(R.id.tvPopupGroupAvatar)?.text =
            group.name.take(2).uppercase()

        // ── 2. Load real members ──────────────────────────────────────────────
        val membersContainer = popupView.findViewById<LinearLayout>(R.id.llPopupMembers)
        loadMembersInPopup(group.idGroup, membersContainer)

        // ── 3. Toggle switch ──────────────────────────────────────────────────
        val switchActivate = popupView.findViewById<Switch>(R.id.switchActivateGroup)
        switchActivate.isChecked = group.sosGlobal == 1
        switchActivate.setOnCheckedChangeListener { _, isChecked ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    DatabaseProvider.get(this@HomeActivity)
                        .emergencyGroupDao()
                        .update(group.copy(sosGlobal = if (isChecked) 1 else 0))
                }
                Toast.makeText(
                    this@HomeActivity,
                    if (isChecked) "Groupe activé" else "Groupe désactivé",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // ── 4. SOS button — no action for now ────────────────────────────────

        // ── 5. Add member — no action for now ────────────────────────────────

        // ── 6. Delete group ───────────────────────────────────────────────────
        popupView.findViewById<TextView>(R.id.btnDeleteGroup).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Supprimer le groupe")
                .setMessage("Voulez-vous vraiment supprimer \"${group.name}\" ?")
                .setPositiveButton("Supprimer") { _, _ ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            DatabaseProvider.get(this@HomeActivity)
                                .emergencyGroupDao().delete(group)
                        }
                        dialog.dismiss()
                        loadGroupsStories(storiesContainer, userId)
                        Toast.makeText(
                            this@HomeActivity,
                            "Groupe supprimé",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD REAL MEMBERS INTO POPUP
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadMembersInPopup(groupId: String, container: LinearLayout?) {
        if (container == null) return

        scope.launch {
            val members = withContext(Dispatchers.IO) {
                DatabaseProvider.get(this@HomeActivity)
                    .groupMemberDao().getByGroupId(groupId)
            }

            // Keep only + button (position 0), remove the rest
            if (container.childCount > 1) {
                container.removeViews(1, container.childCount - 1)
            }

            members.forEachIndexed { index, member ->
                val user = withContext(Dispatchers.IO) {
                    DatabaseProvider.get(this@HomeActivity).userDao().getById(member.idUser)
                }

                val dp     = resources.displayMetrics.density
                val size   = (44 * dp).toInt()
                val params = LinearLayout.LayoutParams(size, size)
                params.marginEnd = (8 * dp).toInt()

                val tvMember = TextView(this@HomeActivity)
                tvMember.layoutParams = params
                tvMember.text = if (user != null) {
                    "${user.prenom.firstOrNull()?.uppercaseChar() ?: ""}${user.nom.firstOrNull()?.uppercaseChar() ?: ""}"
                } else "M${index + 1}"
                tvMember.textSize = 13f
                tvMember.setTextColor(0xFFFFFFFF.toInt())
                tvMember.gravity = android.view.Gravity.CENTER
                tvMember.setBackgroundResource(R.drawable.step_circle_active)

                container.addView(tvMember)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        val userId = SessionManager.getCurrentUserId(this) ?: return
        checkPendingNotifications(userId)
        val llGroupsStories = findViewById<LinearLayout>(R.id.llGroupsStories)
        loadGroupsStories(llGroupsStories, userId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}