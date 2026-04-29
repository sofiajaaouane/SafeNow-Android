package com.example.safefnow2.ui.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.dao.GroupMemberCount
import com.example.safefnow2.data.repository.GroupsOnlineFirstRepository

class MyGroupsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GroupsOnlineFirstRepository(app)
    private val db = DatabaseProvider.get(app)

    fun myGroups(userId: String): LiveData<List<GroupRowUi>> {
        val source = repo.myGroups(userId).asLiveData()
        val countsSource = db.groupMemberDao().getCountsByGroupFlow().asLiveData()
        val out = MediatorLiveData<List<GroupRowUi>>()
        var latestGroups: List<EmergencyGroup> = emptyList()
        var latestCounts: Map<String, Int> = emptyMap()

        fun publish() {
            val groups = latestGroups
            if (groups.isEmpty()) {
                out.value = emptyList()
                return
            }
            out.value =
                groups.map { g ->
                    GroupRowUi(group = g, memberCount = latestCounts[g.idGroup] ?: 0)
                }
        }

        out.addSource(source) { groups ->
            latestGroups = groups
            publish()
        }
        out.addSource(countsSource) { counts: List<GroupMemberCount> ->
            latestCounts = counts.associate { it.idGroup to it.cnt }
            publish()
        }
        return out
    }
}

data class GroupRowUi(
    val group: EmergencyGroup,
    val memberCount: Int,
)

