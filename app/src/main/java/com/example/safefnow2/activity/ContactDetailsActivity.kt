package com.example.safefnow2.activity

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.data.SosDevicePrefs
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.SosRepository
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
import com.example.safefnow2.util.SessionManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ContactDetailsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONTACT_USER_ID = "extra_contact_user_id"
    }

    private var contactUser: User? = null
    private var contactDiseases: List<Disease> = emptyList()
    private var resolvedPeerDeviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_details)

        val backButton = findViewById<ImageButton>(R.id.contactsDetailsBack)
        val tvContactName = findViewById<TextView>(R.id.tvContactName)
        val tvContactPhone = findViewById<TextView>(R.id.tvContactPhone)
        val tvContactEmail = findViewById<TextView>(R.id.tvContactEmail)
        val tvSosDeviceStatus = findViewById<TextView>(R.id.tvSosDeviceStatus)
        val btnContactSos = findViewById<FrameLayout>(R.id.btnContactSos)
        val btnInfo = findViewById<Button>(R.id.contactInfoButton)

        backButton.setOnClickListener { finish() }

        val contactUserId = intent.getStringExtra(EXTRA_CONTACT_USER_ID)
        if (contactUserId == null) {
            finish()
            return
        }

        val db = DatabaseProvider.get(this)
        btnInfo.setOnClickListener { showContactInfoDialog() }

        btnContactSos.setOnClickListener {
            val user = contactUser ?: return@setOnClickListener
            val selfId = SessionManager.getCurrentUserId(this) ?: return@setOnClickListener
            lifecycleScope.launch {
                val self = withContext(Dispatchers.IO) { db.userDao().getById(selfId) }
                val senderName = self?.let { "${it.prenom} ${it.nom}".trim() }.orEmpty()
                    .ifEmpty { "SafeNow" }
                val contactLabel = "${user.prenom} ${user.nom}".trim()
                
                // 1. Récupérer la localisation réelle
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@ContactDetailsActivity)
                val location = if (ActivityCompat.checkSelfPermission(this@ContactDetailsActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                    } catch (e: Exception) { null }
                } else null

                val address = if (location != null) {
                    com.example.safefnow2.util.AlertHistoryHelper.getReadableAddress(this@ContactDetailsActivity, location)
                } else "Position inconnue"

                val isOnline = ConnectivityObserver(this@ContactDetailsActivity).isOnlineFlow().first()
                if (!isOnline) {
                    Toast.makeText(this@ContactDetailsActivity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val repo = OnlineRepository(
                    DatabaseProvider.get(this@ContactDetailsActivity),
                    OnlineWriteGuard(ConnectivityObserver(this@ContactDetailsActivity).isOnlineFlow()),
                    RtdbClient(),
                )
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        repo.sendContactSos(
                            senderId = selfId,
                            receiverId = user.idUser,
                            senderName = senderName,
                            receiverName = contactLabel,
                            senderLocation = address,
                            senderLat = location?.latitude,
                            senderLng = location?.longitude,
                        )
                    }
                }

                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                    Toast.makeText(this@ContactDetailsActivity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                } else if (result.isFailure) {
                    Toast.makeText(this@ContactDetailsActivity, "Erreur SOS", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ContactDetailsActivity, getString(R.string.toast_sos_sent), Toast.LENGTH_SHORT).show()
                }
            }
        }

        lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) { db.userDao().getById(contactUserId) }
            val diseases = withContext(Dispatchers.IO) { db.diseaseDao().getByUserId(contactUserId) }

            if (user == null) {
                finish()
                return@launch
            }

            contactUser = user
            contactDiseases = diseases

            tvContactName.text = "${user.prenom} ${user.nom}"
            tvContactPhone.text = user.numTel

            val email = user.email
            if (email.isNullOrBlank()) {
                tvContactEmail.visibility = View.GONE
            } else {
                tvContactEmail.text = email
                tvContactEmail.visibility = View.VISIBLE
            }

            tvSosDeviceStatus.text = getString(R.string.contact_sos_status_missing)
            withContext(Dispatchers.IO) {
                val repo = SosRepository(this@ContactDetailsActivity)
                val deviceId = runCatching { repo.lookupDeviceIdByPhone(user.numTel) }.getOrNull()
                withContext(Dispatchers.Main) {
                    resolvedPeerDeviceId = deviceId
                    tvSosDeviceStatus.text = if (deviceId.isNullOrBlank()) {
                        getString(R.string.contact_sos_status_missing)
                    } else {
                        getString(R.string.contact_sos_status_linked)
                    }
                }
            }
        }
    }

    private fun showContactInfoDialog() {
        val user = contactUser ?: return
        val diseases = contactDiseases

        val view = layoutInflater.inflate(R.layout.dialog_contact_info, null)

        val dialogBack = view.findViewById<ImageButton>(R.id.contactInfoBack)
        val tvDescription = view.findViewById<TextView>(R.id.tvContactDescription)
        val tvBloodType = view.findViewById<TextView>(R.id.tvContactBloodType)
        val diseaseContainer = view.findViewById<LinearLayout>(R.id.diseaseContainer)

        tvDescription.visibility = View.GONE
        tvBloodType.visibility = View.GONE

        if (!user.description.isNullOrBlank()) {
            tvDescription.text = "Description: ${user.description}"
            tvDescription.visibility = View.VISIBLE
        }

        if (!user.bloodType.isNullOrBlank()) {
            tvBloodType.text = "Blood type: ${user.bloodType}"
            tvBloodType.visibility = View.VISIBLE
        }

        diseaseContainer.removeAllViews()
        if (diseases.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Aucune disease"
                setTextColor(0xFF999999.toInt())
                textSize = 14f
                setPadding(0, 8, 0, 0)
            }
            diseaseContainer.addView(emptyTv)
        } else {
            diseases.forEach { disease ->
                val diseaseTv = TextView(this).apply {
                    text = disease.name
                    setTextColor(0xFF111111.toInt())
                    textSize = 14f
                    setPadding(0, 6, 0, 0)
                }
                diseaseContainer.addView(diseaseTv)
            }
        }

        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialogBack.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
