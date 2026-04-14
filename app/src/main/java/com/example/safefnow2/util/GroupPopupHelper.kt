package com.example.safefnow2.util

import android.view.Gravity
import android.view.LayoutInflater
import android.content.Context
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.GroupMember
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GroupPopupHelper {

    private fun onlineRepo(activity: Context): OnlineRepository {
        val isOnline = ConnectivityObserver(activity).isOnlineFlow()
        return OnlineRepository(DatabaseProvider.get(activity), OnlineWriteGuard(isOnline), RtdbClient())
    }

    fun show(
        activity: AppCompatActivity,
        scope: CoroutineScope,
        group: EmergencyGroup,
        userId: String,
        onGroupDeleted: () -> Unit
    ) {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.popup_group, null)

        val dialog = AlertDialog.Builder(activity)
            .setView(popupView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Fill name and avatar
        popupView.findViewById<TextView>(R.id.tvPopupGroupName).text = group.name
        popupView.findViewById<TextView>(R.id.tvPopupGroupAvatar)?.text =
            group.name.take(2).uppercase()

        // Load members
        val membersContainer = popupView.findViewById<LinearLayout>(R.id.llPopupMembers)
        loadMembers(activity, scope, group.idGroup, userId, membersContainer)

        // Toggle switch
        val switchActivate = popupView.findViewById<Switch>(R.id.switchActivateGroup)
        switchActivate.isChecked = group.sosGlobal == 1
        switchActivate.setOnCheckedChangeListener { _, isChecked ->
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { onlineRepo(activity).setGroupActive(group, isChecked, userId) }
                }
                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                    Toast.makeText(activity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        activity,
                        if (isChecked) "Groupe activé" else "Groupe désactivé",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Add member button
        popupView.findViewById<TextView>(R.id.btnAddMember).setOnClickListener {
            showAddMemberDialog(activity, scope, group.idGroup, userId, membersContainer)
        }

        // Delete group button
        popupView.findViewById<TextView>(R.id.btnDeleteGroup).setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle("Supprimer le groupe")
                .setMessage("Voulez-vous vraiment supprimer \"${group.name}\" ?")
                .setPositiveButton("Supprimer") { _, _ ->
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { onlineRepo(activity).deleteGroup(group.idGroup, userId) }
                        }
                        if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                            Toast.makeText(activity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                        } else {
                            dialog.dismiss()
                            onGroupDeleted()
                            Toast.makeText(activity, "Groupe supprimé", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD MEMBERS — long-press to delete
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadMembers(
        activity: AppCompatActivity,
        scope: CoroutineScope,
        groupId: String,
        currentUserId: String,
        container: LinearLayout?
    ) {
        if (container == null) return

        scope.launch {
            val members = withContext(Dispatchers.IO) {
                DatabaseProvider.get(activity).groupMemberDao().getByGroupId(groupId)
            }


            if (container.childCount > 1) {
                container.removeViews(1, container.childCount - 1)
            }

            members.forEachIndexed { index, member ->
                val user = withContext(Dispatchers.IO) {
                    DatabaseProvider.get(activity).userDao().getById(member.idUser)
                }

                val dp     = activity.resources.displayMetrics.density
                val size   = (44 * dp).toInt()
                val params = LinearLayout.LayoutParams(size, size)
                params.marginEnd = (8 * dp).toInt()

                val tvMember = TextView(activity)
                tvMember.layoutParams = params
                tvMember.text = if (user != null) {
                    "${user.prenom.firstOrNull()?.uppercaseChar() ?: ""}${user.nom.firstOrNull()?.uppercaseChar() ?: ""}"
                } else "M${index + 1}"
                tvMember.textSize = 13f
                tvMember.setTextColor(0xFFFFFFFF.toInt())
                tvMember.gravity = Gravity.CENTER
                tvMember.setBackgroundResource(R.drawable.step_circle_active)

                // Long-press → delete member
                val memberName = if (user != null) "${user.prenom} ${user.nom}" else "ce membre"
                tvMember.setOnLongClickListener {
                    AlertDialog.Builder(activity)
                        .setTitle("Retirer le membre")
                        .setMessage("Voulez-vous retirer $memberName du groupe ?")
                        .setPositiveButton("Retirer") { _, _ ->
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { onlineRepo(activity).removeMember(groupId, member.idUser, currentUserId) }
                                }
                                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                                    Toast.makeText(activity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                                } else {
                                    loadMembers(activity, scope, groupId, currentUserId, container)
                                    Toast.makeText(
                                        activity,
                                        "$memberName retiré du groupe",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                    true
                }

                container.addView(tvMember)
            }
        }
    }


    private fun showAddMemberDialog(
        activity: AppCompatActivity,
        scope: CoroutineScope,
        groupId: String,
        userId: String,
        membersContainer: LinearLayout
    ) {
        scope.launch {
            val availableFriends = withContext(Dispatchers.IO) {
                val allFriends = DatabaseProvider.get(activity).amitierDao()
                    .getAcceptedFriends(userId)
                val existingIds = DatabaseProvider.get(activity).groupMemberDao()
                    .getByGroupId(groupId).map { it.idUser }
                allFriends.filter { it.idUser !in existingIds }
            }

            if (availableFriends.isEmpty()) {
                Toast.makeText(
                    activity,
                    "Aucun contact disponible à ajouter",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val names   = availableFriends.map { "${it.prenom} ${it.nom}" }.toTypedArray()
            val checked = BooleanArray(availableFriends.size) { false }

            AlertDialog.Builder(activity)
                .setTitle("Ajouter des membres")
                .setMultiChoiceItems(names, checked) { _, index, isChecked ->
                    checked[index] = isChecked
                }
                .setPositiveButton("Ajouter") { _, _ ->
                    scope.launch {
                        val selected = availableFriends.filterIndexed { i, _ -> checked[i] }
                        if (selected.isEmpty()) return@launch

                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                selected.forEach { friend ->
                                    onlineRepo(activity).addMember(groupId, friend.idUser, userId)
                                }
                            }
                        }
                        if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                            Toast.makeText(activity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                        } else {
                            loadMembers(activity, scope, groupId, userId, membersContainer)
                            Toast.makeText(
                                activity,
                                "${selected.size} membre(s) ajouté(s)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }
}