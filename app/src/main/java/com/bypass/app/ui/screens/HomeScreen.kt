package com.bypass.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bypass.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hasOverlayPermission: () -> Boolean,
    isServiceRunning: () -> Boolean,
    onRequestOverlayPermission: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val permissionGranted = hasOverlayPermission()
    val serviceRunning = isServiceRunning()
    val platforms by viewModel.enabledPlatforms.collectAsState()
    var startOnBoot by remember { mutableStateOf(viewModel.startOnBoot) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Bypass",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Hero card
            HeroCard(serviceRunning = serviceRunning)

            // Permission card
            if (!permissionGranted) {
                PermissionCard(onGrant = {
                    onRequestOverlayPermission()
                    permissionGranted = hasOverlayPermission()
                })
            }

            // Service toggle card
            ServiceToggleCard(
                enabled = permissionGranted,
                running = serviceRunning,
                onToggle = {
                    onToggleService(serviceRunning)
                }
            )

            // Boot toggle
            SettingToggleRow(
                icon = Icons.Default.Autorenew,
                title = "Start on boot",
                subtitle = "Bubble appears automatically after device restart",
                checked = startOnBoot,
                onCheckedChange = {
                    startOnBoot = it
                    viewModel.startOnBoot = it
                }
            )

            // Active platforms preview
            if (platforms.isNotEmpty()) {
                ActivePlatformsCard(platforms = platforms.map { "${it.emoji} ${it.name}" })
            }

            // Settings shortcut
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Platforms")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroCard(serviceRunning: Boolean) {
    val statusColor = if (serviceRunning) Color(0xFF22C55E) else Color(0xFFEF4444)
    val statusText = if (serviceRunning) "Active" else "Inactive"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚡", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Floating Video Downloader",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3CD)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B))
            Column(Modifier.weight(1f)) {
                Text(
                    "Overlay Permission Needed",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF92400E)
                )
                Text(
                    "Required to show the floating bubble",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF78350F)
                )
            }
            TextButton(onClick = onGrant) {
                Text("Grant", color = Color(0xFFF59E0B))
            }
        }
    }
}

@Composable
private fun ServiceToggleCard(enabled: Boolean, running: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (running) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (running) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (running) "Bubble is running" else "Bubble is stopped",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (running) "Tap the floating ⚡ on your screen" else "Tap to start the floating bubble",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Button(
                onClick = onToggle,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) Color(0xFFEF4444) else Color(0xFF22C55E)
                )
            ) {
                Text(if (running) "Stop" else "Start")
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActivePlatformsCard(platforms: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Active Platforms", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            platforms.forEach { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
