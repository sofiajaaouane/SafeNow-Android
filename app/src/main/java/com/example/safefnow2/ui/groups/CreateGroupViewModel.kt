package com.example.safefnow2.ui.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.repository.GroupFriendsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateGroupViewModel(app: Application) : AndroidViewModel(app) {
    private val friendsRepo = GroupFriendsRepository(app)

    private val _selectedMembers = MutableLiveData<List<User>>(emptyList())
    val selectedMembers: LiveData<List<User>> = _selectedMembers

    fun loadFriends(currentUserId: String, onResult: (List<User>) -> Unit) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                friendsRepo.loadAcceptedFriends(currentUserId)
            }
            onResult(list)
        }
    }

    fun addSelected(users: List<User>) {
        if (users.isEmpty()) return
        val current = _selectedMembers.value.orEmpty()
        val seen = linkedSetOf<String>()
        val merged = ArrayList<User>()
        (current + users).forEach { u ->
            val id = u.idUser.trim()
            if (id.isEmpty()) return@forEach
            if (!seen.add(id)) return@forEach
            merged.add(u)
        }
        _selectedMembers.value = merged
    }

    fun removeSelected(userId: String) {
        val id = userId.trim()
        if (id.isEmpty()) return
        _selectedMembers.value = _selectedMembers.value.orEmpty().filter { it.idUser != id }
    }
}

