package com.example.callback

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.content.ContextCompat
import kotlin.math.abs

class FloatingButtonService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable { stopSelf() }
    private var lastNumber: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        val (name, number) = getLastCallerInfo()
        lastNumber = number

        if (floatingView == null) {
            showFloatingButton(name ?: number ?: "Unknown")
        } else {
            // Update text if already showing
            val recallButton = floatingView?.findViewById<Button>(R.id.btn_recall)
            recallButton?.text = "Recall ${name ?: number ?: "Unknown"}"
        }
        
        // Reset 10-second timer
        handler.removeCallbacks(autoDismissRunnable)
        handler.postDelayed(autoDismissRunnable, 10000)
        
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val channelId = "floating_button_service"
        val channelName = "Floating Button Service"
        
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Recall Service Active")
            .setContentText("Monitoring calls to provide recall shortcut")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun getLastCallerInfo(): Pair<String?, String?> {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    val name = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                    return Pair(name, number)
                }
            }
        }
        return Pair(null, null)
    }

    private fun showFloatingButton(displayName: String) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null)

        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedX = sharedPref.getInt("btn_x", 0)
        val savedY = sharedPref.getInt("btn_y", 100)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = savedX
        params.y = savedY

        val recallButton = floatingView?.findViewById<Button>(R.id.btn_recall)
        recallButton?.text = "Recall $displayName"
        
        recallButton?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var isMoving = false

            @android.annotation.SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        handler.removeCallbacks(autoDismissRunnable)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isMoving = true
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager?.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoving) {
                            v.performClick()
                            dialLastNumber()
                            stopSelf()
                        } else {
                            sharedPref.edit().putInt("btn_x", params.x).putInt("btn_y", params.y).apply()
                            handler.postDelayed(autoDismissRunnable, 10000)
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(floatingView, params)
    }

    private fun dialLastNumber() {
        val number = lastNumber ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$number")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoDismissRunnable)
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
        }
    }
}