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
import com.bypass.app.data.PlatformRepository
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
    // -----------------------------------------------------------------------
    // Radial Platform Menu (Fanned Out Circle Around Bubble)
    // -----------------------------------------------------------------------

    private fun togglePicker(bubbleX: Int, bubbleY: Int) {
        if (isPickerClosing) return

        if (isPickerOpen) {
            closePickerWithAnimation()
        } else {
            serviceScope.launch {
                val platforms = repository.enabledPlatforms.first()
                openRadialMenuWithAnimation(platforms, bubbleX, bubbleY)
            }
        }
    }

    private fun openRadialMenuWithAnimation(platforms: List<Platform>, bubbleX: Int, bubbleY: Int) {
        if (platforms.isEmpty()) {
            Toast.makeText(this, "No platforms enabled. Open Bypass settings to enable platforms.", Toast.LENGTH_LONG).show()
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
        val view = inflater.inflate(R.layout.view_radial_menu, null)
        val container = view.findViewById<FrameLayout>(R.id.radial_container)
        val backdrop = view.findViewById<View>(R.id.radial_backdrop)

        backdrop.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                closePickerWithAnimation()
                true
            } else {
                false
            }
        }

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val bubbleSize = (44 * density).toInt()
        val buttonSize = (44 * density).toInt()

        val centerX = bubbleX + bubbleSize / 2
        val centerY = bubbleY + bubbleSize / 2
        val isLeft = centerX < screenWidth / 2

        // Radial geometry: fan out ~76dp from bubble center
        val radius = 76 * density
        val count = platforms.size

        // Calculate arc angles depending on which screen edge the bubble is resting on
        val angles = when (count) {
            1 -> if (isLeft) doubleArrayOf(0.0) else doubleArrayOf(180.0)
            2 -> if (isLeft) doubleArrayOf(-32.0, 32.0) else doubleArrayOf(148.0, 212.0)
            3 -> if (isLeft) doubleArrayOf(-48.0, 0.0, 48.0) else doubleArrayOf(132.0, 180.0, 228.0)
            4 -> if (isLeft) doubleArrayOf(-54.0, -18.0, 18.0, 54.0) else doubleArrayOf(126.0, 162.0, 198.0, 234.0)
            else -> {
                val sweep = 120.0
                val start = if (isLeft) -sweep / 2.0 else 180.0 - sweep / 2.0
                val step = sweep / (count - 1).coerceAtLeast(1)
                DoubleArray(count) { i -> start + i * step }
            }
        }

        // Tilt arc if bubble is near top or bottom screen boundary
        val tilt = when {
            centerY < 130 * density -> if (isLeft) 24.0 else -24.0
            centerY > screenHeight - (130 * density) -> if (isLeft) -24.0 else 24.0
            else -> 0.0
        }

        platforms.forEachIndexed { i, platform ->
            val btn = inflater.inflate(R.layout.item_radial_button, container, false) as androidx.cardview.widget.CardView

            // Brand background color
            val brandColor = PlatformRepository.getPlatformBrandColor(platform.name, platform.colorHex)
            btn.setCardBackgroundColor(brandColor)

            // Setup real-time brand icon or fallback emoji
            val iconRes = PlatformRepository.getPlatformIconRes(platform.name)
            val iconView = btn.findViewById<ImageView>(R.id.radial_button_icon)
            val emojiView = btn.findViewById<TextView>(R.id.radial_button_emoji)

            if (iconRes != null) {
                iconView.setImageResource(iconRes)
                iconView.visibility = View.VISIBLE
                emojiView.visibility = View.GONE
            } else {
                emojiView.text = platform.emoji
                emojiView.visibility = View.VISIBLE
                iconView.visibility = View.GONE
            }

            // Position initially centered at the bubble's center
            val lp = FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
                leftMargin = centerX - buttonSize / 2
                topMargin = centerY - buttonSize / 2
            }
            btn.layoutParams = lp

            btn.translationX = 0f
            btn.translationY = 0f
            btn.scaleX = 0f
            btn.scaleY = 0f
            btn.alpha = 0f

            btn.setOnClickListener {
                btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                btn.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(90)
                    .withEndAction {
                        closePickerWithAnimation {
                            launchWebView(platform)
                        }
                    }
                    .start()
            }

            container.addView(btn)

            // Calculate radial offset
            val angleDeg = angles[i] + tilt
            val rad = Math.toRadians(angleDeg)
            val deltaX = (radius * Math.cos(rad)).toFloat()
            val deltaY = (radius * Math.sin(rad)).toFloat()

            // Smooth spring fan-out animation
            btn.animate()
                .translationX(deltaX)
                .translationY(deltaY)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(260)
                .setStartDelay((i * 35).toLong())
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(view, params)
            pickerRoot = view
            isPickerOpen = true
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

        val container = pickerRoot?.findViewById<FrameLayout>(R.id.radial_container)
        val childCount = container?.childCount ?: 0

        if (childCount == 0) {
            removePickerInternal()
            onEndAction?.invoke()
            return
        }

        for (i in 0 until childCount) {
            val child = container?.getChildAt(i)
            val isLast = (i == childCount - 1)
            child?.animate()
                ?.translationX(0f)
                ?.translationY(0f)
                ?.scaleX(0f)
                ?.scaleY(0f)
                ?.alpha(0f)
                ?.setDuration(170)
                ?.setStartDelay(((childCount - 1 - i) * 20).toLong())
                ?.setInterpolator(AccelerateInterpolator())
                ?.withEndAction {
                    if (isLast) {
                        removePickerInternal()
                        onEndAction?.invoke()
                    }
                }
                ?.start()
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
