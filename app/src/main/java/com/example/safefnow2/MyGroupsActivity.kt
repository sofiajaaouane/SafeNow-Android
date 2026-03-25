package com.example.safefnow2

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.GroupMember
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyGroupsActivity : ComponentActivity() {

    // ── Coroutine scope ───────────────────────────────────────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ── DAOs ──────────────────────────────────────────────────────────────────
    private val emergencyGroupDao by lazy { DatabaseProvider.get(this).emergencyGroupDao() }
    private val groupMemberDao    by lazy { DatabaseProvider.get(this).groupMemberDao() }
    private val userDao           by lazy { DatabaseProvider.get(this).userDao() }

    // ── Full list for search filtering ────────────────────────────────────────
    private var allGroups: List<EmergencyGroup> = emptyList()

    // ── Dynamic list container ────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD ALL GROUPS OF CURRENT USER
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadGroups() {
        val userId = SessionManager.getCurrentUserId(this)
        if (userId == null) { finish(); return }

        scope.launch {
            val groups = withContext(Dispatchers.IO) {
                val memberEntries = groupMemberDao.getByUserId(userId)
                val groupIds = memberEntries.map { it.idGroup }
                groupIds.mapNotNull { emergencyGroupDao.getById(it) }
            }
            allGroups = groups
            displayGroups(groups)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DISPLAY GROUPS IN THE LIST
    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun displayGroups(groups: List<EmergencyGroup>) {
        val groupsWithCount = withContext(Dispatchers.IO) {
            groups.map { group ->
                val memberCount = groupMemberDao.getByGroupId(group.idGroup).size
                Pair(group, memberCount)
            }
        }

        llGroups.removeAllViews()

        if (groupsWithCount.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text     = "Vous n'avez pas encore de groupes"
            tvEmpty.textSize = 14f
            tvEmpty.setTextColor(0xFFAAAAAA.toInt())
            tvEmpty.gravity  = android.view.Gravity.CENTER
            tvEmpty.setPadding(0, 48, 0, 0)
            llGroups.addView(tvEmpty)
            return
        }

        groupsWithCount.forEach { (group, memberCount) ->
            addGroupRow(group, memberCount)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADD ONE GROUP ROW
    // ─────────────────────────────────────────────────────────────────────────
    private fun addGroupRow(group: EmergencyGroup, memberCount: Int) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_group, llGroups, false)

        val tvAvatar      = row.findViewById<TextView>(R.id.tvGroupAvatar)
        val tvGroupName   = row.findViewById<TextView>(R.id.tvGroupName)
        val tvMemberCount = row.findViewById<TextView>(R.id.tvMemberCount)

        tvAvatar.text      = group.name.take(2).uppercase()
        tvGroupName.text   = group.name
        tvMemberCount.text = "$memberCount membre${if (memberCount > 1) "s" else ""}"

        // Click on row → show popup
        row.setOnClickListener { showGroupPopup(group) }

        llGroups.addView(row)

        // Divider line
        val divider = android.view.View(this)
        val params  = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        divider.layoutParams = params
        divider.setBackgroundColor(0xFFF0F0F0.toInt())
        llGroups.addView(divider)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHOW GROUP POPUP WITH FULL BACKEND
    // ─────────────────────────────────────────────────────────────────────────
    private fun showGroupPopup(group: EmergencyGroup) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_group, null)

        val dialog = AlertDialog.Builder(this)
            .setView(popupView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // ── 1. Fill group name ────────────────────────────────────────────────
        popupView.findViewById<TextView>(R.id.tvPopupGroupName).text = group.name

        // ── 2. Fill group avatar initials ─────────────────────────────────────
        popupView.findViewById<TextView>(R.id.tvPopupGroupAvatar)?.text =
            group.name.take(2).uppercase()

        // ── 3. Load real members into the members row ─────────────────────────
        val membersContainer = popupView.findViewById<LinearLayout>(R.id.llPopupMembers)
        loadMembersInPopup(group.idGroup, membersContainer)

        // ── 4. Activate / deactivate toggle ───────────────────────────────────
        val switchActivate = popupView.findViewById<Switch>(R.id.switchActivateGroup)
        switchActivate.isChecked = group.sosGlobal == 1
        switchActivate.setOnCheckedChangeListener { _, isChecked ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    emergencyGroupDao.update(group.copy(sosGlobal = if (isChecked) 1 else 0))
                }
                Toast.makeText(
                    this@MyGroupsActivity,
                    if (isChecked) "Groupe activé" else "Groupe désactivé",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


        // ── 6. Add member button ──────────────────────────────────────────────
        popupView.findViewById<TextView>(R.id.btnAddMember).setOnClickListener {
            showAddMemberDialog(group.idGroup, membersContainer)
        }

        // ── 7. Delete group button ────────────────────────────────────────────
        popupView.findViewById<TextView>(R.id.btnDeleteGroup).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Supprimer le groupe")
                .setMessage("Voulez-vous vraiment supprimer \"${group.name}\" ?")
                .setPositiveButton("Supprimer") { _, _ ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // CASCADE in ForeignKey deletes members + items automatically
                            emergencyGroupDao.delete(group)
                        }
                        dialog.dismiss()
                        loadGroups()
                        Toast.makeText(this@MyGroupsActivity, "Groupe supprimé", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD REAL MEMBERS INTO POPUP MEMBERS ROW
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadMembersInPopup(groupId: String, container: LinearLayout?) {
        if (container == null) return

        scope.launch {
            val members = withContext(Dispatchers.IO) {
                groupMemberDao.getByGroupId(groupId)
            }

            // Keep only the + button (position 0), remove the rest
            if (container.childCount > 1) {
                container.removeViews(1, container.childCount - 1)
            }

            // Add one circle per real member showing their initials
            members.forEachIndexed { index, member ->
                val user = withContext(Dispatchers.IO) { userDao.getById(member.idUser) }

                val dp   = resources.displayMetrics.density
                val size = (44 * dp).toInt()

                val tvMember = TextView(this@MyGroupsActivity)
                val params   = LinearLayout.LayoutParams(size, size)
                params.marginEnd = (8 * dp).toInt()
                tvMember.layoutParams = params

                tvMember.text = if (user != null) {
                    "${user.prenom.firstOrNull()?.uppercaseChar() ?: ""}${user.nom.firstOrNull()?.uppercaseChar() ?: ""}"
                } else "M${index + 1}"

                tvMember.textSize = 13f
                tvMember.setTextColor(0xFFFFFFFF.toInt())
                tvMember.gravity = android.view.Gravity.CENTER
                tvMember.setBackgroundResource(R.drawable.step_circle_active)

                container.addView(tvMember)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADD MEMBER DIALOG — search by phone number
    // ─────────────────────────────────────────────────────────────────────────
    private fun showAddMemberDialog(groupId: String, membersContainer: LinearLayout?) {
        val input       = EditText(this)
        input.hint      = "Numéro de téléphone"
        input.inputType = android.text.InputType.TYPE_CLASS_PHONE
        input.setPadding(48, 32, 48, 32)

        AlertDialog.Builder(this)
            .setTitle("Ajouter un membre")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val phone = input.text.toString().trim()
                if (phone.isEmpty()) {
                    Toast.makeText(this, "Entrez un numéro", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                scope.launch {
                    val user = withContext(Dispatchers.IO) { userDao.getByPhone(phone) }

                    if (user == null) {
                        Toast.makeText(this@MyGroupsActivity,
                            "Aucun utilisateur trouvé avec ce numéro", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // Check if already a member
                    val existing = withContext(Dispatchers.IO) {
                        groupMemberDao.getById(groupId, user.idUser)
                    }
                    if (existing != null) {
                        Toast.makeText(this@MyGroupsActivity,
                            "${user.prenom} est déjà membre de ce groupe", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // Add the member
                    withContext(Dispatchers.IO) {
                        groupMemberDao.insert(GroupMember(idGroup = groupId, idUser = user.idUser))
                    }

                    Toast.makeText(this@MyGroupsActivity,
                        "${user.prenom} ${user.nom} ajouté au groupe", Toast.LENGTH_SHORT).show()

                    // Refresh members row in popup + group list
                    loadMembersInPopup(groupId, membersContainer)
                    loadGroups()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEARCH FILTER
    // ─────────────────────────────────────────────────────────────────────────
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