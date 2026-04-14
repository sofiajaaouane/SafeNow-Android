package com.example.safefnow2.activity

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.safefnow2.R
import com.example.safefnow2.data.remote.RtdbClient
import com.example.safefnow2.data.remote.RtdbPaths
import com.example.safefnow2.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SosIncomingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SENDER_NAME = "extra_sender_name"
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
        findViewById<TextView>(R.id.tvSosIncomingSender).text =
            if (sender.isNotEmpty()) sender else "SafeNow"

        findViewById<TextView>(R.id.btnSosIncomingStop).setOnClickListener {
            stopAlarm()
            clearSosId()
            finish()
        }

        startAlarm()
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

    private fun clearSosId() {
        val userId = SessionManager.getCurrentUserId(this)?.trim().orEmpty()
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

