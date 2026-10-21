package com.bypass.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import com.bypass.app.R

/**
 * Displays a lightweight, non-intrusive on-screen notification at the lower part
 * of the screen for 0.5 - 1.0 second when a download finishes.
 *
 * Uses WindowManager overlay (FLAG_NOT_TOUCHABLE) so touches pass straight through
 * to whatever app the user is actively using without interruption.
 */
object DownloadToastOverlay {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentWindowManager: WindowManager? = null
    private var hideRunnable: Runnable? = null

    fun show(
        context: Context,
        title: String = "Download finished",
        locationText: String,
        durationMs: Long = 950L,
        onDismiss: (() -> Unit)? = null
    ) {
        mainHandler.post {
            val appContext = context.applicationContext

            // Fallback to standard Toast if overlay permission is not granted
            if (!Settings.canDrawOverlays(appContext)) {
                Toast.makeText(
                    appContext,
                    "✅ $title\nSaved to $locationText",
                    Toast.LENGTH_SHORT
                ).show()
                onDismiss?.invoke()
                return@post
            }

            try {
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                // Clear any previous banner immediately to prevent stacking
                dismissImmediately()

                val inflater = LayoutInflater.from(appContext)
                val view = inflater.inflate(R.layout.view_download_toast, null)

                val tvTitle = view.findViewById<TextView>(R.id.tv_toast_title)
                val tvLocation = view.findViewById<TextView>(R.id.tv_toast_location)

                tvTitle.text = title
                tvLocation.text = "Saved to $locationText"

                val density = appContext.resources.displayMetrics.density
                val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = (80 * density).toInt() // Positioned nicely in the lower portion of the screen
                }

                view.alpha = 0f
                view.translationY = 32f * density

                wm.addView(view, params)
                currentView = view
                currentWindowManager = wm

                // Smooth entrance animation (fade-in & slide up)
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(160)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                // Auto-dismiss after durationMs (approx 0.8 - 1.0s display)
                val dismissRunnable = Runnable {
                    if (currentView == view) {
                        view.animate()
                            .alpha(0f)
                            .translationY(24f * density)
                            .setDuration(180)
                            .setInterpolator(AccelerateInterpolator())
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    dismissImmediately()
                                    onDismiss?.invoke()
                                }
                            })
                            .start()
                    } else {
                        onDismiss?.invoke()
                    }
                }

                hideRunnable = dismissRunnable
                mainHandler.postDelayed(dismissRunnable, durationMs)

            } catch (e: Exception) {
                dismissImmediately()
                try {
                    Toast.makeText(
                        appContext,
                        "✅ $title\nSaved to $locationText",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: Exception) {}
                onDismiss?.invoke()
            }
        }
    }

    private fun dismissImmediately() {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        hideRunnable = null

        val v = currentView
        val wm = currentWindowManager
        currentView = null
        currentWindowManager = null

        if (v != null && wm != null) {
            try {
                wm.removeView(v)
            } catch (_: Exception) {}
        }
    }
}
