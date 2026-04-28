package com.example.safefnow2.ui.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.repository.GroupFriendsRepository
import com.example.safefnow2.data.repository.GroupMembersOnlineFirstRepository
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.ui.common.Event
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import com.example.safefnow2.data.local.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupMembersViewModel(app: Application) : AndroidViewModel(app) {
    private val membersRepo = GroupMembersOnlineFirstRepository(app)
    private val friendsRepo = GroupFriendsRepository(app)

    private val _toast = MutableLiveData<Event<String>>()
    val toast: LiveData<Event<String>> = _toast

    fun members(groupId: String): LiveData<List<MemberUi>> {
        return membersRepo.members(groupId)
            .asLiveData()
            .mapToUi()
    }

    fun loadAvailableFriends(groupId: String, currentUserId: String, onResult: (List<User>) -> Unit) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                friendsRepo.loadAvailableFriends(currentUserId = currentUserId, groupId = groupId)
            }
            onResult(list)
        }
    }

    fun addMembers(groupId: String, currentUserId: String, memberIds: List<String>) {
        if (memberIds.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val repo = onlineRepo()
                    memberIds.distinct().forEach { uid ->
                        if (uid.isNotBlank()) repo.addMember(groupId, uid, currentUserId)
                    }
                }
            }
            if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                _toast.value = Event("Connectez-vous a Internet")
            } else if (result.isFailure) {
                _toast.value = Event("Erreur ajout membre")
            } else {
                _toast.value = Event("${memberIds.size} membre(s) ajouté(s)")
            }
        }
    }

    fun removeMember(groupId: String, currentUserId: String, memberId: String, memberName: String?) {
        if (memberId.isBlank()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { onlineRepo().removeMember(groupId, memberId, currentUserId) }
            }
            if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                _toast.value = Event("Connectez-vous a Internet")
            } else if (result.isFailure) {
                _toast.value = Event("Erreur suppression membre")
            } else {
                _toast.value = Event("${memberName ?: "Membre"} retiré du groupe")
            }
        }
    }

    private fun onlineRepo(): OnlineRepository {
        val isOnline = ConnectivityObserver(getApplication()).isOnlineFlow()
        return OnlineRepository(DatabaseProvider.get(getApplication()), OnlineWriteGuard(isOnline), RtdbClient())
    }
}

data class MemberUi(
    val userId: String,
    val displayName: String,
    val initials: String,
)

private fun LiveData<List<User>>.mapToUi(): LiveData<List<MemberUi>> {
    val out = MediatorLiveData<List<MemberUi>>()
    out.addSource(this) { list ->
        out.value = list.mapIndexed { idx, u ->
            val display = "${u.prenom} ${u.nom}".trim().ifEmpty { u.idUser }
            val initials = buildString {
                u.prenom.trim().firstOrNull()?.uppercaseChar()?.let { append(it) }
                u.nom.trim().firstOrNull()?.uppercaseChar()?.let { append(it) }
            }.ifEmpty { "M${idx + 1}" }
            MemberUi(userId = u.idUser, displayName = display, initials = initials)
        }
    }
    return out
}

