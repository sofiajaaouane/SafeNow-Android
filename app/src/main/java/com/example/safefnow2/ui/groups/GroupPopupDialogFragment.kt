package com.example.safefnow2.ui.groups

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupPopupDialogFragment : DialogFragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val vm: GroupMembersViewModel by lazy {
        ViewModelProvider(this)[GroupMembersViewModel::class.java]
    }

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
        val switchActivate = popupView.findViewById<Switch>(R.id.switchActivateGroup)

        tvName.text = groupName
        tvAvatar.text = groupName.take(2).uppercase()
        switchActivate.isChecked = sosGlobal == 1

        vm.members(groupId).observe(this) { members ->
            renderAvatars(avatarsContainer, members, groupId, currentUserId)
        }

        vm.toast.observe(this) { ev ->
            val msg = ev.getIfNotHandled() ?: return@observe
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        popupView.findViewById<TextView>(R.id.btnAddMember).setOnClickListener {
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

