package com.example.safefnow2.activity

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.safefnow2.R

class SosIncomingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SENDER_NAME = "extra_sender_name"
    }

    private var ringtone: Ringtone? = null

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

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}

