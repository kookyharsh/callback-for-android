package com.example.callback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    companion object {
        private var pendingNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Only remember a number reported for this call. Android may also send
                // a copy of the broadcast without the number.
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (!number.isNullOrBlank()) pendingNumber = number
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val number = pendingNumber
                pendingNumber = null
                if (number != null) showRecallButton(context, number)
            }
        }
    }

    private fun showRecallButton(context: Context, number: String) {
        val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!sharedPref.getBoolean("feature_enabled", true)) return
        if (!sharedPref.getBoolean("background_enabled", true)) return
        if (!sharedPref.getBoolean("show_in_dnd", false) && isDndActive(context)) return

        val serviceIntent = Intent(context, FloatingButtonService::class.java).apply {
            putExtra("number", number)
        }
        context.startForegroundService(serviceIntent)
    }

    private fun isDndActive(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        return notificationManager?.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
    }
}
