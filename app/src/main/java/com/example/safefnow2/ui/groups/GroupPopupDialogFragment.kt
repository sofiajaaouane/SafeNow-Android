package com.example.safefnow2.ui.groups

import android.Manifest
import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.content.pm.PackageManager
import android.location.Location
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import com.example.safefnow2.util.AlertHistoryHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GroupPopupDialogFragment : DialogFragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val vm: GroupMembersViewModel by lazy {
        ViewModelProvider(this)[GroupMembersViewModel::class.java]
    }
    private var latestMembers: List<MemberUi> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val groupId = requireArguments().getString(ARG_GROUP_ID).orEmpty()
        val groupName = requireArguments().getString(ARG_GROUP_NAME).orEmpty()
        val adminId = requireArguments().getString(ARG_GROUP_ADMIN_ID).orEmpty()
        val currentUserId = requireArguments().getString(ARG_CURRENT_USER_ID).orEmpty()
        val sosGlobal = requireArguments().getInt(ARG_GROUP_SOS_GLOBAL, 1)

        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_group, null)
        val tvName = popupView.findViewById<TextView>(R.id.tvPopupGroupName)
        val tvAvatar = popupView.findViewById<TextView>(R.id.tvPopupGroupAvatar)
        val avatarsContainer = popupView.findViewById<LinearLayout>(R.id.llPopupMembersAvatars)
        val tvLoading = popupView.findViewById<TextView>(R.id.tvPopupMembersLoading)
        val switchActivate = popupView.findViewById<Switch>(R.id.switchActivateGroup)

        tvName.text = groupName
        tvAvatar.text = groupName.take(2).uppercase()
        switchActivate.isChecked = sosGlobal == 1

        tvLoading.visibility = android.view.View.VISIBLE
        vm.members(groupId).observe(this) { members ->
            tvLoading.visibility = android.view.View.GONE
            latestMembers = members
            renderAvatars(avatarsContainer, members, groupId, currentUserId)
        }

        vm.toast.observe(this) { ev ->
            val msg = ev.getIfNotHandled() ?: return@observe
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        popupView.findViewById<TextView>(R.id.btnAddMember).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Membres")
                .setItems(arrayOf("Ajouter des membres", "Retirer des membres")) { _, which ->
                    if (which == 0) {
                        showAddMembersDialog(groupId, currentUserId)
                    } else {
                        showRemoveMembersDialog(groupId, adminId, currentUserId)
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        switchActivate.setOnCheckedChangeListener { _, isChecked ->
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        onlineRepo().setGroupActive(
                            group = DatabaseProvider.get(requireContext()).emergencyGroupDao().getById(groupId)
                                ?: return@runCatching,
                            active = isChecked,
                            currentUserId = currentUserId
                        )
                    }
                }
                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                    Toast.makeText(requireContext(), "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(popupView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnSos = popupView.findViewById<android.view.View>(R.id.btnSOS)
        if (adminId.isNotEmpty() && currentUserId != adminId) {
            btnSos.visibility = android.view.View.GONE
        }
        btnSos.setOnClickListener {
            if (sosGlobal != 1) {
                Toast.makeText(requireContext(), "Groupe désactivé", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (adminId.isNotEmpty() && currentUserId != adminId) {
                Toast.makeText(requireContext(), "Seul l'admin peut lancer SOS", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
                val location: Location? =
                    if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        runCatching {
                            fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                CancellationTokenSource().token
                            ).await()
                        }.getOrNull()
                    } else null

                val locationStr = if (location != null) {
                    AlertHistoryHelper.getReadableAddress(requireContext(), location)
                } else {
                    "Position inconnue"
                }

                val senderName = withContext(Dispatchers.IO) {
                    val me = DatabaseProvider.get(requireContext()).userDao().getById(currentUserId)
                    me?.let { "${it.prenom} ${it.nom}".trim() }?.ifEmpty { "SafeNow" } ?: "SafeNow"
                }

                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        onlineRepo().sendGroupSos(
                            groupId = groupId,
                            senderName = senderName,
                            groupAdminId = adminId,
                            currentUserId = currentUserId,
                            senderLocation = locationStr,
                            senderLat = location?.latitude,
                            senderLng = location?.longitude,
                        )
                    }
                }

                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                    Toast.makeText(requireContext(), "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                } else if (result.isFailure && result.exceptionOrNull()?.message == "not_admin") {
                    Toast.makeText(requireContext(), "Seul l'admin peut lancer SOS", Toast.LENGTH_SHORT).show()
                } else if (result.isFailure) {
                    Toast.makeText(requireContext(), "Erreur SOS", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "SOS envoyé au groupe", Toast.LENGTH_SHORT).show()
                }
            }
        }

        popupView.findViewById<TextView>(R.id.btnDeleteGroup).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Supprimer le groupe")
                .setMessage("Voulez-vous vraiment supprimer \"$groupName\" ?")
                .setPositiveButton("Supprimer") { _, _ ->
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { onlineRepo().deleteGroup(groupId, currentUserId) }
                        }
                        if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                            Toast.makeText(requireContext(), "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                        } else {
                            dialog.dismiss()
                            Toast.makeText(requireContext(), "Groupe supprimé", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        return dialog
    }

    private fun showAddMembersDialog(groupId: String, currentUserId: String) {
        vm.loadAvailableFriends(groupId, currentUserId) { available ->
            if (available.isEmpty()) {
                Toast.makeText(requireContext(), "Aucun contact disponible à ajouter", Toast.LENGTH_SHORT).show()
                return@loadAvailableFriends
            }
            val names = available.map { "${it.prenom} ${it.nom}".trim() }.toTypedArray()
            val checked = BooleanArray(available.size) { false }
            AlertDialog.Builder(requireContext())
                .setTitle("Ajouter des membres")
                .setMultiChoiceItems(names, checked) { _, index, isChecked ->
                    checked[index] = isChecked
                }
                .setPositiveButton("Ajouter") { _, _ ->
                    val selectedIds = available.filterIndexed { idx, _ -> checked[idx] }.map { it.idUser }
                    vm.addMembers(groupId, currentUserId, selectedIds)
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun showRemoveMembersDialog(groupId: String, adminId: String, currentUserId: String) {
        val removable = latestMembers.filter { it.userId != adminId && it.userId != currentUserId }
        if (removable.isEmpty()) {
            Toast.makeText(requireContext(), "Aucun membre à retirer", Toast.LENGTH_SHORT).show()
            return
        }
        val names = removable.map { it.displayName }.toTypedArray()
        val checked = BooleanArray(removable.size) { false }
        AlertDialog.Builder(requireContext())
            .setTitle("Retirer des membres")
            .setMultiChoiceItems(names, checked) { _, index, isChecked ->
                checked[index] = isChecked
            }
            .setPositiveButton("Retirer") { _, _ ->
                val selected = removable.filterIndexed { idx, _ -> checked[idx] }
                selected.forEach { m ->
                    vm.removeMember(groupId, currentUserId, m.userId, m.displayName)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun renderAvatars(
        container: LinearLayout,
        members: List<MemberUi>,
        groupId: String,
        currentUserId: String,
    ) {
        container.removeAllViews()
        val dp = resources.displayMetrics.density
        val size = (44 * dp).toInt()
        val marginEnd = (8 * dp).toInt()

        members.forEachIndexed { _, m ->
            val params = LinearLayout.LayoutParams(size, size).apply { this.marginEnd = marginEnd }
            val tv = TextView(requireContext())
            tv.layoutParams = params
            tv.text = m.initials
            tv.textSize = 13f
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.gravity = Gravity.CENTER
            tv.setBackgroundResource(R.drawable.step_circle_active)
            tv.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Retirer le membre")
                    .setMessage("Voulez-vous retirer ${m.displayName} du groupe ?")
                    .setPositiveButton("Retirer") { _, _ ->
                        vm.removeMember(groupId, currentUserId, m.userId, m.displayName)
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
                true
            }
            container.addView(tv)
        }
    }

    private fun onlineRepo(): OnlineRepository {
        val isOnline = ConnectivityObserver(requireContext()).isOnlineFlow()
        return OnlineRepository(DatabaseProvider.get(requireContext()), OnlineWriteGuard(isOnline), RtdbClient())
    }

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        private const val ARG_GROUP_NAME = "group_name"
        private const val ARG_GROUP_ADMIN_ID = "group_admin_id"
        private const val ARG_GROUP_SOS_GLOBAL = "group_sos_global"
        private const val ARG_CURRENT_USER_ID = "current_user_id"

        fun newInstance(
            groupId: String,
            groupName: String,
            adminId: String,
            sosGlobal: Int,
            currentUserId: String,
        ): GroupPopupDialogFragment {
            return GroupPopupDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_ID, groupId)
                    putString(ARG_GROUP_NAME, groupName)
                    putString(ARG_GROUP_ADMIN_ID, adminId)
                    putInt(ARG_GROUP_SOS_GLOBAL, sosGlobal)
                    putString(ARG_CURRENT_USER_ID, currentUserId)
                }
            }
        }
    }
}

