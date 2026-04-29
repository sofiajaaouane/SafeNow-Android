package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.Item
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.ui.groups.CreateGroupViewModel
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class CreateGroupActivity : ComponentActivity() {


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val vm: CreateGroupViewModel by viewModels()


    private val onlineRepo by lazy {
        val isOnline = ConnectivityObserver(this).isOnlineFlow()
        OnlineRepository(DatabaseProvider.get(this), OnlineWriteGuard(isOnline), RtdbClient())
    }


    private lateinit var llNecessities: LinearLayout
    private lateinit var llMemberAvatars: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.create_group)


        val etGroupTitle       = findViewById<EditText>(R.id.etGroupTitle)
        val etGroupDescription = findViewById<EditText>(R.id.etGroupDescription)
        val etListItem         = findViewById<EditText>(R.id.etListItem)
        val btnAddItem         = findViewById<TextView>(R.id.btnAddItem)
        val btnCreateGroup     = findViewById<TextView>(R.id.btnCreateGroup)
        val btnReturn          = findViewById<ImageButton>(R.id.btnReturn)
        val btnAddMember       = findViewById<TextView>(R.id.btnAddGroupMember)
        llNecessities          = findViewById(R.id.llNecessities)
        llMemberAvatars        = findViewById(R.id.llCreateGroupMembersAvatars)


        btnReturn.setOnClickListener { finish() }

        vm.selectedMembers.observe(this) { members ->
            renderMemberAvatars(members)
        }

        btnAddMember.setOnClickListener {
            val userId = SessionManager.getCurrentUserId(this) ?: run {
                redirectToLogin()
                return@setOnClickListener
            }
            vm.loadFriends(userId) { friends ->
                val currentIds = vm.selectedMembers.value.orEmpty().map { it.idUser }.toSet()
                val available = friends.filter { it.idUser !in currentIds && it.idUser != userId }
                if (available.isEmpty()) {
                    Toast.makeText(this, getString(R.string.group_no_contact_to_add), Toast.LENGTH_SHORT).show()
                    return@loadFriends
                }
                showPickMembersDialog(available)
            }
        }


        btnAddItem.setOnClickListener {
            val item = etListItem.text.toString().trim()
            if (item.isEmpty()) {
                Toast.makeText(this, getString(R.string.create_group_toast_enter_item), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addNecessityRow(item)
            etListItem.setText("")
            etListItem.requestFocus()
        }


        etListItem.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null &&
                        event.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN)
            ) {
                val item = etListItem.text.toString().trim()
                if (item.isNotEmpty()) {
                    addNecessityRow(item)
                    etListItem.setText("")
                }
                true
            } else false
        }


        btnCreateGroup.setOnClickListener {
            val title       = etGroupTitle.text.toString().trim()
            val description = etGroupDescription.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(this, getString(R.string.create_group_toast_enter_title), Toast.LENGTH_SHORT).show()
                etGroupTitle.requestFocus()
                return@setOnClickListener
            }


            val userId = SessionManager.getCurrentUserId(this)
            if (userId == null) {
                redirectToLogin()
                return@setOnClickListener
            }

            val necessities = collectNecessities()

            scope.launch {
                // Verify that the user still exists in the database
                val userExists = withContext(Dispatchers.IO) {
                    DatabaseProvider.get(this@CreateGroupActivity).userDao().getById(userId) != null
                }

                if (!userExists) {
                    Toast.makeText(this@CreateGroupActivity, getString(R.string.common_session_expired_relogin), Toast.LENGTH_LONG).show()
                    SessionManager.clear(this@CreateGroupActivity)
                    redirectToLogin()
                    return@launch
                }

                val groupId = UUID.randomUUID().toString()


                val group = EmergencyGroup(
                    idGroup = groupId,
                    name = title,
                    description = description.ifEmpty { null },
                    sosGlobal = 1,
                    idAdmin = userId
                )

                val items = necessities.map { itemName ->
                    Item(
                        idItem = UUID.randomUUID().toString(),
                        type = "necessity",
                        name = itemName,
                        description = null,
                        idGroup = groupId
                    )
                }

                val result = withContext(Dispatchers.IO) {
                    runCatching { onlineRepo.createGroup(group, userId, items) }
                }

                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                    Toast.makeText(this@CreateGroupActivity, getString(R.string.common_offline), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val selectedIds = vm.selectedMembers.value.orEmpty().map { it.idUser }.distinct()
                    .filter { it.isNotBlank() && it != userId }
                if (selectedIds.isNotEmpty()) {
                    val addResult = withContext(Dispatchers.IO) {
                        runCatching {
                            selectedIds.forEach { mid ->
                                onlineRepo.addMember(groupId, mid, userId)
                            }
                        }
                    }
                    if (addResult.isFailure && addResult.exceptionOrNull() is OfflineWriteNotAllowed) {
                        Toast.makeText(this@CreateGroupActivity, getString(R.string.common_offline), Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                Toast.makeText(
                    this@CreateGroupActivity,
                    getString(R.string.group_created_success, title),
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
        }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun showPickMembersDialog(available: List<User>) {
        val names = available.map { "${it.prenom} ${it.nom}".trim() }.toTypedArray()
        val checked = BooleanArray(available.size) { false }
        AlertDialog.Builder(this)
            .setTitle(R.string.group_add_members_title)
            .setMultiChoiceItems(names, checked) { _, index, isChecked ->
                checked[index] = isChecked
            }
            .setPositiveButton(R.string.group_add) { _, _ ->
                val selected = available.filterIndexed { idx, _ -> checked[idx] }
                vm.addSelected(selected)
            }
            .setNegativeButton(R.string.dialog_delete_cancel, null)
            .show()
    }

    private fun renderMemberAvatars(members: List<User>) {
        llMemberAvatars.removeAllViews()
        val dp = resources.displayMetrics.density
        val size = (44 * dp).toInt()
        val marginEnd = (8 * dp).toInt()

        members.forEachIndexed { idx, u ->
            val params = LinearLayout.LayoutParams(size, size).apply { this.marginEnd = marginEnd }
            val tv = TextView(this)
            tv.layoutParams = params
            val initials = buildString {
                u.prenom.trim().firstOrNull()?.uppercaseChar()?.let { append(it) }
                u.nom.trim().firstOrNull()?.uppercaseChar()?.let { append(it) }
            }.ifEmpty { "M${idx + 1}" }
            tv.text = initials
            tv.textSize = 13f
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.gravity = Gravity.CENTER
            tv.setBackgroundResource(R.drawable.step_circle_active)
            tv.setOnLongClickListener {
                vm.removeSelected(u.idUser)
                true
            }
            llMemberAvatars.addView(tv)
        }
    }


    private fun addNecessityRow(item: String) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_necessity, llNecessities, false)

        val tvItem    = row.findViewById<TextView>(R.id.tvNecessityItem)
        val btnRemove = row.findViewById<ImageButton>(R.id.btnRemoveNecessity)

        tvItem.text = "• $item"

        btnRemove.setOnClickListener {
            llNecessities.removeView(row)
        }

        llNecessities.addView(row)
    }


    private fun collectNecessities(): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until llNecessities.childCount) {
            val row    = llNecessities.getChildAt(i) as LinearLayout
            val tvItem = row.findViewById<TextView>(R.id.tvNecessityItem)
            val text   = tvItem.text.toString().removePrefix("• ").trim()
            if (text.isNotEmpty()) result.add(text)
        }
        return result
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}