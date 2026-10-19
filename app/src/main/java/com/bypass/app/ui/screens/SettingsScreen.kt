package com.bypass.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bypass.app.data.Platform
import com.bypass.app.data.PlatformRepository
import com.bypass.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val platforms by viewModel.allPlatforms.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var editingPlatform by remember { mutableStateOf<Platform?>(null) }
    var deletingPlatform by remember { mutableStateOf<Platform?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Platforms", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Platform") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        if (platforms.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔗", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No platforms yet", style = MaterialTheme.typography.titleLarge)
                    Text("Tap + to add one", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
            ) {
                item {
                    Text(
                        "Tap to edit · Toggle to show/hide in picker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(platforms, key = { it.id }) { platform ->
                    PlatformListItem(
                        platform = platform,
                        onEdit = { editingPlatform = platform },
                        onDelete = { deletingPlatform = platform },
                        onToggle = { viewModel.toggleEnabled(platform) }
                    )
                }
            }
        }
    }

    // Add sheet
    if (showAddSheet) {
        AddEditPlatformSheet(
            platform = null,
            existingCount = platforms.size,
            onDismiss = { showAddSheet = false },
            onSave = { p ->
                viewModel.addPlatform(p)
                showAddSheet = false
            }
        )
    }

    // Edit sheet
    editingPlatform?.let { p ->
        AddEditPlatformSheet(
            platform = p,
            existingCount = platforms.size,
            onDismiss = { editingPlatform = null },
            onSave = { updated ->
                viewModel.updatePlatform(updated)
                editingPlatform = null
            }
        )
    }

    // Delete confirmation dialog
    deletingPlatform?.let { p ->
        AlertDialog(
            onDismissRequest = { deletingPlatform = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFEF4444)) },
            title = { Text("Delete ${p.name}?") },
            text = { Text("This platform will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlatform(p)
                    deletingPlatform = null
                }) { Text("Delete", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingPlatform = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformListItem(
    platform: Platform,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Brand icon badge or emoji
            val iconRes = PlatformRepository.getPlatformIconRes(platform.name)
            if (iconRes != null) {
                Surface(
                    shape = CircleShape,
                    color = Color(PlatformRepository.getPlatformBrandColor(platform.name, platform.colorHex)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = platform.name,
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Text(platform.emoji, fontSize = 28.sp)
            }

            // Name + URL
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(platform.name, fontWeight = FontWeight.SemiBold)
                    if (platform.isDefault) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "default",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    platform.downloaderUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1
                )
            }

            // Enable/disable toggle
            Switch(
                checked = platform.isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(40.dp, 24.dp)
            )

            // Delete
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
