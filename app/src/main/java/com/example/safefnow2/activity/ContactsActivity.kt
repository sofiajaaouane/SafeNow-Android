package com.example.safefnow2.activity

import android.content.Intent
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Amitier
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.remote.toUser
import com.example.safefnow2.data.sync.SyncRepository
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.ui.contacts.ContactsViewModel
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactUiItem(
        val amitierIdUser1: String,
        val amitierIdUser2: String,
        val contactUserId: String,
        val prenom: String,
        val nom: String,
        val fullName: String,
        val phoneNumber: String
)

class ContactsActivity : ComponentActivity() {

    private val vm: ContactsViewModel by viewModels()
    private lateinit var adapter: ContactsAdapter
    private val onlineRepo by lazy {
        val isOnline = ConnectivityObserver(this).isOnlineFlow()
        OnlineRepository(DatabaseProvider.get(this), OnlineWriteGuard(isOnline), RtdbClient())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        val back = findViewById<ImageButton>(R.id.contactsBack)
        val search = findViewById<EditText>(R.id.contactsSearch)
        val list = findViewById<RecyclerView>(R.id.contactsList)
        val empty = findViewById<TextView>(R.id.contactsEmpty)
        val fabAdd = findViewById<ImageButton>(R.id.contactsFabAdd)

        back.setOnClickListener { finish() }

        search.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        vm.setQuery(s?.toString() ?: "")
                    }
                    override fun afterTextChanged(s: Editable?) {}
                }
        )

        adapter =
                ContactsAdapter(
                        onDelete = { item -> showDeleteConfirm(item) },
                        onContactClick = { item -> openContactDetails(item) }
                )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val currentUserId = SessionManager.getCurrentUserId(this) ?: return
        vm.contacts(currentUserId).observe(this) { filtered ->
            adapter.submitList(filtered.sortedBy { it.fullName })
            empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
        vm.toast.observe(this) { ev ->
            val msg = ev.getIfNotHandled() ?: return@observe
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        fabAdd.setOnClickListener { showAddContactDialog() }
    }

    private fun openContactDetails(item: ContactUiItem) {
        val intent =
                Intent(this, ContactDetailsActivity::class.java).apply {
                    putExtra(ContactDetailsActivity.EXTRA_CONTACT_USER_ID, item.contactUserId)
                }
        startActivity(intent)
    }

    private fun showDeleteConfirm(item: ContactUiItem) {
        AlertDialog.Builder(this)
                .setTitle(R.string.contact_delete_title)
                .setMessage(getString(R.string.contact_delete_message, item.fullName))
                .setPositiveButton(R.string.contact_delete_confirm) { _, _ -> deleteAmitier(item) }
                .setNegativeButton(R.string.contact_delete_cancel, null)
                .show()
    }

    private fun deleteAmitier(item: ContactUiItem) {
        val currentUserId = SessionManager.getCurrentUserId(this@ContactsActivity).orEmpty()
        val edge = Amitier(
            idUser1 = item.amitierIdUser1,
            idUser2 = item.amitierIdUser2,
            status = "PENDING"
        )
        vm.deleteContact(edge, currentUserId)
    }

    private fun showAddContactDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_contact, null)

        val spinner = view.findViewById<Spinner>(R.id.spinnerCountryCode)
        val etPhoneNumber = view.findViewById<EditText>(R.id.etPhoneNumber)
        val btnSearch = view.findViewById<TextView>(R.id.btnSearchContact)

        val resultContainer = view.findViewById<LinearLayout>(R.id.contactSearchResult)
        val tvResultFullName = view.findViewById<TextView>(R.id.tvResultFullName)
        val tvResultPhone = view.findViewById<TextView>(R.id.tvResultPhone)
        val btnSendAmitier = view.findViewById<TextView>(R.id.btnSendAmitier)

        val tvNoUser = view.findViewById<TextView>(R.id.tvNoUser)

        val countries = listOf("Morocco +212" to "+212", "USA +1" to "+1")
        spinner.adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_item, countries.map { it.first })
                        .also { adapter ->
                            adapter.setDropDownViewResource(
                                    android.R.layout.simple_spinner_dropdown_item
                            )
                        }

        var foundUser: User? = null

        resultContainer.visibility = View.GONE
        tvNoUser.visibility = View.GONE

        val dialog =
                AlertDialog.Builder(this).setView(view).setNegativeButton(R.string.dialog_delete_cancel, null).create()

        btnSearch.setOnClickListener {
            val localNumber = etPhoneNumber.text.toString().trim()
            if (spinner.selectedItemPosition < 0) {
                Toast.makeText(
                                this,
                                getString(R.string.signup_toast_select_country_digits),
                                Toast.LENGTH_SHORT
                        )
                        .show()
                return@setOnClickListener
            }
            if (localNumber.length != 9 || !localNumber.all { it.isDigit() }) {
                Toast.makeText(
                                this,
                                getString(R.string.signup_toast_select_country_digits),
                                Toast.LENGTH_SHORT
                        )
                        .show()
                return@setOnClickListener
            }

            val code = countries[spinner.selectedItemPosition].second
            val fullPhone = code + localNumber

            vm.searchUserByPhone(fullPhone) { user ->
                foundUser = user
                if (user == null) {
                    resultContainer.visibility = View.GONE
                    tvNoUser.visibility = View.VISIBLE
                    return@searchUserByPhone
                }
                tvNoUser.visibility = View.GONE
                tvResultFullName.text = "${user.prenom} ${user.nom}"
                tvResultPhone.text = user.numTel
                resultContainer.visibility = View.VISIBLE
            }
        }

        btnSendAmitier.setOnClickListener {
            val user =
                    foundUser
                            ?: run {
                                Toast.makeText(
                                                this,
                                                getString(R.string.add_contact_search_first),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                                return@setOnClickListener
                            }

            val currentUserId =
                    SessionManager.getCurrentUserId(this)
                            ?: run {
                                Toast.makeText(this, getString(R.string.common_invalid_session), Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }

            lifecycleScope.launch {
                vm.sendFriendRequest(currentUserId, user.idUser)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

class ContactsAdapter(
        private val onDelete: (ContactUiItem) -> Unit,
        private val onContactClick: (ContactUiItem) -> Unit
) :
        RecyclerView.Adapter<ContactsAdapter.Holder>() {

    private var items: List<ContactUiItem> = emptyList()

    fun submitList(list: List<ContactUiItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return Holder(v, onDelete, onContactClick)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class Holder(
            itemView: View,
            private val onDelete: (ContactUiItem) -> Unit,
            private val onContactClick: (ContactUiItem) -> Unit
    ) :
            RecyclerView.ViewHolder(itemView) {
        private val fullName = itemView.findViewById<TextView>(R.id.itemContactFullName)
        private val phone = itemView.findViewById<TextView>(R.id.itemContactPhone)
        private val deleteBtn = itemView.findViewById<ImageButton>(R.id.itemContactDelete)

        fun bind(item: ContactUiItem) {
            fullName.text = item.fullName
            phone.text = item.phoneNumber
            itemView.setOnClickListener { onContactClick(item) }
            fullName.setOnClickListener { onContactClick(item) }
            phone.setOnClickListener { onContactClick(item) }
            deleteBtn.setOnClickListener { onDelete(item) }
        }
    }
}
