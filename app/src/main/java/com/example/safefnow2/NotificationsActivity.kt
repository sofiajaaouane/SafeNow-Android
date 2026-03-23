package com.example.safefnow2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.dao.AmitierDao
import com.example.safefnow2.data.local.entity.User
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

        val database = DatabaseProvider.get(this)
        amitierDao = database.amitierDao()

        currentUserId = getCurrentUserId()

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
            val users = runBlocking {
                val allInvitations = amitierDao.getAll()
                val myInvitations = allInvitations.filter {
                    it.idUser2 == currentUserId && it.status == "PENDING"
                }

                val userDao = DatabaseProvider.get(this@NotificationsActivity).userDao()
                myInvitations.mapNotNull { inv ->
                    userDao.getById(inv.idUser1)
                }
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

            requests.forEach { user ->
                requestsContainer.addView(createRequestItem(user))
            }
        }
    }

    private fun createRequestItem(user: User): View {
        val itemView = LayoutInflater.from(this)
            .inflate(R.layout.item_friend_request, requestsContainer, false)

        val tvInitials: TextView = itemView.findViewById(R.id.tvInitials)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)

        tvInitials.text = "${user.nom.first()}${user.prenom.first()}".uppercase()
        tvName.text = "${user.nom} ${user.prenom}"
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
            runBlocking {
                amitierDao.acceptRequest(user.idUser, currentUserId)
            }
            runOnUiThread {
                Toast.makeText(this, "Ami ajouté", Toast.LENGTH_SHORT).show()
                loadPendingRequests()
            }
        }.start()
    }

    private fun rejectRequest(user: User) {
        Thread {
            runBlocking {
                amitierDao.rejectRequest(user.idUser, currentUserId)
            }
            runOnUiThread {
                Toast.makeText(this, "Invitation refusée", Toast.LENGTH_SHORT).show()
                loadPendingRequests()
            }
        }.start()
    }

    private fun getCurrentUserId(): String {
        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        return sharedPref.getString("user_id", "") ?: ""
    }
}