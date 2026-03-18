package com.example.safefnow2

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ContactsActivity : ComponentActivity() {

    private val searchQuery = MutableStateFlow("")
    private lateinit var adapter: ContactsAdapter
    private var allContacts: List<Contact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        val back = findViewById<ImageButton>(R.id.contactsBack)
        val search = findViewById<EditText>(R.id.contactsSearch)
        val list = findViewById<RecyclerView>(R.id.contactsList)
        val empty = findViewById<TextView>(R.id.contactsEmpty)
        val fabAdd = findViewById<ImageButton>(R.id.contactsFabAdd)

        back.setOnClickListener { finish() }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery.value = s?.toString()?.trim() ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        adapter = ContactsAdapter(
            onDelete = { contact -> showDeleteConfirm(contact) }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val db = DatabaseProvider.get(this)
        combine(db.contactDao().getAllFlow(), searchQuery) { contacts, query ->
            allContacts = contacts
            if (query.isEmpty()) contacts
            else contacts.filter { it.fullName.contains(query, ignoreCase = true) }
        }.onEach { filtered ->
            adapter.submitList(filtered)
            empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }.launchIn(lifecycleScope)

        fabAdd.setOnClickListener { showAddContactDialog() }
    }

    private fun showDeleteConfirm(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer le contact")
            .setMessage("Supprimer ${contact.fullName} ?")
            .setPositiveButton("Supprimer") { _, _ -> deleteContact(contact) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun deleteContact(contact: Contact) {
        lifecycleScope.launch {
            DatabaseProvider.get(this@ContactsActivity).contactDao().delete(contact)
            Toast.makeText(this@ContactsActivity, "Contact supprimé", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddContactDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val fullName = view.findViewById<EditText>(R.id.dialogContactFullName)
        val phone = view.findViewById<EditText>(R.id.dialogContactPhone)

        AlertDialog.Builder(this)
            .setTitle("Ajouter un contact")
            .setView(view)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = fullName.text.toString().trim()
                val num = phone.text.toString().trim()
                if (name.isNotEmpty() && num.isNotEmpty()) {
                    lifecycleScope.launch {
                        DatabaseProvider.get(this@ContactsActivity).contactDao()
                            .insert(Contact(fullName = name, phoneNumber = num))
                        Toast.makeText(this@ContactsActivity, "Contact ajouté", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}

// Adapter for contact list.
class ContactsAdapter(
    private val onDelete: (Contact) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.Holder>() {

    private var items: List<Contact> = emptyList()

    fun submitList(list: List<Contact>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return Holder(v, onDelete)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: View, onDelete: (Contact) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val fullName = itemView.findViewById<TextView>(R.id.itemContactFullName)
        private val phone = itemView.findViewById<TextView>(R.id.itemContactPhone)
        private val deleteBtn = itemView.findViewById<ImageButton>(R.id.itemContactDelete)

        fun bind(contact: Contact) {
            fullName.text = contact.fullName
            phone.text = contact.phoneNumber
            deleteBtn.setOnClickListener { onDelete(contact) }
        }
    }
}
