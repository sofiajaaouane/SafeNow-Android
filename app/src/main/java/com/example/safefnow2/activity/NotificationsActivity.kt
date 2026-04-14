package com.example.safefnow2.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.data.remote.SosHistoryEntry
import com.example.safefnow2.data.remote.SosRepository
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.dao.AmitierDao
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.util.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsActivity : AppCompatActivity() {

    private lateinit var amitierDao: AmitierDao
    private lateinit var requestsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var tvRequestCount: TextView
    private lateinit var btnBack: ImageView
    private lateinit var llSosHistory: LinearLayout
    private lateinit var tvSosHistoryEmpty: TextView
    private lateinit var progressSosHistory: ProgressBar

    private var currentUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val uid = SessionManager.getCurrentUserId(this)
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "Session invalide", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentUserId = uid

        val database = DatabaseProvider.get(this)
        amitierDao = database.amitierDao()

        initViews()
        loadAll()
    }

    override fun onResume() {
        super.onResume()
        if (currentUserId.isNotEmpty()) {
            loadAll()
        }
    }

    private fun initViews() {
        requestsContainer = findViewById(R.id.requestsContainer)
        progressBar = findViewById(R.id.progressBar)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        scrollView = findViewById(R.id.scrollView)
        tvRequestCount = findViewById(R.id.tvRequestCount)
        btnBack = findViewById(R.id.btnBack)
        llSosHistory = findViewById(R.id.llSosHistory)
        tvSosHistoryEmpty = findViewById(R.id.tvSosHistoryEmpty)
        progressSosHistory = findViewById(R.id.progressSosHistory)

        btnBack.setOnClickListener { finish() }
    }

    private fun loadAll() {
        progressBar.visibility = View.VISIBLE
        scrollView.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val users = amitierDao.getPendingReceivedRequests(currentUserId)
            val history = runCatching { SosRepository(this@NotificationsActivity).loadSosHistoryForMyPhone() }
                .getOrElse { emptyList() }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                scrollView.visibility = View.VISIBLE
                displayRequests(users)
                displaySosHistory(history)
            }
        }
    }

    private fun displayRequests(requests: List<User>) {
        requestsContainer.removeAllViews()

        if (requests.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            tvRequestCount.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            tvRequestCount.visibility = View.VISIBLE
            tvRequestCount.text = requests.size.toString()
            requests.forEach { user -> requestsContainer.addView(createRequestItem(user)) }
        }
    }

    private fun displaySosHistory(entries: List<SosHistoryEntry>) {
        llSosHistory.removeAllViews()
        progressSosHistory.visibility = View.GONE

        if (entries.isEmpty()) {
            tvSosHistoryEmpty.visibility = View.VISIBLE
        } else {
            tvSosHistoryEmpty.visibility = View.GONE
            val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            entries.forEach { e ->
                val row = LayoutInflater.from(this).inflate(R.layout.item_sos_history, llSosHistory, false)
                val roleLabel = when (e.role) {
                    "sent" -> getString(R.string.sos_history_role_sent)
                    "received" -> getString(R.string.sos_history_role_received)
                    else -> e.role
                }
                row.findViewById<TextView>(R.id.tvSosHistoryRole).text = roleLabel
                row.findViewById<TextView>(R.id.tvSosHistoryPeer).text =
                    e.peerDisplayName.ifEmpty { "—" }
                row.findViewById<TextView>(R.id.tvSosHistoryPhone).text =
                    if (e.peerPhoneDigits.isNotEmpty()) e.peerPhoneDigits else ""
                row.findViewById<TextView>(R.id.tvSosHistoryRequestId).text =
                    "ID: ${e.requestId} · ${fmt.format(Date(e.createdAtMillis))}"
                llSosHistory.addView(row)
            }
        }
    }

    private fun createRequestItem(user: User): View {
        val itemView =
            LayoutInflater.from(this).inflate(R.layout.item_friend_request, requestsContainer, false)

        val tvInitials: TextView = itemView.findViewById(R.id.tvInitials)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)

        val p = user.prenom.firstOrNull()
        val n = user.nom.firstOrNull()
        tvInitials.text =
            buildString {
                if (p != null) append(p.uppercaseChar())
                if (n != null) append(n.uppercaseChar())
            }

        tvName.text = "${user.prenom} ${user.nom}"
        tvPhone.text = user.numTel

        if (!user.email.isNullOrEmpty()) {
            tvEmail.text = user.email
            tvEmail.visibility = View.VISIBLE
        }

        btnAccept.setOnClickListener { acceptRequest(user) }
        btnReject.setOnClickListener { rejectRequest(user) }

        return itemView
    }

    private fun acceptRequest(user: User) {
        lifecycleScope.launch(Dispatchers.IO) {
            amitierDao.acceptRequest(user.idUser, currentUserId)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@NotificationsActivity, "Ami ajouté", Toast.LENGTH_SHORT).show()
                loadAll()
            }
        }
    }

    private fun rejectRequest(user: User) {
        lifecycleScope.launch(Dispatchers.IO) {
            amitierDao.rejectRequest(user.idUser, currentUserId)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@NotificationsActivity, "Invitation refusée", Toast.LENGTH_SHORT).show()
                loadAll()
            }
        }
    }
}
