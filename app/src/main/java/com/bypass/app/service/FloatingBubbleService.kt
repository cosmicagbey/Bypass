package com.bypass.app.service

import android.animation.ValueAnimator
import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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

    private var isPickerOpen = false
    private var isPickerClosing = false
    private var lastBubbleX = 24
    private var lastBubbleY = 300

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository by lazy { (application as BypassApp).repository }

    // -----------------------------------------------------------------------
    // Lifecycle & Background Persistence
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
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /**
     * Called when the main app activity is swiped away from Recents.
     * We keep the foreground service and floating bubble alive!
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isRunning) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        removePickerInternal()
        removeBubble()
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // Notification
    // -----------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bypass Floating Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating video downloader accessible on your screen"
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        val bubbleSize = (44 * displayMetrics.density).toInt()
        val edgeMargin = (8 * displayMetrics.density).toInt()
        val leftEdge = edgeMargin
        val rightEdge = screenWidth - bubbleSize - edgeMargin

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = leftEdge
            y = (screenHeight * 0.35f).toInt()
        }

        lastBubbleX = params.x
        lastBubbleY = params.y

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
                        if (!isDragging && isPickerOpen) {
                            closePickerWithAnimation()
                        }
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = (initialX + dx).coerceIn(0, screenWidth - bubbleSize)
                        params.y = (initialY + dy).coerceIn(edgeMargin, screenHeight - bubbleSize - (24 * displayMetrics.density).toInt())
                        lastBubbleX = params.x
                        lastBubbleY = params.y
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // Smoothly snap bubble to whichever side is closer (left or right)
                        val targetX = if (params.x + bubbleSize / 2 < screenWidth / 2) {
                            leftEdge
                        } else {
                            rightEdge
                        }

                        ValueAnimator.ofInt(params.x, targetX).apply {
                            duration = 220
                            interpolator = DecelerateInterpolator(1.5f)
                            addUpdateListener { va ->
                                params.x = va.animatedValue as Int
                                lastBubbleX = params.x
                                try {
                                    windowManager.updateViewLayout(view, params)
                                } catch (_: Exception) {}
                            }
                            start()
                        }
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
    // Stylish Platform Picker (Slide Out / Slide In Animations)
    // -----------------------------------------------------------------------

    private fun togglePicker(bubbleX: Int, bubbleY: Int) {
        if (isPickerClosing) return

        if (isPickerOpen) {
            closePickerWithAnimation()
        } else {
            serviceScope.launch {
                val platforms = repository.enabledPlatforms.first()
                openPickerWithAnimation(platforms, bubbleX, bubbleY)
            }
        }
    }

    private fun openPickerWithAnimation(platforms: List<Platform>, bubbleX: Int, bubbleY: Int) {
        if (platforms.isEmpty()) {
            Toast.makeText(this, "No platforms enabled. Open Bypass settings to add or enable platforms.", Toast.LENGTH_LONG).show()
            return
        }

        // Animate bubble icon to ✕
        val bubbleIcon = bubbleRoot?.findViewById<TextView>(R.id.bubble_icon)
        bubbleIcon?.animate()
            ?.rotation(90f)
            ?.scaleX(0.85f)
            ?.scaleY(0.85f)
            ?.setDuration(160)
            ?.withEndAction {
                bubbleIcon.text = "✕"
                bubbleIcon.rotation = 0f
                bubbleIcon.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
            }
            ?.start()

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_platform_picker, null)
        val card = view.findViewById<View>(R.id.picker_card)
        val container = view.findViewById<LinearLayout>(R.id.picker_container)

        // Populate and configure each platform row
        platforms.forEachIndexed { index, platform ->
            val btn = inflater.inflate(R.layout.item_platform_button, container, false)
            btn.findViewById<TextView>(R.id.btn_emoji).text = platform.emoji
            btn.findViewById<TextView>(R.id.btn_label).text = platform.name

            // Dynamic tint on the emoji badge matching the platform accent color
            try {
                val colorInt = Color.parseColor(platform.colorHex)
                val badge = btn.findViewById<View>(R.id.badge_container)
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor((colorInt and 0x00FFFFFF) or 0x22000000) // ~13% soft translucent tint
                }
                badge.background = shape
            } catch (_: Exception) {}

            btn.setOnClickListener {
                btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                closePickerWithAnimation {
                    launchWebView(platform)
                }
            }

            // Initially invisible for staggered entrance ripple
            btn.alpha = 0f
            btn.translationY = 16f
            container.addView(btn)
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val pickerWidthPx = (210 * displayMetrics.density).toInt()
        val isLeft = bubbleX > screenWidth / 2

        val pickerX = if (isLeft) {
            (bubbleX - pickerWidthPx - (8 * displayMetrics.density).toInt()).coerceAtLeast(16)
        } else {
            (bubbleX + (48 * displayMetrics.density).toInt()).coerceAtMost(screenWidth - pickerWidthPx - 16)
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

        // Outside touch dismisses with smooth slide-back
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                closePickerWithAnimation()
                true
            } else {
                false
            }
        }

        // Set initial transform: tucked next to the bubble
        card.alpha = 0f
        card.scaleX = 0.25f
        card.scaleY = 0.25f
        card.translationX = if (isLeft) 60f else -60f

        try {
            windowManager.addView(view, params)
            pickerRoot = view
            isPickerOpen = true

            // Smooth spring entrance
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()

            // Staggered cascade entrance for each platform button
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(50L + i * 35L)
                    .setDuration(180)
                    .start()
            }
        } catch (_: Exception) {}
    }

    private fun closePickerWithAnimation(onEndAction: (() -> Unit)? = null) {
        if (isPickerClosing || pickerRoot == null) {
            onEndAction?.invoke()
            return
        }

        isPickerClosing = true

        // Rotate bubble icon back to ⚡
        val bubbleIcon = bubbleRoot?.findViewById<TextView>(R.id.bubble_icon)
        bubbleIcon?.animate()
            ?.rotation(-90f)
            ?.scaleX(0.85f)
            ?.scaleY(0.85f)
            ?.setDuration(150)
            ?.withEndAction {
                bubbleIcon.text = "⚡"
                bubbleIcon.rotation = 0f
                bubbleIcon.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(110)?.start()
            }
            ?.start()

        val card = pickerRoot?.findViewById<View>(R.id.picker_card)
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val isLeft = lastBubbleX > screenWidth / 2

        card?.animate()
            ?.alpha(0f)
            ?.scaleX(0.2f)
            ?.scaleY(0.2f)
            ?.translationX(if (isLeft) 60f else -60f)
            ?.setDuration(180)
            ?.setInterpolator(AccelerateInterpolator())
            ?.withEndAction {
                removePickerInternal()
                isPickerClosing = false
                isPickerOpen = false
                onEndAction?.invoke()
            }
            ?.start() ?: run {
                removePickerInternal()
                isPickerClosing = false
                isPickerOpen = false
                onEndAction?.invoke()
            }
    }

    private fun removePickerInternal() {
        pickerRoot?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        pickerRoot = null
        isPickerOpen = false
        isPickerClosing = false
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
