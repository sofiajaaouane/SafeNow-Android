package com.example.safefnow2.activity

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.util.ConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignUpStep1Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup_step1)

        val spinner = findViewById<Spinner>(R.id.spinnerCountryCode)
        val etPhone = findViewById<EditText>(R.id.etPhoneNumber)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        val btnNext = findViewById<TextView>(R.id.btnNext)

        val countries = listOf(
            "Morocco +212" to "+212",
            "USA +1" to "+1"
        )
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, countries.map { it.first })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        tvLogin.setOnClickListener {
            finish()
        }

        btnNext.setOnClickListener {
            if (spinner.selectedItemPosition < 0) {
                Toast.makeText(this, "Selectionnez le pays puis entrez exactement 9 chiffres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val localNumber = etPhone.text.toString().trim()
            if (localNumber.length != 9 || !localNumber.all { it.isDigit() }) {
                Toast.makeText(this, "Selectionnez le pays puis entrez exactement 9 chiffres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val code = countries[spinner.selectedItemPosition].second
            val fullPhone = code + localNumber

            lifecycleScope.launch {
                val isTaken = withContext(Dispatchers.IO) {
                    val db = DatabaseProvider.get(this@SignUpStep1Activity)
                    if (db.userDao().getByPhone(fullPhone) != null) return@withContext true

                    val online = ConnectivityObserver(this@SignUpStep1Activity).isOnlineFlow().first()
                    if (!online) return@withContext false

                    val rtdb = RtdbClient()
                    val digits = fullPhone.filter { it.isDigit() }
                    val byFull = rtdb.get(RtdbPaths.userByPhone(fullPhone)).getValue(String::class.java)
                    val byDigits = if (digits.isNotEmpty()) {
                        rtdb.get(RtdbPaths.userByPhone(digits)).getValue(String::class.java)
                    } else null
                    !byFull.isNullOrEmpty() || !byDigits.isNullOrEmpty()
                }

                if (isTaken) {
                    Toast.makeText(this@SignUpStep1Activity, "Ce numero est deja utilise", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                startActivity(
                    Intent(this@SignUpStep1Activity, SignUpStep2Activity::class.java)
                        .putExtra(EXTRA_PHONE, fullPhone)
                )
            }
        }
    }

    companion object {
        const val EXTRA_PHONE = "extra_phone"
    }
}
