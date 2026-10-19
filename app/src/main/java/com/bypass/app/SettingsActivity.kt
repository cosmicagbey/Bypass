package com.bypass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bypass.app.ui.screens.SettingsScreen
import com.bypass.app.ui.theme.BypassTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BypassTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}
