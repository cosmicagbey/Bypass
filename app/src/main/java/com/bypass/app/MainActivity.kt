package com.bypass.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.bypass.app.service.FloatingBubbleService
import com.bypass.app.ui.screens.HomeScreen
import com.bypass.app.ui.theme.BypassTheme

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkStates()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission granted or denied, proceed with service
        startBubbleServiceInternal()
    }

    private var hasOverlayState = mutableStateOf(false)
    private var isServiceRunningState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkStates()

        setContent {
            BypassTheme {
                HomeScreen(
                    hasOverlayPermission = { hasOverlayState.value },
                    isServiceRunning = { isServiceRunningState.value },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onToggleService = { running ->
                        if (running) {
                            stopBubbleService()
                        } else {
                            startBubbleService()
                        }
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkStates()
    }

    private fun checkStates() {
        hasOverlayState.value = Settings.canDrawOverlays(this)
        isServiceRunningState.value = FloatingBubbleService.isRunning
    }

    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            overlayPermissionLauncher.launch(fallback)
        }
    }

    private fun startBubbleService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startBubbleServiceInternal()
        }
    }

    private fun startBubbleServiceInternal() {
        try {
            val intent = Intent(this, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isServiceRunningState.value = true
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start overlay: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopBubbleService() {
        try {
            stopService(Intent(this, FloatingBubbleService::class.java))
            isServiceRunningState.value = false
        } catch (_: Exception) {}
    }
}
