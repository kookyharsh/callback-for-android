package com.example.callback

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class CallNotificationListenerService : NotificationListenerService() {

    companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        // Track ongoing calls per package to detect call end transitions
        private val ongoingCalls = mutableMapOf<String, String>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val featureEnabled = sharedPref.getBoolean("feature_enabled", true)
        val backgroundEnabled = sharedPref.getBoolean("background_enabled", true)
        if (!featureEnabled || !backgroundEnabled) return

        val isWhatsAppEnabled = sharedPref.getBoolean("app_whatsapp_enabled", true)
        val isW4bEnabled = sharedPref.getBoolean("app_w4b_enabled", true)

        if (packageName == WHATSAPP_PACKAGE && !isWhatsAppEnabled) return
        if (packageName == WHATSAPP_BUSINESS_PACKAGE && !isW4bEnabled) return

        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val category = notification.category

        val fullContent = "$title $text $subText".lowercase()

        // Check if this notification is a call notification
        val isCallCategory = category == Notification.CATEGORY_CALL
        val containsCallKeywords = fullContent.contains("call") ||
                fullContent.contains("chamada") ||
                fullContent.contains("llamada") ||
                fullContent.contains("appels")

        if (!isCallCategory && !containsCallKeywords) return

        val isVideo = fullContent.contains("video") || fullContent.contains("vídeo")
        val callType = if (isVideo) "video" else "voice"

        val contactName = extractContactName(title, text)

        val isMissedCall = fullContent.contains("missed") ||
                fullContent.contains("perdida") ||
                fullContent.contains("manquée") ||
                fullContent.contains("verpasst")

        val isOngoingOrIncoming = fullContent.contains("incoming") ||
                fullContent.contains("ongoing") ||
                fullContent.contains("answering") ||
                isCallCategory

        if (isMissedCall) {
            triggerRecallButton(packageName, contactName, callType)
        } else if (isOngoingOrIncoming) {
            // Track active call so when notification is removed, we trigger recall button
            ongoingCalls[packageName] = "$contactName|$callType"
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (ongoingCalls.containsKey(packageName)) {
            val callInfo = ongoingCalls.remove(packageName) ?: return
            val parts = callInfo.split("|")
            val contactName = parts.getOrNull(0) ?: ""
            val callType = parts.getOrNull(1) ?: "voice"

            val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val featureEnabled = sharedPref.getBoolean("feature_enabled", true)
            val backgroundEnabled = sharedPref.getBoolean("background_enabled", true)
            val isWhatsAppEnabled = sharedPref.getBoolean("app_whatsapp_enabled", true)
            val isW4bEnabled = sharedPref.getBoolean("app_w4b_enabled", true)

            if (!featureEnabled || !backgroundEnabled) return
            if (packageName == WHATSAPP_PACKAGE && !isWhatsAppEnabled) return
            if (packageName == WHATSAPP_BUSINESS_PACKAGE && !isW4bEnabled) return

            triggerRecallButton(packageName, contactName, callType)
        }
    }

    private fun extractContactName(title: String, text: String): String {
        return when {
            title.isNotEmpty() && !title.lowercase().contains("whatsapp") && !title.lowercase().contains("call") -> title
            text.isNotEmpty() && !text.lowercase().contains("whatsapp") && !text.lowercase().contains("call") -> text
            else -> if (title.isNotEmpty()) title else text
        }
    }

    private fun triggerRecallButton(packageName: String, contactName: String, callType: String) {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val showInDnd = sharedPref.getBoolean("show_in_dnd", false)
        if (!showInDnd && isDndActive()) return

        val serviceIntent = Intent(this, FloatingButtonService::class.java).apply {
            putExtra("app_package", packageName)
            putExtra("contact_name", contactName)
            putExtra("call_type", callType)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun isDndActive(): Boolean {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        return notificationManager?.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
    }
}
