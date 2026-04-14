package com.example.safefnow2

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import com.example.safefnow2.activity.LoginActivity
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Disease
import com.example.safefnow2.data.local.entity.User
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.repository.OfflineWriteNotAllowed
import com.example.safefnow2.data.repository.OnlineRepository
import com.example.safefnow2.databinding.ActivityProfileBinding
import com.example.safefnow2.data.SosDevicePrefs
import com.example.safefnow2.data.remote.SosRepository
import com.example.safefnow2.service.AlwaysListenPrefs
import com.example.safefnow2.service.AlwaysListenService
import com.example.safefnow2.util.ConnectivityObserver
import com.example.safefnow2.util.OnlineWriteGuard
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
    private val sosRepository by lazy { SosRepository(this) }
    private val onlineRepo by lazy {
        val isOnline = ConnectivityObserver(this).isOnlineFlow()
        OnlineRepository(DatabaseProvider.get(this), OnlineWriteGuard(isOnline), RtdbClient())
    }

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
                val intent = Intent(this, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
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
            val intent = Intent(this, LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
            return
        }
        currentUserId = userId

        setupSosDeviceUi()
        setupAlwaysListenUi()

        loadUserProfile()
        setupListeners()
    }

    private fun setupSosDeviceUi() {
        val sosPrefs = SosDevicePrefs(this)
        binding.tvMyDeviceId.text = sosPrefs.getOrCreateDeviceId()
        binding.etPeerDeviceId.setText(sosPrefs.getPeerDeviceId())
        binding.btnSavePeerDevice.setOnClickListener {
            sosPrefs.setPeerDeviceId(binding.etPeerDeviceId.text.toString())
            sosPrefs.clearPeerPhoneHint()
            Toast.makeText(this, R.string.toast_peer_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAlwaysListenUi() {
        val prefs = AlwaysListenPrefs(this)
        binding.switchAlwaysListen.isChecked = prefs.isEnabled()

        binding.switchAlwaysListen.setOnCheckedChangeListener { _, enabled ->
            prefs.setEnabled(enabled)
            if (enabled) AlwaysListenService.start(this) else AlwaysListenService.stop(this)
        }

        binding.btnBatteryOpt.setOnClickListener {
            requestDisableBatteryOptimization()
        }
    }

    private fun requestDisableBatteryOptimization() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            return
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val pkg = packageName
        if (pm.isIgnoringBatteryOptimizations(pkg)) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            return
        }
        runCatching {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            }
            startActivity(i)
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
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
            val result = runCatching { onlineRepo.updateProfile(updatedUser, diseaseEntities) }
            withContext(Dispatchers.Main) {
                result.onFailure {
                    if (it is OfflineWriteNotAllowed) {
                        Toast.makeText(this@ProfileActivity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ProfileActivity, "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
                result.onSuccess {
                    currentUser = updatedUser
                    binding.tvUserName.text = "${updatedUser.nom} ${updatedUser.prenom}"
                    binding.etPassword.setText("")
                    isPasswordVisible = false
                    binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                    Toast.makeText(this@ProfileActivity, "Profil mis a jour", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch(Dispatchers.IO) {
                        runCatching {
                            sosRepository.syncMyDeviceToCloud(
                                "${updatedUser.prenom} ${updatedUser.nom}".trim().ifEmpty { "SafeNow" },
                                updatedUser.numTel,
                                updatedUser.idUser
                            )
                        }
                    }
                }
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
            val result = runCatching { onlineRepo.deleteAccount(currentUserId) }

            withContext(Dispatchers.Main) {
                if (result.isFailure && result.exceptionOrNull() is OfflineWriteNotAllowed) {
                    Toast.makeText(this@ProfileActivity, "Connectez-vous a Internet", Toast.LENGTH_SHORT).show()
                } else {
                    SessionManager.clear(this@ProfileActivity)
                    Toast.makeText(this@ProfileActivity, "Compte supprime", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}