package com.example.callback

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlin.math.abs
import kotlin.math.sqrt

class FloatingButtonService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var dismissZoneView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var dismissParams: WindowManager.LayoutParams
    
    private val handler = Handler(Looper.getMainLooper())
    private var countdownAnimator: ValueAnimator? = null
    private var lastNumber: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        
        val intentNumber = intent?.getStringExtra("number")
        
        handler.postDelayed(
            {
                val (name, number) = if (!intentNumber.isNullOrEmpty()) {
                    // Try to find name for this number
                    Pair(getNameFromNumber(intentNumber), intentNumber)
                } else {
                    getLastCallerInfo()
                }
                
                lastNumber = number

                if (floatingView == null) {
                    showFloatingButton(name ?: number ?: "Unknown")
                } else {
                    try {
                        val recallButton = floatingView?.findViewById<Button>(R.id.btn_recall)
                        recallButton?.text = getString(R.string.recall_with_name, name ?: number ?: "Unknown")
                    } catch (e: Exception) { e.printStackTrace() }
                }
                
                // Only start countdown if floatingView was actually shown
                if (floatingView != null) {
                    if (countdownAnimator == null || (!(countdownAnimator!!.isRunning) && !(countdownAnimator!!.isPaused))) {
                        startCountdown()
                    }
                }
            },
            150,
        ) // Reduced delay for faster appearance

        return START_NOT_STICKY
    }

    private fun getNameFromNumber(number: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            try {
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.CACHED_NAME),
                    "${CallLog.Calls.NUMBER} = ?",
                    arrayOf(number),
                    "${CallLog.Calls.DATE} DESC"
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        return it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun startCountdown() {
        countdownAnimator?.removeAllListeners() // Remove old listeners before canceling
        countdownAnimator?.cancel()

        countdownAnimator = ValueAnimator.ofInt(10000, 0).apply {
            duration = 10000
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val level = animator.animatedValue as Int
                floatingView?.findViewById<Button>(R.id.btn_recall)?.background?.level = level
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (floatingView?.findViewById<Button>(R.id.btn_recall)?.background?.level == 0) {
                        stopSelf()
                    }
                }
            })
            start()
        }
    }
    
    // Helper to resume countdown after moving
    private fun resumeCountdown() {
        if (countdownAnimator?.isPaused == true) {
            countdownAnimator?.resume()
        } else if (countdownAnimator == null || !countdownAnimator!!.isRunning) {
            startCountdown()
        }
    }

    private fun startForegroundService() {
        val channelId = "floating_button_service_silent"
        val channelName = "Floating Button Service"
        
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN).apply {
            description = "Silent notification for floating button service"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun getLastCallerInfo(): Pair<String?, String?> {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            try {
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                        val name = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                        return Pair(name, number)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return Pair(null, null)
    }

    private fun showFloatingButton(displayName: String) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        
        try {
            // 1. Setup Dismiss Zone (Hidden initially)
            @Suppress("InflateParams")
            dismissZoneView = inflater.inflate(R.layout.layout_dismiss_zone, null)
            dismissParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                alpha = 0f // Hidden
            }
            windowManager?.addView(dismissZoneView, dismissParams)

            // 2. Setup Floating Button
            @Suppress("InflateParams")
            floatingView = inflater.inflate(R.layout.layout_floating_button, null)
            val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val savedX = sharedPref.getInt("btn_x", 0)
            val savedY = sharedPref.getInt("btn_y", 100)

            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX
                y = savedY
            }

            val recallButton = floatingView?.findViewById<Button>(R.id.btn_recall)
            recallButton?.text = getString(R.string.recall_with_name, displayName)
            
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
                            countdownAnimator?.pause()
                            
                            // Show Dismiss Zone
                            dismissParams.alpha = 1f
                            try {
                                if (dismissZoneView?.isAttachedToWindow == true) {
                                    windowManager?.updateViewLayout(dismissZoneView, dismissParams)
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            
                            if (abs(dx) > 5 || abs(dy) > 5) {
                                isMoving = true
                                params.x = initialX + dx.toInt()
                                params.y = initialY + dy.toInt()
                                try {
                                    if (floatingView?.isAttachedToWindow == true) {
                                        windowManager?.updateViewLayout(floatingView, params)
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                                
                                // Check for proximity to dismiss icon
                                updateDismissZoneScaling(event.rawX, event.rawY)
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            // Hide Dismiss Zone
                            dismissParams.alpha = 0f
                            try {
                                if (dismissZoneView?.isAttachedToWindow == true) {
                                    windowManager?.updateViewLayout(dismissZoneView, dismissParams)
                                }
                            } catch (e: Exception) { e.printStackTrace() }

                            if (isInsideDismissZone(event.rawX, event.rawY)) {
                                stopSelf()
                            } else if (!isMoving) {
                                v.performClick()
                                dialLastNumber()
                                stopSelf()
                            } else {
                                sharedPref.edit {
                                    putInt("btn_x", params.x)
                                    putInt("btn_y", params.y)
                                }
                                resumeCountdown()
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun updateDismissZoneScaling(rawX: Float, rawY: Float) {
        val dismissIcon = dismissZoneView?.findViewById<ImageView>(R.id.iv_dismiss_icon) ?: return
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        val centerX = screenWidth / 2f
        // Match the 80dp margin + 36dp (half of 72dp icon) = 116dp
        val centerY = screenHeight - 116 * density

        val distance = sqrt(((rawX - centerX) * (rawX - centerX) + (rawY - centerY) * (rawY - centerY)).toDouble())
        
        val threshold = 250 * density
        if (distance < threshold) {
            val scale = 1.0f + (threshold - distance.toFloat()) / threshold * 0.8f
            dismissIcon.scaleX = scale
            dismissIcon.scaleY = scale
        } else {
            dismissIcon.scaleX = 1.0f
            dismissIcon.scaleY = 1.0f
        }
    }

    private fun isInsideDismissZone(rawX: Float, rawY: Float): Boolean {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        val centerX = screenWidth / 2f
        // Match the 80dp margin + 36dp (half of 72dp icon) = 116dp
        val centerY = screenHeight - 116 * density
        
        val distance = sqrt(((rawX - centerX) * (rawX - centerX) + (rawY - centerY) * (rawY - centerY)).toDouble())
        // Increased radius for easier dismissal
        return distance < 100 * density
    }

    private fun dialLastNumber() {
        val number = lastNumber ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = "tel:$number".toUri()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownAnimator?.cancel()
        try {
            if (floatingView != null && floatingView?.isAttachedToWindow == true) {
                windowManager?.removeView(floatingView)
            }
            if (dismissZoneView != null && dismissZoneView?.isAttachedToWindow == true) {
                windowManager?.removeView(dismissZoneView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
