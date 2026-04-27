package com.example.safefnow2.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.example.safefnow2.R
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.data.sync.SyncRepository
import com.example.safefnow2.util.SessionManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SosIncomingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_SOS_ID = "extra_sos_id"
    }

    private var ringtone: Ringtone? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rtdb by lazy { RtdbClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_sos_incoming)

        val sender = intent.getStringExtra(EXTRA_SENDER_NAME).orEmpty()
        val sosId = intent.getStringExtra(EXTRA_SOS_ID).orEmpty()
        
        findViewById<TextView>(R.id.tvSosIncomingSender).text =
            if (sender.isNotEmpty()) sender else "SafeNow"

        findViewById<TextView>(R.id.btnSosIncomingStop).setOnClickListener {
            handleStopSos(sosId)
        }

        startAlarm()
    }

    private fun handleStopSos(sosId: String) {
        stopAlarm()
        val userId = SessionManager.getCurrentUserId(this)?.trim().orEmpty()
        
        scope.launch {
            // 1. Récupérer la localisation de celui qui arrête
            val location = getCurrentLocation()
            val address = if (location != null) getReadableAddress(location) else "Position inconnue"
            val stopTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // 2. Mettre à jour la base de données locale + RTDB (RECEIVED = STOP click)
            withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(this@SosIncomingActivity)
                val alert = db.alertDao().getById(sosId)
                val updatedLocal =
                    if (alert != null) {
                        alert.copy(
                            stoppedById = userId,
                            stoppedAt = stopTime,
                            stoppedLocation = address,
                            stoppedLatitude = location?.latitude,
                            stoppedLongitude = location?.longitude,
                        )
                    } else {
                        com.example.safefnow2.data.local.entity.Alert(
                            idAlert = sosId,
                            createdAt = stopTime,
                            typeAlert = "SOS",
                            targetType = "RECEIVED",
                            stoppedById = userId,
                            stoppedAt = stopTime,
                            stoppedLocation = address,
                            stoppedLatitude = location?.latitude,
                            stoppedLongitude = location?.longitude,
                        )
                    }
                db.alertDao().insert(updatedLocal)

                db.declarationAlertDao().insert(
                    com.example.safefnow2.data.local.entity.DeclarationAlert(
                        idUser = userId,
                        idAlert = sosId,
                        localisation = address,
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                        status = "RECEIVED",
                        createdAt = stopTime,
                    )
                )

                val updates = mapOf(
                    "${RtdbPaths.alert(sosId)}/stoppedById" to userId,
                    "${RtdbPaths.alert(sosId)}/stoppedAt" to stopTime,
                    "${RtdbPaths.alert(sosId)}/stoppedLocation" to address,
                    "${RtdbPaths.alert(sosId)}/stoppedLatitude" to location?.latitude,
                    "${RtdbPaths.alert(sosId)}/stoppedLongitude" to location?.longitude,
                    RtdbPaths.declarationAlert(userId, sosId) to mapOf(
                        "idUser" to userId,
                        "idAlert" to sosId,
                        "status" to "RECEIVED",
                        "createdAt" to stopTime,
                        "localisation" to address,
                        "latitude" to location?.latitude,
                        "longitude" to location?.longitude,
                        "updatedAt" to rtdb.serverTimestamp(),
                    ),
                )
                runCatching { rtdb.updateChildren("", updates) }
                runCatching { SyncRepository(db, rtdb).syncNow(userId) }
            }

            // 3. Effacer sur Firebase
            clearSosId(userId)
            
            finish()
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        return try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getReadableAddress(location: Location): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@SosIncomingActivity, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: "Adresse introuvable"
                } else "Lat: ${location.latitude}, Lon: ${location.longitude}"
            } catch (e: Exception) {
                "Lat: ${location.latitude}, Lon: ${location.longitude}"
            }
        }
    }

    private fun startAlarm() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val r = RingtoneManager.getRingtone(this, uri) ?: return
        ringtone = r
        r.isLooping = true
        r.play()
    }

    private fun stopAlarm() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun clearSosId(userId: String) {
        if (userId.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val updates = mapOf(
                RtdbPaths.userSosId(userId) to null,
                RtdbPaths.userSosSenderName(userId) to null,
                RtdbPaths.userSosCreatedAt(userId) to null
            )
            runCatching { rtdb.updateChildren("", updates) }
        }
    }

    override fun onDestroy() {
        stopAlarm()
        scope.cancel()
        super.onDestroy()
    }
}
