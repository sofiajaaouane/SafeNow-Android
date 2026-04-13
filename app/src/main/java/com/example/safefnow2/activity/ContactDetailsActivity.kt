    package com.example.safefnow2.activity

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactDetailsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONTACT_USER_ID = "extra_contact_user_id"
    }

    private var contactUser: User? = null
    private var contactDiseases: List<Disease> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_details)

        val backButton = findViewById<ImageButton>(R.id.contactsDetailsBack)
        val tvContactName = findViewById<TextView>(R.id.tvContactName)
        val tvContactPhone = findViewById<TextView>(R.id.tvContactPhone)
        val tvContactEmail = findViewById<TextView>(R.id.tvContactEmail)
        val btnInfo = findViewById<Button>(R.id.contactInfoButton)

        backButton.setOnClickListener { finish() }

        val contactUserId = intent.getStringExtra(EXTRA_CONTACT_USER_ID)
        if (contactUserId == null) {
            finish()
            return
        }

        val db = DatabaseProvider.get(this)
        btnInfo.setOnClickListener { showContactInfoDialog() }

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
                    setTextColor(0xFF111111111.toInt())
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

