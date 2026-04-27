package com.example.safefnow2.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.safefnow2.activity.SosIncomingActivity

/**
 * Helper for alert notifications with sound and vibration.
 */
object AlertHelper {

    private const val CHANNEL_ID = "safenow_alerts"
    private const val CHANNEL_NAME = "Alertes SafeNow"
    private const val SOS_CHANNEL_ID = "safenow_sos"
    private const val SOS_CHANNEL_NAME = "SOS SafeNow"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        val alerts = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(soundUri, audioAttributes)
        }
        manager.createNotificationChannel(alerts)

        val sos = NotificationChannel(SOS_CHANNEL_ID, SOS_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 800)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(soundUri, audioAttributes)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(sos)
    }

    fun showAlertNotification(context: Context, title: String, text: String, notificationId: Int = 1) {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) { }
    }

    fun showSosFullScreen(context: Context, title: String, text: String, senderName: String, sosId: String, notificationId: Int = 999) {
        ensureChannel(context)
        val intent = Intent(context, SosIncomingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SosIncomingActivity.EXTRA_SENDER_NAME, senderName)
            putExtra(SosIncomingActivity.EXTRA_SOS_ID, sosId)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(pending, true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) { }
    }

    fun startSosIncomingActivity(context: Context, senderName: String, sosId: String) {
        val intent = Intent(context, SosIncomingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SosIncomingActivity.EXTRA_SENDER_NAME, senderName)
            putExtra(SosIncomingActivity.EXTRA_SOS_ID, sosId)
        }
        runCatching { context.startActivity(intent) }
    }

    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = ContextCompat.getSystemService(context, VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(context, Vibrator::class.java)
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
        } else {
            @Suppress("DEPRECATION")
            (vibrator as? Vibrator)?.vibrate(400L)
        }
    }
}
