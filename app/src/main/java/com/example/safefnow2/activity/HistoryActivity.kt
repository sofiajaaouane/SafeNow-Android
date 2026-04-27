package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Alert
import com.example.safefnow2.data.local.entity.DeclarationAlert
import com.example.safefnow2.data.local.entity.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var rvHistory: RecyclerView
    private val alertList = mutableListOf<AlertWithUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvHistory = findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)
        
        loadHistory()
    }

    private fun loadHistory() {
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(this@HistoryActivity)
                val declarations = db.declarationAlertDao().getAll()
                declarations.map {  decl ->
                    val user = db.userDao().getById(decl.idUser)
                    val alert = db.alertDao().getById(decl.idAlert)
                    AlertWithUser(alert, user, decl)
                }
            }
            alertList.clear()
            alertList.addAll(data)
            rvHistory.adapter = HistoryAdapter(alertList)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    data class AlertWithUser(
        val alert: Alert?,
        val user: User?,
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
            
            holder.tvName.text = item.user?.let { "${it.prenom} ${it.nom}" } ?: "Utilisateur inconnu"
            
            val targetStr = when (item.alert?.targetType) {
                "GROUP" -> "Groupe : ${item.alert.targetName ?: "Inconnu"}"
                "GLOBAL" -> "Alerte Globale"
                "CONTACT" -> "Contact : ${item.alert.targetName ?: "Inconnu"}"
                "RECEIVED" -> "SOS Reçu de ${item.alert.targetName ?: "Inconnu"}"
                else -> item.alert?.typeAlert ?: "SOS"
            }
            holder.tvType.text = targetStr

            holder.tvDate.text = item.declaration.createdAt ?: "Date inconnue"

            holder.itemView.setOnClickListener {
                val intent = Intent(this@HistoryActivity, AlertDetailActivity::class.java).apply {
                    putExtra(AlertDetailActivity.EXTRA_ALERT_ID, item.declaration.idAlert)
                    putExtra(AlertDetailActivity.EXTRA_USER_ID, item.declaration.idUser)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
    }
}
