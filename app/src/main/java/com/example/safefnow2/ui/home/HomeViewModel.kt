package com.example.safefnow2.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.repository.GroupsOnlineFirstRepository
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.data.repository.SosInboxRepository
import com.example.safefnow2.data.repository.SosIncoming
import com.example.safefnow2.data.repository.UserOnlineFirstRepository
import com.example.safefnow2.ui.common.Event
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import com.example.safefnow2.data.local.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val userRepo = UserOnlineFirstRepository(app)
    private val groupsRepo = GroupsOnlineFirstRepository(app)
    private val sosInboxRepo = SosInboxRepository(app)

    private val _toast = MutableLiveData<Event<String>>()
    val toast: LiveData<Event<String>> = _toast

    fun user(userId: String): LiveData<User?> = userRepo.user(userId).asLiveData()

    fun myGroups(userId: String): LiveData<List<EmergencyGroup>> = groupsRepo.myGroups(userId).asLiveData()

    fun pendingInvitesCount(userId: String): LiveData<Int> = groupsRepo.pendingInvitesCount(userId).asLiveData()

    fun incomingSos(userId: String): LiveData<Event<SosIncoming>> {
        return sosInboxRepo.incoming(userId)
            .asLiveData()
            .mapToEvent()
    }

    fun sendGlobalSos(
        userId: String,
        senderName: String,
        senderLocation: String?,
        senderLat: Double?,
        senderLng: Double?,
    ) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    onlineRepo().sendGlobalSosToActiveGroups(
                        currentUserId = userId,
                        senderName = senderName,
                        senderLocation = senderLocation,
                        senderLat = senderLat,
                        senderLng = senderLng,
                    )
                }
            }
            if (result.isFailure) {
                val ex = result.exceptionOrNull()
                if (ex is OfflineWriteNotAllowed) {
                    _toast.value = Event("Please connect to the internet")
                } else {
                    _toast.value = Event(ex?.message ?: "SOS error")
                }
            } else {
                val count = result.getOrNull() ?: 0
                _toast.value = if (count <= 0) Event("No SOS-enabled group") else Event("SOS sent")
            }
        }
    }

    private fun onlineRepo(): OnlineRepository {
        val isOnline = ConnectivityObserver(getApplication()).isOnlineFlow()
        return OnlineRepository(DatabaseProvider.get(getApplication()), OnlineWriteGuard(isOnline), RtdbClient())
    }
}

private fun LiveData<SosIncoming>.mapToEvent(): LiveData<Event<SosIncoming>> {
    val out = MediatorLiveData<Event<SosIncoming>>()
    out.addSource(this) { v ->
        if (v.sosId.isBlank()) return@addSource
        out.value = Event(v)
    }
    return out
}

