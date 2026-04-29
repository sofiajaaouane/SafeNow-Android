package com.example.safefnow2.activity

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Alert
import com.example.safefnow2.data.local.entity.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlertDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALERT_ID = "extra_alert_id"
        const val EXTRA_USER_ID = "extra_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_detail)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val alertId = intent.getStringExtra(EXTRA_ALERT_ID) ?: return
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: return

        loadAlertDetails(alertId, userId)
    }

    private fun loadAlertDetails(alertId: String, userId: String) {
        lifecycleScope.launch {
            val db = DatabaseProvider.get(this@AlertDetailActivity)
            val alert = withContext(Dispatchers.IO) { db.alertDao().getById(alertId) }
            
            val declaration = withContext(Dispatchers.IO) { 
                db.declarationAlertDao().getById(userId, alertId) 
            }

            val sender = withContext(Dispatchers.IO) {
                val sid = alert?.senderId
                if (sid.isNullOrEmpty()) null else db.userDao().getById(sid)
            }

            if (alert != null) {
                displaySenderInfo(sender, alert, declaration?.localisation)
                loadDestinataires(alert)
                
                if (alert.stoppedById != null) {
                    val stopper = withContext(Dispatchers.IO) { db.userDao().getById(alert.stoppedById) }
                    displayStopperInfo(stopper, alert)
                } else {
                    hideStopperInfo()
                }
            }
        }
    }

    private fun loadDestinataires(alert: Alert) {
        val container = findViewById<LinearLayout>(R.id.llDetailDestinataires)
        container.removeAllViews()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = DatabaseProvider.get(this@AlertDetailActivity)
            val recipients = mutableListOf<User>()

            when (alert.targetType) {
                "GROUP" -> {
                    val groupId = alert.targetId
                    if (groupId != null) {
                        val members = db.groupMemberDao().getByGroupId(groupId)
                        members.forEach { m ->
                            db.userDao().getById(m.idUser)?.let { recipients.add(it) }
                        }
                    }
                }
                "GLOBAL" -> {
                    // SOS Global envoyé à tous mes contacts acceptés
                    val currentUserId = com.example.safefnow2.util.SessionManager.getCurrentUserId(this@AlertDetailActivity)
                    if (currentUserId != null) {
                        recipients.addAll(db.amitierDao().getAcceptedFriends(currentUserId))
                    }
                }
                "CONTACT" -> {
                    val id = alert.targetId
                    if (!id.isNullOrEmpty()) {
                        db.userDao().getById(id)?.let { recipients.add(it) }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (recipients.isEmpty()) {
                    val tv = TextView(this@AlertDetailActivity)
                    tv.text = getString(R.string.alert_no_recipient_found)
                    tv.setTextColor(Color.GRAY)
                    container.addView(tv)
                } else {
                    recipients.forEach { user ->
                        val tv = TextView(this@AlertDetailActivity)
                        tv.text = "• ${user.prenom} ${user.nom}"
                        tv.setPadding(0, 4, 0, 4)
                        tv.setTextColor(Color.BLACK)
                        tv.textSize = 14f
                        container.addView(tv)
                    }
                }
            }
        }
    }

    private fun displaySenderInfo(user: User?, alert: Alert, location: String?) {
        findViewById<TextView>(R.id.tvDetailSenderName).text = user?.let { "${it.prenom} ${it.nom}".trim() }
            ?: alert.senderName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.history_unknown_user)
        
        val typeStr = when (alert.targetType) {
            "GROUP" -> "GROUP SOS (${alert.targetName})"
            "GLOBAL" -> "GLOBAL SOS"
            "RECEIVED" -> "SOS RECEIVED FROM ${alert.targetName}"
            else -> alert.typeAlert
        }
        findViewById<TextView>(R.id.tvDetailAlertType).text = typeStr
        findViewById<TextView>(R.id.tvDetailStartTime).text = alert.createdAt
        val coords = if (alert.senderLatitude != null && alert.senderLongitude != null) {
            " (${String.format("%.4f", alert.senderLatitude)}, ${String.format("%.4f", alert.senderLongitude)})"
        } else ""
        val loc = location ?: alert.senderLocation
        findViewById<TextView>(R.id.tvDetailStartLocation).text = (loc ?: getString(R.string.alert_unknown_location)) + coords
    }

    private fun displayStopperInfo(user: User?, alert: Alert) {
        findViewById<TextView>(R.id.tvDetailStopperName).text = user?.let { "${it.prenom} ${it.nom}" } ?: getString(R.string.history_unknown_user)
        findViewById<TextView>(R.id.tvDetailStopTime).text = alert.stoppedAt
        findViewById<TextView>(R.id.tvDetailStopLocation).text = alert.stoppedLocation ?: getString(R.string.alert_unknown_location)
        
        findViewById<TextView>(R.id.labelStoppedBy).visibility = View.VISIBLE
        findViewById<View>(R.id.cardStoppedInfo).visibility = View.VISIBLE
    }

    private fun hideStopperInfo() {
        findViewById<TextView>(R.id.labelStoppedBy).visibility = View.GONE
        findViewById<View>(R.id.cardStoppedInfo).visibility = View.GONE
    }
}
