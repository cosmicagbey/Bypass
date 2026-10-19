package com.bypass.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.bypass.app.service.FloatingBubbleService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs: SharedPreferences =
            context.getSharedPreferences("bypass_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("start_on_boot", false)) {
            context.startForegroundService(
                Intent(context, FloatingBubbleService::class.java)
            )
        }
    }
}
