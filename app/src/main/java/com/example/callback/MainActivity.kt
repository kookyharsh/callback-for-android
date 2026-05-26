package com.example.callback

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
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

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            checkOverlayPermission()
        } else {
            Toast.makeText(this, "Permissions required for app functionality", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Handling window insets for better UI appearance
        val mainView = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toggleFeature = findViewById<SwitchCompat>(R.id.toggleFeature)
        val toggleBackground = findViewById<SwitchCompat>(R.id.toggleBackground)
        val btnBattery = findViewById<Button>(R.id.btnBatteryOptimization)
        val tvBatteryStatus = findViewById<TextView>(R.id.tvBatteryStatus)
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        
        updateBatteryStatus(tvBatteryStatus)
        updateSwitchColors(toggleFeature)
        updateSwitchColors(toggleBackground)

        btnBattery.setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }
        
        // Load saved state
        toggleFeature.isChecked = sharedPref.getBoolean("feature_enabled", true)
        toggleBackground.isChecked = sharedPref.getBoolean("background_enabled", true)

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
                Toast.makeText(this, "Background service Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Background service Disabled", Toast.LENGTH_SHORT).show()
            }
        }

        if (toggleFeature.isChecked) {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG
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
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun updateBatteryStatus(textView: TextView) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            textView.text = "Status: Optimization Disabled (App runs freely)"
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            textView.text = "Status: Optimization Active (System may kill app)"
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun updateSwitchColors(switch: SwitchCompat) {
        val thumbColor = if (switch.isChecked) {
            ContextCompat.getColor(this, R.color.logo_blue)
        } else {
            ContextCompat.getColor(this, R.color.status_red)
        }

        val trackColor = if (switch.isChecked) {
            ContextCompat.getColor(this, R.color.logo_blue_light)
        } else {
            ContextCompat.getColor(this, R.color.status_red_light)
        }

        switch.thumbTintList = ColorStateList.valueOf(thumbColor)
        switch.trackTintList = ColorStateList.valueOf(trackColor)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val tvBatteryStatus = findViewById<TextView>(R.id.tvBatteryStatus)
        if (tvBatteryStatus != null) {
            updateBatteryStatus(tvBatteryStatus)
        }
    }
}
