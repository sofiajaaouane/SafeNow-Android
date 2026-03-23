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
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.dao.AmitierDao
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.runBlocking

class NotificationsActivity : AppCompatActivity() {

    private lateinit var amitierDao: AmitierDao
    private lateinit var requestsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var tvRequestCount: TextView
    private lateinit var btnBack: ImageView

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
        loadPendingRequests()
    }

    private fun initViews() {
        requestsContainer = findViewById(R.id.requestsContainer)
        progressBar = findViewById(R.id.progressBar)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        scrollView = findViewById(R.id.scrollView)
        tvRequestCount = findViewById(R.id.tvRequestCount)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
    }

    private fun loadPendingRequests() {
        progressBar.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE

        Thread {
            val users =
                    runBlocking {
                        amitierDao.getPendingReceivedRequests(currentUserId)
                    }

            runOnUiThread {
                displayRequests(users)
                progressBar.visibility = View.GONE
            }
        }.start()
    }

    private fun displayRequests(requests: List<User>) {
        requestsContainer.removeAllViews()

        if (requests.isEmpty()) {
            scrollView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            tvRequestCount.visibility = View.GONE
        } else {
            scrollView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            tvRequestCount.visibility = View.VISIBLE
            tvRequestCount.text = requests.size.toString()

            requests.forEach { user -> requestsContainer.addView(createRequestItem(user)) }
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
        Thread {
            runBlocking { amitierDao.acceptRequest(user.idUser, currentUserId) }
            runOnUiThread {
                Toast.makeText(this, "Ami ajouté", Toast.LENGTH_SHORT).show()
                loadPendingRequests()
            }
        }.start()
    }

    private fun rejectRequest(user: User) {
        Thread {
            runBlocking { amitierDao.rejectRequest(user.idUser, currentUserId) }
            runOnUiThread {
                Toast.makeText(this, "Invitation refusée", Toast.LENGTH_SHORT).show()
                loadPendingRequests()
            }
        }.start()
    }
}
