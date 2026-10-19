package com.bypass.app.service

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.bypass.app.BypassApp
import com.bypass.app.MainActivity
import com.bypass.app.R
import com.bypass.app.data.Platform
import com.bypass.app.ui.WebViewActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class FloatingBubbleService : Service() {

    companion object {
        const val CHANNEL_ID = "bypass_bubble_channel"
        const val NOTIF_ID = 101
        const val ACTION_STOP = "com.bypass.app.ACTION_STOP_SERVICE"
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var bubbleRoot: View? = null
    private var pickerRoot: View? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository by lazy { (application as BypassApp).repository }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), 0)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        removeBubble()
        removePicker()
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // Notification
    // -----------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bypass Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating downloader overlay button"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingBubbleService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bypass is active")
            .setContentText("Tap the floating ⚡ bubble on screen to download")
            .setSmallIcon(R.drawable.ic_bubble_notification)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    // -----------------------------------------------------------------------
    // Floating Bubble
    // -----------------------------------------------------------------------

    private fun showBubble() {
        if (bubbleRoot != null) return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_floating_bubble, null)

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val bubbleSize = (64 * displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = (screenHeight * 0.35f).toInt()
        }

        // Drag & Click logic
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = (initialX + dx).coerceIn(0, screenWidth - bubbleSize)
                        params.y = (initialY + dy).coerceIn(0, screenHeight - bubbleSize)
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        togglePicker(params.x, params.y)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
            bubbleRoot = view
        } catch (_: Exception) {}
    }

    private fun removeBubble() {
        bubbleRoot?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        bubbleRoot = null
    }

    // -----------------------------------------------------------------------
    // Platform Picker
    // -----------------------------------------------------------------------

    private fun togglePicker(bubbleX: Int, bubbleY: Int) {
        if (pickerRoot != null) {
            removePicker()
            return
        }
        serviceScope.launch {
            val platforms = repository.enabledPlatforms.first()
            showPicker(platforms, bubbleX, bubbleY)
        }
    }

    private fun showPicker(platforms: List<Platform>, bubbleX: Int, bubbleY: Int) {
        if (platforms.isEmpty()) {
            Toast.makeText(this, "No platforms enabled. Add or enable platforms in Bypass.", Toast.LENGTH_LONG).show()
            return
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_platform_picker, null)
        val container = view.findViewById<LinearLayout>(R.id.picker_container)

        platforms.forEach { platform ->
            val btn = inflater.inflate(R.layout.item_platform_button, container, false)
            btn.findViewById<TextView>(R.id.btn_emoji).text = platform.emoji
            btn.findViewById<TextView>(R.id.btn_label).text = platform.name
            btn.setOnClickListener {
                removePicker()
                launchWebView(platform)
            }
            container.addView(btn)
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val pickerWidthPx = (180 * displayMetrics.density).toInt()

        val pickerX = if (bubbleX > screenWidth / 2) {
            (bubbleX - pickerWidthPx).coerceAtLeast(16)
        } else {
            (bubbleX + (70 * displayMetrics.density).toInt()).coerceAtMost(screenWidth - pickerWidthPx - 16)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pickerX
            y = bubbleY.coerceAtLeast(24)
        }

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                removePicker()
                true
            } else {
                false
            }
        }

        try {
            windowManager.addView(view, params)
            pickerRoot = view
        } catch (_: Exception) {}
    }

    private fun removePicker() {
        pickerRoot?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        pickerRoot = null
    }

    // -----------------------------------------------------------------------
    // Launch WebView
    // -----------------------------------------------------------------------

    private fun launchWebView(platform: Platform) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        val copiedUrl = if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0)?.text?.toString() ?: ""
        } else {
            ""
        }

        val intent = Intent(this, WebViewActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(WebViewActivity.EXTRA_URL, platform.downloaderUrl)
            putExtra(WebViewActivity.EXTRA_PLATFORM_NAME, platform.name)
            putExtra(WebViewActivity.EXTRA_CLIPBOARD_URL, copiedUrl)
        }
        startActivity(intent)
    }
}
