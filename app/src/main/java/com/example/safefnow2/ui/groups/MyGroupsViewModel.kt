package com.example.safefnow2.ui.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.repository.GroupsOnlineFirstRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyGroupsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GroupsOnlineFirstRepository(app)
    private val db = DatabaseProvider.get(app)

    fun myGroups(userId: String): LiveData<List<GroupRowUi>> {
        val source = repo.myGroups(userId).asLiveData()
        val out = MediatorLiveData<List<GroupRowUi>>()
        out.addSource(source) { groups ->
            viewModelScope.launch {
                val rows = withContext(Dispatchers.IO) {
                    groups.map { g ->
                        val count = db.groupMemberDao().getByGroupId(g.idGroup).size
                        GroupRowUi(group = g, memberCount = count)
                    }
                }
                out.value = rows
            }
        }
        return out
    }
}

data class GroupRowUi(
    val group: EmergencyGroup,
    val memberCount: Int,
)

