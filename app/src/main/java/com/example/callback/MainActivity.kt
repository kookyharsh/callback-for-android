package com.example.callback

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val deniedPermissions = permissions.filter { !it.value }.keys
        if (deniedPermissions.isEmpty()) {
            checkOverlayPermission()
        } else {
            val permissionNames = deniedPermissions.asSequence().map { permission ->
                permission.substringAfterLast(".").replace("_", " ")
            }.joinToString(", ")
            
            Toast.makeText(this, "Missing: $permissionNames", Toast.LENGTH_LONG).show()
            
            if (permissions.any { it.value }) {
                checkOverlayPermission()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        val mainView = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toggleFeature = findViewById<SwitchCompat>(R.id.toggleFeature)
        val toggleBackground = findViewById<SwitchCompat>(R.id.toggleBackground2)
        val toggleDnd = findViewById<SwitchCompat>(R.id.toggleDnd)
        val toggleCellular = findViewById<SwitchCompat>(R.id.toggleCellular)
        val toggleWhatsApp = findViewById<SwitchCompat>(R.id.toggleWhatsApp)
        val toggleW4B = findViewById<SwitchCompat>(R.id.toggleW4B)

        val btnBattery = findViewById<Button>(R.id.btnBatteryOptimization)
        val tvBatteryStatus = findViewById<TextView>(R.id.tvBatteryStatus)
        val btnNotificationAccess = findViewById<Button>(R.id.btnNotificationAccess)
        val tvNotificationStatus = findViewById<TextView>(R.id.tvNotificationStatus)

        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        
        toggleFeature.isChecked = sharedPref.getBoolean("feature_enabled", true)
        toggleBackground.isChecked = sharedPref.getBoolean("background_enabled", true)
        toggleDnd.isChecked = sharedPref.getBoolean("show_in_dnd", false)
        toggleCellular.isChecked = sharedPref.getBoolean("app_cellular_enabled", true)
        toggleWhatsApp.isChecked = sharedPref.getBoolean("app_whatsapp_enabled", true)
        toggleW4B.isChecked = sharedPref.getBoolean("app_w4b_enabled", true)

        updateSwitchColors(toggleFeature)
        updateSwitchColors(toggleBackground)
        updateSwitchColors(toggleDnd)
        updateSwitchColors(toggleCellular)
        updateSwitchColors(toggleWhatsApp)
        updateSwitchColors(toggleW4B)

        updateBatteryStatus(tvBatteryStatus)
        updateNotificationListenerStatus(tvNotificationStatus)

        btnBattery.setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }

        btnNotificationAccess.setOnClickListener {
            openNotificationListenerSettings()
        }

        toggleFeature.setOnCheckedChangeListener { switch, isChecked ->
            updateSwitchColors(switch as SwitchCompat)
            sharedPref.edit { putBoolean("feature_enabled", isChecked) }
            if (isChecked) {
                requestPermissions()
            }
        }

        toggleBackground.setOnCheckedChangeListener { switch, isChecked ->
            updateSwitchColors(switch as SwitchCompat)
            sharedPref.edit { putBoolean("background_enabled", isChecked) }
            if (isChecked) {
                Toast.makeText(this, getString(R.string.persistence_enabled), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.persistence_disabled), Toast.LENGTH_SHORT).show()
            }
        }

        toggleDnd.setOnCheckedChangeListener { switch, isChecked ->
            updateSwitchColors(switch as SwitchCompat)
            sharedPref.edit { putBoolean("show_in_dnd", isChecked) }
        }

        toggleCellular.setOnCheckedChangeListener { switch, isChecked ->
            updateSwitchColors(switch as SwitchCompat)
            sharedPref.edit { putBoolean("app_cellular_enabled", isChecked) }
        }

        toggleWhatsApp.setOnCheckedChangeListener { switch, isChecked ->
            updateSwitchColors(switch as SwitchCompat)
            sharedPref.edit { putBoolean("app_whatsapp_enabled", isChecked) }
            if (isChecked && !isNotificationListenerEnabled()) {
                openNotificationListenerSettings()
            }
        }

        toggleW4B.setOnCheckedChangeListener { switch, isChecked ->
            updateSwitchColors(switch as SwitchCompat)
            sharedPref.edit { putBoolean("app_w4b_enabled", isChecked) }
            if (isChecked && !isNotificationListenerEnabled()) {
                openNotificationListenerSettings()
            }
        }

        if (toggleFeature.isChecked) {
            requestPermissions()
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Please enable Notification Access for Callback", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to open notification settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNotificationListenerStatus(textView: TextView) {
        if (isNotificationListenerEnabled()) {
            textView.text = getString(R.string.notification_listener_active)
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            textView.text = getString(R.string.notification_listener_inactive)
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest)
        } else {
            checkOverlayPermission()
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            try {
                startActivity(intent)
                Toast.makeText(this, "Please enable 'Appear on top' for Callback", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                val genericIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(genericIntent)
            }
        }
    }

    private fun updateBatteryStatus(textView: TextView) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            textView.text = getString(R.string.optimization_disabled)
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            textView.text = getString(R.string.optimization_active)
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun updateSwitchColors(switch: SwitchCompat) {
        val thumbColor = if (switch.isChecked) {
            ContextCompat.getColor(this, R.color.toggle_on)
        } else {
            ContextCompat.getColor(this, R.color.toggle_off)
        }

        val trackColor = if (switch.isChecked) {
            ContextCompat.getColor(this, R.color.toggle_on_track)
        } else {
            ContextCompat.getColor(this, R.color.toggle_off_track)
        }

        switch.thumbTintList = ColorStateList.valueOf(thumbColor)
        switch.trackTintList = ColorStateList.valueOf(trackColor)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                @android.annotation.SuppressLint("BatteryLife")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.could_not_open_battery), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.battery_already_disabled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val tvBatteryStatus = findViewById<TextView>(R.id.tvBatteryStatus)
        if (tvBatteryStatus != null) {
            updateBatteryStatus(tvBatteryStatus)
        }
        val tvNotificationStatus = findViewById<TextView>(R.id.tvNotificationStatus)
        if (tvNotificationStatus != null) {
            updateNotificationListenerStatus(tvNotificationStatus)
        }
    }
}
