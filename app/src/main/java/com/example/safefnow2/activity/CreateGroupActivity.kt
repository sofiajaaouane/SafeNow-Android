package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.EmergencyGroup
import com.example.safefnow2.data.local.entity.GroupMember
import com.example.safefnow2.data.local.entity.Item
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


    private val emergencyGroupDao by lazy { DatabaseProvider.get(this).emergencyGroupDao() }
    private val groupMemberDao    by lazy { DatabaseProvider.get(this).groupMemberDao() }
    private val itemDao           by lazy { DatabaseProvider.get(this).itemDao() }


    private lateinit var llNecessities: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.create_group)


        val etGroupTitle       = findViewById<EditText>(R.id.etGroupTitle)
        val etGroupDescription = findViewById<EditText>(R.id.etGroupDescription)
        val etListItem         = findViewById<EditText>(R.id.etListItem)
        val btnAddItem         = findViewById<TextView>(R.id.btnAddItem)
        val btnCreateGroup     = findViewById<TextView>(R.id.btnCreateGroup)
        val btnReturn          = findViewById<ImageButton>(R.id.btnReturn)
        llNecessities          = findViewById(R.id.llNecessities)


        btnReturn.setOnClickListener { finish() }


        btnAddItem.setOnClickListener {
            val item = etListItem.text.toString().trim()
            if (item.isEmpty()) {
                Toast.makeText(this, "Entrez un element avant d'ajouter", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Entrez un titre pour le groupe", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@CreateGroupActivity, "Session expirée, veuillez vous reconnecter", Toast.LENGTH_LONG).show()
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

                withContext(Dispatchers.IO) {


                    emergencyGroupDao.insert(group)


                    groupMemberDao.insert(
                        GroupMember(
                            idGroup = groupId,
                            idUser = userId
                        )
                    )


                    necessities.forEach { itemName ->
                        itemDao.insert(
                            Item(
                                idItem = UUID.randomUUID().toString(),
                                type = "necessity",
                                name = itemName,
                                description = null,
                                idGroup = groupId
                            )
                        )
                    }
                }

                Toast.makeText(
                    this@CreateGroupActivity,
                    "Groupe \"$title\" cree avec succes",
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