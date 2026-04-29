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
import androidx.activity.viewModels
import com.example.safefnow2.R
import com.example.safefnow2.util.SessionManager
import com.example.safefnow2.ui.groups.GroupPopupDialogFragment
import com.example.safefnow2.ui.groups.MyGroupsViewModel
import com.example.safefnow2.ui.groups.GroupRowUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyGroupsActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val vm: MyGroupsViewModel by viewModels()

    private var allGroups: List<GroupRowUi> = emptyList()
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

        observeGroups()
    }

    private fun observeGroups() {
        val userId = SessionManager.getCurrentUserId(this) ?: run { finish(); return }
        vm.myGroups(userId).observe(this) { groups ->
            allGroups = groups
            scope.launch { displayGroups(groups) }
        }
    }

    private suspend fun displayGroups(groups: List<GroupRowUi>) {
        val onlyGroups = groups.map { row -> row.group }

        llGroups.removeAllViews()

        if (onlyGroups.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text     = "Vous n'avez pas encore de groupes"
            tvEmpty.textSize = 14f
            tvEmpty.setTextColor(0xFFAAAAAA.toInt())
            tvEmpty.gravity  = Gravity.CENTER
            tvEmpty.setPadding(0, 48, 0, 0)
            llGroups.addView(tvEmpty)
            return
        }

        onlyGroups.forEach { group ->
            addGroupRow(group)
        }
    }

    private fun addGroupRow(group: com.example.safefnow2.data.local.entity.EmergencyGroup) {
        val userId = SessionManager.getCurrentUserId(this) ?: return

        val row = LayoutInflater.from(this).inflate(R.layout.item_group, llGroups, false)

        row.findViewById<TextView>(R.id.tvGroupAvatar).text = group.name.take(2).uppercase()
        row.findViewById<TextView>(R.id.tvGroupName).text = group.name

        row.setOnClickListener {
            GroupPopupDialogFragment
                .newInstance(
                    groupId = group.idGroup,
                    groupName = group.name,
                    adminId = group.idAdmin,
                    sosGlobal = group.sosGlobal,
                    currentUserId = userId,
                )
                .show(supportFragmentManager, "group_popup")
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
        else allGroups.filter { it.group.name.contains(query, ignoreCase = true) }
        scope.launch { displayGroups(filtered) }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}