package com.bypass.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BypassPurple,
    onPrimary = White,
    primaryContainer = BypassPurpleDark,
    onPrimaryContainer = BypassPurpleLight,
    secondary = BypassPurpleLight,
    background = BypassSurface,
    surface = BypassSurface,
    surfaceVariant = BypassSurfaceVariant,
    onBackground = White,
    onSurface = White
)

private val LightColorScheme = lightColorScheme(
    primary = BypassPurple,
    onPrimary = White,
    primaryContainer = BypassPurpleLight,
    onPrimaryContainer = BypassPurpleDark,
    secondary = BypassPurpleDark,
    background = Gray100,
    surface = White,
    surfaceVariant = Color(0xFFF0EEFF),
    onBackground = Gray800,
    onSurface = Gray800
)

@Composable
fun BypassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BypassTypography,
        content = content
    )
}
