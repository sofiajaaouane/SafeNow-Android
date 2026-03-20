package com.example.safefnow2

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.databinding.ActivityProfileBinding
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ProfileActivity : AppCompatActivity() {

    // ── ViewBinding ──────────────────────────────────────────────────────────
    private lateinit var binding: ActivityProfileBinding

    // ── DAOs via DatabaseProvider ────────────────────────────────────────────
    private val userDao by lazy { DatabaseProvider.get(this).userDao() }
    private val diseaseDao by lazy { DatabaseProvider.get(this).diseaseDao() }

    // ── ID du user connecté ──────────────────────────────────────────────────
    private lateinit var currentUserId: String

    // ── User courant ─────────────────────────────────────────────────────────
    private var currentUser: User? = null

    // ── Etat visibilite mot de passe ─────────────────────────────────────────
    private var isPasswordVisible = false

    // ── GROUPE SANGUIN : groupe sélectionné ──────────────────────────────────
    private var selectedBloodType: String? = null

    // ── Map chips groupe sanguin (initialisée après setContentView) ──────────
    private val bloodTypeChips: Map<String, TextView> by lazy {
        mapOf(
            "A+"  to binding.chipAPos,
            "A-"  to binding.chipANeg,
            "B+"  to binding.chipBPos,
            "B-"  to binding.chipBNeg,
            "AB+" to binding.chipABPos,
            "AB-" to binding.chipABNeg,
            "O+"  to binding.chipOPos,
            "O-"  to binding.chipONeg
        )
    }
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Deconnexion")
            .setMessage("Voulez-vous vous deconnecter ?")
            .setPositiveButton("Deconnecter") { _, _ ->
                SessionManager.clear(this)
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton("Annuler", null)
            .setIcon(R.drawable.ic_logout)
            .show()
    }
    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = SessionManager.getCurrentUserId(this)
        if (userId == null) {
            Toast.makeText(this, "Utilisateur introuvable", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        currentUserId = userId

        loadUserProfile()
        setupListeners()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHARGEMENT DU PROFIL
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadUserProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = userDao.getById(currentUserId)
            val diseases = diseaseDao.getByUserId(currentUserId)

            withContext(Dispatchers.Main) {
                if (user == null) {
                    Toast.makeText(this@ProfileActivity, "Profil introuvable", Toast.LENGTH_SHORT).show()
                    finish()
                    return@withContext
                }
                currentUser = user
                fillForm(user)
                fillDiseases(diseases)
            }
        }
    }

    private fun fillForm(user: User) {
        binding.etNom.setText(user.nom)
        binding.etPrenom.setText(user.prenom)
        binding.etEmail.setText(user.email ?: "")
        binding.etDescription.setText(user.description ?: "")
        binding.etPhone.setText(user.numTel)
        binding.tvUserName.text = "${user.nom} ${user.prenom}"
        binding.etPassword.setText("")

        // ── Pré-sélectionne le groupe sanguin si déjà renseigné ──────────────
        preselectBloodType(user.bloodType)
    }

    private fun fillDiseases(diseases: List<Disease>) {
        binding.llDiseases.removeAllViews()
        diseases.forEach { disease ->
            addDiseaseRow(disease.name)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUPE SANGUIN — CHIPS
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupBloodTypeChips() {
        bloodTypeChips.forEach { (bloodType, chip) ->
            chip.setOnClickListener {
                selectBloodType(bloodType)
            }
        }
    }

    /** Sélectionne visuellement un chip et mémorise le choix */
    private fun selectBloodType(bloodType: String) {
        // Réinitialise tous les chips
        bloodTypeChips.values.forEach { chip ->
            chip.setBackgroundResource(R.drawable.chip_blood_unselected)
            chip.setTextColor(Color.parseColor("#9E9E9E"))
        }
        // Active le chip choisi
        bloodTypeChips[bloodType]?.let { chip ->
            chip.setBackgroundResource(R.drawable.chip_blood_selected)
            chip.setTextColor(Color.parseColor("#D32F2F"))
        }
        selectedBloodType = bloodType
    }

    /** Pré-sélectionne le chip correspondant au groupe sanguin du user */
    private fun preselectBloodType(bloodType: String?) {
        if (bloodType != null && bloodTypeChips.containsKey(bloodType)) {
            selectBloodType(bloodType)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GESTION DES EVENEMENTS
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupListeners() {

        binding.btnBack.setOnClickListener {
            finish()
        }
// Bouton déconnexion
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }
        binding.btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            binding.etPassword.transformationMethod = if (isPasswordVisible) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }

        binding.btnAddDisease.setOnClickListener {
            addDiseaseRow("")
        }

        binding.btnSaveChanges.setOnClickListener {
            saveProfile()
        }

        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }

        // ── Active les clics sur les chips groupe sanguin ────────────────────
        setupBloodTypeChips()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GESTION DES MALADIES
    // ─────────────────────────────────────────────────────────────────────────

    private fun addDiseaseRow(name: String) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_disease, binding.llDiseases, false)

        val etDiseaseName = row.findViewById<EditText>(R.id.etDiseaseName)
        val btnRemove = row.findViewById<ImageButton>(R.id.btnRemoveDisease)

        if (name.isNotEmpty()) etDiseaseName.setText(name)

        btnRemove.setOnClickListener {
            binding.llDiseases.removeView(row)
        }

        binding.llDiseases.addView(row)
    }

    private fun collectDiseases(): List<Disease> {
        val result = mutableListOf<Disease>()
        for (i in 0 until binding.llDiseases.childCount) {
            val row = binding.llDiseases.getChildAt(i) as LinearLayout
            val et = row.findViewById<EditText>(R.id.etDiseaseName)
            val name = et.text.toString().trim()
            if (name.isNotEmpty()) {
                result.add(
                    Disease(
                        idDisease = UUID.randomUUID().toString(),
                        name      = name,
                        idUser    = currentUserId
                    )
                )
            }
        }
        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENREGISTREMENT DU PROFIL
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveProfile() {
        val nom         = binding.etNom.text.toString().trim()
        val prenom      = binding.etPrenom.text.toString().trim()
        val email       = binding.etEmail.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val phone       = binding.etPhone.text.toString().trim()
        val password    = binding.etPassword.text.toString().trim()
        // ── Groupe sanguin : vient du chip sélectionné, pas d'un EditText ────
        val bloodType   = selectedBloodType

        if (nom.isEmpty()) {
            binding.etNom.error = "Le nom est obligatoire"
            binding.etNom.requestFocus()
            return
        }
        if (prenom.isEmpty()) {
            binding.etPrenom.error = "Le prenom est obligatoire"
            binding.etPrenom.requestFocus()
            return
        }
        if (phone.isEmpty()) {
            binding.etPhone.error = "Le numero est obligatoire"
            binding.etPhone.requestFocus()
            return
        }

        val user = currentUser ?: return

        val updatedUser = user.copy(
            nom         = nom,
            prenom      = prenom,
            numTel      = phone,
            email       = email.ifEmpty { null },
            description = description.ifEmpty { null },
            // ── Stocke directement la valeur du chip (ex: "A+", "O-") ────────
            bloodType   = bloodType,
            password    = if (password.isNotEmpty()) password else user.password
        )

        val diseaseEntities = collectDiseases()

        lifecycleScope.launch(Dispatchers.IO) {
            userDao.update(updatedUser)
            diseaseDao.deleteByUserId(currentUserId)
            diseaseEntities.forEach { disease ->
                diseaseDao.insert(disease)
            }

            withContext(Dispatchers.Main) {
                currentUser = updatedUser
                binding.tvUserName.text = "${updatedUser.nom} ${updatedUser.prenom}"
                binding.etPassword.setText("")
                isPasswordVisible = false
                binding.etPassword.transformationMethod =
                    PasswordTransformationMethod.getInstance()
                Toast.makeText(this@ProfileActivity, "Profil mis a jour", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUPPRESSION DU COMPTE
    // ─────────────────────────────────────────────────────────────────────────

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("Supprimer mon compte")
            .setMessage("Etes-vous sur de vouloir supprimer votre compte ? Cette action est irreversible.")
            .setPositiveButton("Supprimer") { _, _ -> deleteAccount() }
            .setNegativeButton("Annuler", null)
            .setIcon(R.drawable.ic_delete)
            .show()
    }

    private fun deleteAccount() {
        val user = currentUser ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            diseaseDao.deleteByUserId(currentUserId)
            userDao.delete(user)

            withContext(Dispatchers.Main) {
                SessionManager.clear(this@ProfileActivity)
                Toast.makeText(this@ProfileActivity, "Compte supprime", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@ProfileActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }
    }
}