package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.util.GroupPopupHelper
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyGroupsActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val emergencyGroupDao by lazy { DatabaseProvider.get(this).emergencyGroupDao() }
    private val groupMemberDao    by lazy { DatabaseProvider.get(this).groupMemberDao() }

    private var allGroups: List<EmergencyGroup> = emptyList()
    private lateinit var llGroups: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.my_groups)

        val btnReturn   = findViewById<ImageButton>(R.id.btnReturn)
        val etSearch    = findViewById<EditText>(R.id.etSearch)
        val btnNewGroup = findViewById<TextView>(R.id.btnNewGroup)
        llGroups        = findViewById(R.id.llGroups)

        btnReturn.setOnClickListener { finish() }
        btnNewGroup.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterGroups(s.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadGroups()
    }

    private fun loadGroups() {
        val userId = SessionManager.getCurrentUserId(this) ?: run { finish(); return }

        scope.launch {
            val groups = withContext(Dispatchers.IO) {
                val memberEntries = groupMemberDao.getByUserId(userId)
                memberEntries.mapNotNull { emergencyGroupDao.getById(it.idGroup) }
            }
            allGroups = groups
            displayGroups(groups)
        }
    }

    private suspend fun displayGroups(groups: List<EmergencyGroup>) {
        val groupsWithCount = withContext(Dispatchers.IO) {
            groups.map { group ->
                Pair(group, groupMemberDao.getByGroupId(group.idGroup).size)
            }
        }

        llGroups.removeAllViews()

        if (groupsWithCount.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text     = "Vous n'avez pas encore de groupes"
            tvEmpty.textSize = 14f
            tvEmpty.setTextColor(0xFFAAAAAA.toInt())
            tvEmpty.gravity  = Gravity.CENTER
            tvEmpty.setPadding(0, 48, 0, 0)
            llGroups.addView(tvEmpty)
            return
        }

        groupsWithCount.forEach { (group, memberCount) ->
            addGroupRow(group, memberCount)
        }
    }

    private fun addGroupRow(group: EmergencyGroup, memberCount: Int) {
        val userId = SessionManager.getCurrentUserId(this) ?: return

        val row = LayoutInflater.from(this).inflate(R.layout.item_group, llGroups, false)

        row.findViewById<TextView>(R.id.tvGroupAvatar).text  = group.name.take(2).uppercase()
        row.findViewById<TextView>(R.id.tvGroupName).text    = group.name
        row.findViewById<TextView>(R.id.tvMemberCount).text  =
            "$memberCount membre${if (memberCount > 1) "s" else ""}"

        row.setOnClickListener {
            GroupPopupHelper.show(
                activity = this,
                scope    = scope,
                group    = group,
                userId   = userId,
                onGroupDeleted = { loadGroups() }
            )
        }

        llGroups.addView(row)

        val divider = View(this)
        divider.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        )
        divider.setBackgroundColor(0xFFF0F0F0.toInt())
        llGroups.addView(divider)
    }

    private fun filterGroups(query: String) {
        val filtered = if (query.isEmpty()) allGroups
        else allGroups.filter { it.name.contains(query, ignoreCase = true) }
        scope.launch { displayGroups(filtered) }
    }

    override fun onResume() {
        super.onResume()
        loadGroups()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}