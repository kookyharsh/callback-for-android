package com.example.callback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var lastNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val backgroundEnabled = sharedPref.getBoolean("background_enabled", true)
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            if (!number.isNullOrEmpty()) {
                lastNumber = number
            }

            val state = when (stateStr) {
                TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                else -> TelephonyManager.CALL_STATE_IDLE
            }

            // Trigger when transitioning to IDLE from anything else, or if we just got an IDLE broadcast and were not IDLE
            if (state == TelephonyManager.CALL_STATE_IDLE && lastState != TelephonyManager.CALL_STATE_IDLE) {
                val isEnabled = sharedPref.getBoolean("feature_enabled", true)
                if (isEnabled && backgroundEnabled) {
                    val showInDnd = sharedPref.getBoolean("show_in_dnd", false)
                    if (!showInDnd && isDndActive(context)) return

                    val serviceIntent = Intent(context, FloatingButtonService::class.java).apply {
                        putExtra("number", lastNumber)
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
            lastState = state
        }
    }

    private fun isDndActive(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        return notificationManager?.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
    }
}