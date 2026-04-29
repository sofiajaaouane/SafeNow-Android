package com.example.safefnow2.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.safefnow2.activity.ContactUiItem
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Amitier
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toUser
import com.example.safefnow2.data.repository.ContactsOnlineFirstRepository
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.data.sync.SyncRepository
import com.example.safefnow2.ui.common.Event
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val contactsRepo = ContactsOnlineFirstRepository(app)

    private val searchQuery = MutableStateFlow("")

    private val _toast = MutableLiveData<Event<String>>()
    val toast: LiveData<Event<String>> = _toast

    fun setQuery(q: String) {
        searchQuery.value = q.trim()
    }

    fun contacts(currentUserId: String): LiveData<List<ContactUiItem>> {
        return contactsRepo.acceptedContacts(currentUserId, searchQuery).asLiveData()
    }

    fun deleteContact(edge: Amitier, currentUserId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { onlineRepo().deleteFriendEdge(edge, currentUserId) }
            }
            if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                _toast.value = Event("Please connect to the internet")
            } else if (result.isFailure) {
                _toast.value = Event("Delete failed")
            } else {
                _toast.value = Event("Contact deleted")
            }
        }
    }

    fun searchUserByPhone(fullPhone: String, onResult: (User?) -> Unit) {
        viewModelScope.launch {
            val user = withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(getApplication())
                val local = db.userDao().getByPhone(fullPhone)
                if (local != null) return@withContext local

                val online = ConnectivityObserver(getApplication()).isOnlineFlow()
                if (!online.valueOrFalse()) return@withContext null

                val rtdb = RtdbClient()
                val digits = fullPhone.filter { it.isDigit() }
                val userId =
                    rtdb.get(RtdbPaths.userByPhone(fullPhone)).getValue(String::class.java)
                        ?: (if (digits.isNotEmpty()) rtdb.get(RtdbPaths.userByPhone(digits)).getValue(String::class.java) else null)
                        ?: return@withContext null
                val userSnap = rtdb.get(RtdbPaths.user(userId))
                val remoteUser = userSnap.toUser()
                if (remoteUser != null) {
                    db.userDao().insert(remoteUser)
                    runCatching { SyncRepository(db, rtdb).syncNow(currentSessionUserId()) }
                }
                remoteUser
            }
            onResult(user)
        }
    }

    fun sendFriendRequest(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            val db = DatabaseProvider.get(getApplication())
            val existing = withContext(Dispatchers.IO) { db.amitierDao().getById(currentUserId, targetUserId) }
            if (existing != null) {
                _toast.value = Event("Request already sent")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    onlineRepo().sendFriendRequest(
                        Amitier(idUser1 = currentUserId, idUser2 = targetUserId, status = "PENDING"),
                        currentUserId = currentUserId
                    )
                }
            }
            if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                _toast.value = Event("Please connect to the internet")
            } else if (result.isFailure) {
                _toast.value = Event("Failed to send request")
            } else {
                _toast.value = Event("Request sent")
            }
        }
    }

    private fun onlineRepo(): OnlineRepository {
        val isOnline = ConnectivityObserver(getApplication()).isOnlineFlow()
        return OnlineRepository(DatabaseProvider.get(getApplication()), OnlineWriteGuard(isOnline), RtdbClient())
    }

    private fun currentSessionUserId(): String {
        val prefs = getApplication<Application>().getSharedPreferences("safenow_session", 0)
        return prefs.getString("current_user_id", "") ?: ""
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<Boolean>.valueOrFalse(): Boolean {
    return runCatching { first() }.getOrDefault(false)
}

