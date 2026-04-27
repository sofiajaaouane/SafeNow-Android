package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Alert
import com.example.safefnow2.data.local.entity.DeclarationAlert
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.sync.SyncRepository
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private val alertList = mutableListOf<AlertWithUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvHistory = findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(this@HistoryActivity)
                val currentUserId = SessionManager.getCurrentUserId(this@HistoryActivity).orEmpty()
                if (currentUserId.isNotEmpty()) {
                    val online = ConnectivityObserver(this@HistoryActivity).isOnlineFlow().first()
                    if (online) {
                        runCatching {
                            SyncRepository(db, RtdbClient()).syncNow(currentUserId)
                        }
                    }
                }
                val declarations = if (currentUserId.isEmpty()) emptyList() else db.declarationAlertDao().getAllByUser(currentUserId)
                declarations.map {  decl ->
                    val alert = db.alertDao().getById(decl.idAlert)
                    val sender = alert?.senderId?.let { sid -> db.userDao().getById(sid) }
                    AlertWithUser(alert, sender, decl)
                }
            }
            alertList.clear()
            alertList.addAll(data)
            rvHistory.adapter = HistoryAdapter(alertList)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    data class AlertWithUser(
        val alert: Alert?,
        val sender: User?,
        val declaration: DeclarationAlert
    )

    inner class HistoryAdapter(private val items: List<AlertWithUser>) :
        RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvHistoryUserName)
            val tvType: TextView = view.findViewById(R.id.tvHistoryType)
            val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_alert, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            val meId = SessionManager.getCurrentUserId(this@HistoryActivity).orEmpty()
            val isSender = item.alert?.senderId != null && item.alert.senderId == meId

            holder.tvName.text =
                if (isSender) (item.alert?.targetName ?: "Destinataires")
                else item.sender?.let { "${it.prenom} ${it.nom}".trim() } ?: (item.alert?.senderName ?: "Utilisateur inconnu")

            val targetStr = when (item.alert?.targetType) {
                "GROUP" -> if (isSender) "SOS GROUPE (envoyé)" else "SOS GROUPE (reçu)"
                "GLOBAL" -> if (isSender) "SOS GLOBAL (envoyé)" else "SOS GLOBAL (reçu)"
                "CONTACT" -> if (isSender) "SOS CONTACT (envoyé)" else "SOS CONTACT (reçu)"
                else -> item.alert?.typeAlert ?: "SOS"
            }
            holder.tvType.text = targetStr

            val coords = if (item.declaration.latitude != null && item.declaration.longitude != null) {
                " (${String.format("%.4f", item.declaration.latitude)}, ${String.format("%.4f", item.declaration.longitude)})"
            } else ""
            val loc = item.declaration.localisation?.trim().orEmpty()
            val locStr = if (loc.isNotEmpty()) " - $loc$coords" else if (coords.isNotEmpty()) " -$coords" else ""
            holder.tvDate.text = (item.declaration.createdAt ?: "Date inconnue") + locStr

            holder.itemView.setOnClickListener {
                val intent = Intent(this@HistoryActivity, AlertDetailActivity::class.java).apply {
                    putExtra(AlertDetailActivity.EXTRA_ALERT_ID, item.declaration.idAlert)
                    putExtra(AlertDetailActivity.EXTRA_USER_ID, meId)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
    }
}
