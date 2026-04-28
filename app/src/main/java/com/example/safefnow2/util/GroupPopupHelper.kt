package com.example.safefnow2.util

import android.Manifest
import android.view.Gravity
import android.view.LayoutInflater
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Alert
import com.example.safefnow2.data.local.entity.DeclarationAlert
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.GroupMember
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toUser
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.data.sync.SyncRepository
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

        popupView.findViewById<TextView>(R.id.tvPopupGroupName).text = group.name
        popupView.findViewById<TextView>(R.id.tvPopupGroupAvatar)?.text =
            group.name.take(2).uppercase()

        val avatarsContainer = popupView.findViewById<LinearLayout>(R.id.llPopupMembersAvatars)
        loadMembers(activity, scope, group.idGroup, userId, avatarsContainer)

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

        popupView.findViewById<TextView>(R.id.btnAddMember).setOnClickListener {
            showAddMemberDialog(activity, scope, group.idGroup, userId, avatarsContainer)
        }

        val btnSos = popupView.findViewById<android.view.View>(R.id.btnSOS)
        if (group.idAdmin != userId) {
            btnSos.visibility = android.view.View.GONE
        }
        btnSos.setOnClickListener {
            if (group.sosGlobal != 1) {
                Toast.makeText(activity, "Groupe désactivé", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
                val location = if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                    } catch (e: Exception) { null }
                } else null

                // On transforme les coordonnées en adresse réelle
                val locationStr = if (location != null) {
                    getReadableAddress(activity, location)
                } else "Position inconnue"

                val senderName = withContext(Dispatchers.IO) {
                    val me = DatabaseProvider.get(activity).userDao().getById(userId)
                    me?.let { "${it.prenom} ${it.nom}".trim() }?.ifEmpty { "SafeNow" } ?: "SafeNow"
                }
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        onlineRepo(activity).sendGroupSos(
                            groupId = group.idGroup,
                            senderName = senderName,
                            groupAdminId = group.idAdmin,
                            currentUserId = userId,
                            senderLocation = locationStr,
                            senderLat = location?.latitude,
                            senderLng = location?.longitude,
                        )
                    }
                }
                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                    Toast.makeText(activity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                } else if (result.isFailure && result.exceptionOrNull()?.message == "not_admin") {
                    Toast.makeText(activity, "Seul l'admin peut lancer SOS", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "SOS envoyé au groupe", Toast.LENGTH_SHORT).show()
                }
            }
        }

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

    private suspend fun getReadableAddress(context: Context, location: Location): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
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

    private fun loadMembers(
        activity: AppCompatActivity,
        scope: CoroutineScope,
        groupId: String,
        currentUserId: String,
        avatarsContainer: LinearLayout?
    ) {
        if (avatarsContainer == null) return

        scope.launch {
            val items = withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(activity)
                var members = db.groupMemberDao().getByGroupId(groupId)
                if (members.isEmpty()) {
                    runCatching { SyncRepository(db, RtdbClient()).syncNow(currentUserId) }
                    members = db.groupMemberDao().getByGroupId(groupId)
                }
                val seenIds = linkedSetOf<String>()
                members.mapNotNullIndexed { idx, member ->
                    val memberId = member.idUser.trim()
                    if (memberId.isEmpty()) return@mapNotNullIndexed null
                    if (!seenIds.add(memberId)) return@mapNotNullIndexed null
                    val user = db.userDao().getById(memberId)
                    MemberItem(
                        memberId = memberId,
                        displayName = user?.let { "${it.prenom} ${it.nom}".trim() }?.ifEmpty { null },
                        initials = initialsFrom(user?.prenom, user?.nom, fallbackIndex = idx),
                    )
                }
            }

            avatarsContainer.removeAllViews()

            val dp = activity.resources.displayMetrics.density
            val size = (44 * dp).toInt()
            val marginEnd = (8 * dp).toInt()

            items.forEach { item ->
                val params = LinearLayout.LayoutParams(size, size)
                params.marginEnd = marginEnd

                val tvMember = TextView(activity)
                tvMember.layoutParams = params
                tvMember.text = item.initials
                tvMember.textSize = 13f
                tvMember.setTextColor(0xFFFFFFFF.toInt())
                tvMember.gravity = Gravity.CENTER
                tvMember.setBackgroundResource(R.drawable.step_circle_active)

                val memberName = item.displayName ?: "ce membre"
                tvMember.setOnLongClickListener {
                    AlertDialog.Builder(activity)
                        .setTitle("Retirer le membre")
                        .setMessage("Voulez-vous retirer $memberName du groupe ?")
                        .setPositiveButton("Retirer") { _, _ ->
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        onlineRepo(activity).removeMember(groupId, item.memberId, currentUserId)
                                    }
                                }
                                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                                    Toast.makeText(activity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                                } else {
                                    loadMembers(activity, scope, groupId, currentUserId, avatarsContainer)
                                    Toast.makeText(activity, "$memberName retiré du groupe", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                    true
                }

                avatarsContainer.addView(tvMember)
            }
        }
    }

    private data class MemberItem(
        val memberId: String,
        val displayName: String?,
        val initials: String,
    )

    private fun initialsFrom(prenom: String?, nom: String?, fallbackIndex: Int): String {
        val p = prenom?.trim().orEmpty()
        val n = nom?.trim().orEmpty()
        val first = p.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        val second = n.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        val out = (first + second).trim()
        return if (out.isNotEmpty()) out else "M${fallbackIndex + 1}"
    }

    private inline fun <T, R : Any> List<T>.mapNotNullIndexed(transform: (index: Int, item: T) -> R?): List<R> {
        val out = ArrayList<R>(size)
        for (i in indices) {
            val v = transform(i, this[i]) ?: continue
            out.add(v)
        }
        return out
    }


    private fun showAddMemberDialog(
        activity: AppCompatActivity,
        scope: CoroutineScope,
        groupId: String,
        userId: String,
        avatarsContainer: LinearLayout
    ) {
        scope.launch {
            val availableFriends = withContext(Dispatchers.IO) {
                loadAvailableFriends(activity, userId, groupId)
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
                                val repo = onlineRepo(activity)
                                selected.forEach { friend ->
                                    repo.addMember(groupId, friend.idUser, userId)
                                }
                            }
                        }
                        if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                            Toast.makeText(activity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                        } else {
                            loadMembers(activity, scope, groupId, userId, avatarsContainer)
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

    private suspend fun loadAvailableFriends(
        activity: AppCompatActivity,
        currentUserId: String,
        groupId: String
    ): List<com.example.safefnow2.data.local.entity.User> {
        val db = DatabaseProvider.get(activity)
        val tryOnline = runCatching {
            val rtdb = RtdbClient()
            val outSnap = rtdb.get(RtdbPaths.friendshipOut(currentUserId))
            val inSnap = rtdb.get(RtdbPaths.friendshipIn(currentUserId))
            val membersSnap = rtdb.get(RtdbPaths.groupMembers(groupId))

            val existingMemberIds = membersSnap.children.mapNotNull { it.key }.toSet()

            fun collectAcceptedIds(snap: com.google.firebase.database.DataSnapshot): Set<String> {
                val ids = linkedSetOf<String>()
                snap.children.forEach { child ->
                    val otherId = child.key ?: return@forEach
                    val status = child.child("status").getValue(String::class.java) ?: "PENDING"
                    if (status == "ACCEPTED") ids.add(otherId)
                }
                return ids
            }

            val accepted = linkedSetOf<String>()
            accepted.addAll(collectAcceptedIds(outSnap))
            accepted.addAll(collectAcceptedIds(inSnap))

            val users = accepted
                .filter { it !in existingMemberIds && it != currentUserId }
                .mapNotNull { id ->
                    val uSnap = rtdb.get(RtdbPaths.user(id))
                    uSnap.toUser()
                }

            users.forEach { u -> db.userDao().insert(u) }
            users
        }

        if (tryOnline.isSuccess) return tryOnline.getOrThrow()

        val allFriends = db.amitierDao().getAcceptedFriends(currentUserId)
        val existingIds = db.groupMemberDao().getByGroupId(groupId).map { it.idUser }.toSet()
        return allFriends.filter { it.idUser !in existingIds }
    }
}
