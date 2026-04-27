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
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.data.SosDevicePrefs
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.SosRepository
import com.example.safefnow2.ui.sos.SosUiEvent
import com.example.safefnow2.ui.sos.SosViewModel
import com.example.safefnow2.util.AlertHistoryHelper
import com.example.safefnow2.util.SessionManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ContactDetailsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONTACT_USER_ID = "extra_contact_user_id"
    }

    private val sosViewModel: SosViewModel by viewModels()

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
        val btnSetSosRecipient = findViewById<TextView>(R.id.btnSetSosRecipient)
        val btnContactSos = findViewById<FrameLayout>(R.id.btnContactSos)
        val btnInfo = findViewById<Button>(R.id.contactInfoButton)

        backButton.setOnClickListener { finish() }

        lifecycleScope.launch {
            sosViewModel.events.collect { event ->
                val message = when (event) {
                    is SosUiEvent.Sent -> getString(R.string.toast_sos_sent)
                    is SosUiEvent.PeerMissing -> getString(R.string.toast_sos_peer_missing)
                    is SosUiEvent.Error -> when (event.message) {
                        "contact_device_unknown" -> getString(R.string.toast_sos_contact_no_device)
                        else -> event.message.ifEmpty { "Erreur SOS" }
                    }
                }
                Toast.makeText(this@ContactDetailsActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        val contactUserId = intent.getStringExtra(EXTRA_CONTACT_USER_ID)
        if (contactUserId == null) {
            finish()
            return
        }

        val db = DatabaseProvider.get(this)
        btnInfo.setOnClickListener { showContactInfoDialog() }

        btnSetSosRecipient.setOnClickListener {
            val id = resolvedPeerDeviceId
            val user = contactUser
            if (id.isNullOrBlank() || user == null) {
                Toast.makeText(this, R.string.toast_sos_contact_no_device, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val prefs = SosDevicePrefs(this)
            prefs.setPeerDeviceId(id)
            val digits = user.numTel.filter { it.isDigit() }
            prefs.setPeerPhoneDigits(digits)
            prefs.setPeerDisplayName("${user.prenom} ${user.nom}".trim())
            Toast.makeText(this, R.string.toast_sos_recipient_set, Toast.LENGTH_SHORT).show()
        }

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
                    AlertHistoryHelper.getReadableAddress(this@ContactDetailsActivity, location)
                } else "Position inconnue"

                // 2. Enregistrer dans l'historique local
                AlertHistoryHelper.saveAlertToLocalHistory(
                    context = this@ContactDetailsActivity,
                    userId = selfId,
                    typeStr = "SOS CONTACT",
                    targetType = "CONTACT",
                    targetName = contactLabel,
                    targetId = user.idUser,
                    location = address
                )

                // 3. Envoyer le SOS via le serveur
                sosViewModel.sendSosToContactPhone(user.numTel, senderName, contactLabel)
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
