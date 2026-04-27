package com.example.safefnow2.util

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.example.safefnow2.data.local.DatabaseProvider
import com.example.safefnow2.data.local.entity.Alert
import com.example.safefnow2.data.local.entity.DeclarationAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object AlertHistoryHelper {
    suspend fun saveAlertToLocalHistory(
        context: Context,
        userId: String,
        typeStr: String,
        targetType: String,
        targetName: String?,
        targetId: String?,
        location: String?
    ) {
        withContext(Dispatchers.IO) {
            val db = DatabaseProvider.get(context)
            val alertId = UUID.randomUUID().toString()
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val alert = Alert(
                idAlert = alertId,
                createdAt = now,
                typeAlert = typeStr,
                targetType = targetType,
                targetName = targetName,
                targetId = targetId
            )
            db.alertDao().insert(alert)

            val decl = DeclarationAlert(
                idUser = userId,
                idAlert = alertId,
                createdAt = now,
                localisation = location,
                status = "SENT"
            )
            db.declarationAlertDao().insert(decl)
        }
    }

    suspend fun getReadableAddress(context: Context, location: Location): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: "Adresse introuvable"
                } else {
                    "Lat: ${"%.4f".format(location.latitude)}, Lon: ${"%.4f".format(location.longitude)}"
                }
            } catch (e: Exception) {
                "Lat: ${"%.4f".format(location.latitude)}, Lon: ${"%.4f".format(location.longitude)}"
            }
        }
    }
}
