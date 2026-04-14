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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Amitier
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.sync.SyncRepository
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
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

    private val searchQuery = MutableStateFlow("")
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
                        searchQuery.value = s?.toString()?.trim() ?: ""
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
        val db = DatabaseProvider.get(this)

        combine(db.amitierDao().getAllFlow(), db.userDao().getAllFlow(), searchQuery) {
                        amitierList,
                        users,
                        query ->
                    val usersById = users.associateBy { it.idUser }
                    val acceptedRelations =
                            amitierList.filter {
                                it.status.trim().equals("ACCEPTED", ignoreCase = true) &&
                                        (it.idUser1 == currentUserId || it.idUser2 == currentUserId)
                            }

                    val items =
                            acceptedRelations
                                    .mapNotNull { relation ->
                                        val otherUserId =
                                                if (relation.idUser1 == currentUserId)
                                                        relation.idUser2
                                                else relation.idUser1
                                        val other = usersById[otherUserId] ?: return@mapNotNull null
                                        ContactUiItem(
                                                amitierIdUser1 = relation.idUser1,
                                                amitierIdUser2 = relation.idUser2,
                                                contactUserId = otherUserId,
                                                prenom = other.prenom,
                                                nom = other.nom,
                                                fullName = "${other.prenom} ${other.nom}",
                                                phoneNumber = other.numTel
                                        )
                                    }
                                    .sortedBy { it.fullName }

                    if (query.isEmpty()) {
                        items
                    } else {
                        val normalizedQuery = query.trim()
                        val hasSpace = normalizedQuery.contains(' ')

                        if (hasSpace) {
                            items.filter {
                                it.fullName.contains(normalizedQuery, ignoreCase = true)
                            }
                        } else {
                            items.filter {
                                it.prenom.contains(normalizedQuery, ignoreCase = true) ||
                                        it.nom.contains(normalizedQuery, ignoreCase = true)
                            }
                        }
                    }
                }
                .onEach { filtered ->
                    adapter.submitList(filtered)
                    empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                }
                .launchIn(lifecycleScope)

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
                .setTitle("Supprimer le contact")
                .setMessage("Supprimer ${item.fullName} ?")
                .setPositiveButton("Supprimer") { _, _ -> deleteAmitier(item) }
                .setNegativeButton("Annuler", null)
                .show()
    }

    private fun deleteAmitier(item: ContactUiItem) {
        lifecycleScope.launch {
            val currentUserId = SessionManager.getCurrentUserId(this@ContactsActivity).orEmpty()
            val edge = Amitier(
                idUser1 = item.amitierIdUser1,
                idUser2 = item.amitierIdUser2,
                status = "PENDING"
            )
            val result = withContext(Dispatchers.IO) {
                runCatching { onlineRepo.deleteFriendEdge(edge, currentUserId) }
            }
            if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                Toast.makeText(this@ContactsActivity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ContactsActivity, "Contact supprimé", Toast.LENGTH_SHORT).show()
            }
        }
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
                AlertDialog.Builder(this).setView(view).setNegativeButton("Annuler", null).create()

        btnSearch.setOnClickListener {
            val localNumber = etPhoneNumber.text.toString().trim()
            if (spinner.selectedItemPosition < 0) {
                Toast.makeText(
                                this,
                                "Selectionnez le pays puis entrez exactement 9 chiffres",
                                Toast.LENGTH_SHORT
                        )
                        .show()
                return@setOnClickListener
            }
            if (localNumber.length != 9 || !localNumber.all { it.isDigit() }) {
                Toast.makeText(
                                this,
                                "Selectionnez le pays puis entrez exactement 9 chiffres",
                                Toast.LENGTH_SHORT
                        )
                        .show()
                return@setOnClickListener
            }

            val code = countries[spinner.selectedItemPosition].second
            val fullPhone = code + localNumber

            lifecycleScope.launch {
                val db = DatabaseProvider.get(this@ContactsActivity)
                val user = withContext(Dispatchers.IO) {
                    val local = db.userDao().getByPhone(fullPhone)
                    if (local != null) return@withContext local

                    val online = ConnectivityObserver(this@ContactsActivity).isOnlineFlow().first()
                    if (!online) return@withContext null

                    val rtdb = RtdbClient()
                    val userIdSnap = rtdb.get(RtdbPaths.userByPhone(fullPhone))
                    val userId = userIdSnap.getValue(String::class.java) ?: return@withContext null
                    val userSnap = rtdb.get(RtdbPaths.user(userId))
                    val remoteUser = userSnap.getValue(User::class.java)
                    if (remoteUser != null) {
                        db.userDao().insert(remoteUser)
                        runCatching { SyncRepository(db, rtdb).syncNow(SessionManager.getCurrentUserId(this@ContactsActivity).orEmpty()) }
                    }
                    remoteUser
                }

                foundUser = user

                if (user == null) {
                    resultContainer.visibility = View.GONE
                    tvNoUser.visibility = View.VISIBLE
                    return@launch
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
                                                "Cherchez un utilisateur d'abord",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                                return@setOnClickListener
                            }

            val currentUserId =
                    SessionManager.getCurrentUserId(this)
                            ?: run {
                                Toast.makeText(this, "Session invalide", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }

            lifecycleScope.launch {
                val db = DatabaseProvider.get(this@ContactsActivity)
                val existing =
                        withContext(Dispatchers.IO) {
                            db.amitierDao().getById(currentUserId, user.idUser)
                        }

                if (existing != null) {
                    Toast.makeText(
                                    this@ContactsActivity,
                                    "Demande déjà envoyée",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        onlineRepo.sendFriendRequest(
                            Amitier(
                                idUser1 = currentUserId,
                                idUser2 = user.idUser,
                                status = "PENDING"
                            ),
                            currentUserId = currentUserId
                        )
                    }
                }

                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                    Toast.makeText(this@ContactsActivity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ContactsActivity, "Demande envoyée", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
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
