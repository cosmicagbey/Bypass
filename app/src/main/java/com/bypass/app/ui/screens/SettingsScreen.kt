package com.bypass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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

    var downloadLocation by remember { mutableStateOf(viewModel.downloadLocation) }
    var showLocationDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Platforms", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
        ) {
            // Save Location Preference Card
            item {
                SaveLocationCard(
                    locationKey = downloadLocation,
                    onClick = { showLocationDialog = true }
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Platforms · Toggle to show/hide in radial menu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (platforms.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔗", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("No platforms yet", style = MaterialTheme.typography.titleLarge)
                            Text("Tap + to add one", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
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

    // Save Location Choice Dialog
    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Choose Save Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        Triple("movies", "Movies / Bypass (Recommended)", "Appears immediately in Gallery & Google Photos"),
                        Triple("downloads", "Downloads Folder", "Saved to standard /Download directory"),
                        Triple("dcim", "DCIM / Bypass", "Saved to camera & photos directory")
                    )
                    options.forEach { (key, name, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    downloadLocation = key
                                    viewModel.downloadLocation = key
                                    showLocationDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (downloadLocation == key),
                                onClick = {
                                    downloadLocation = key
                                    viewModel.downloadLocation = key
                                    showLocationDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(name, fontWeight = FontWeight.Medium)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Done")
                }
            }
        )
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
private fun SaveLocationCard(
    locationKey: String,
    onClick: () -> Unit
) {
    val (title, subtitle) = when (locationKey) {
        "downloads" -> Pair("Downloads Folder", "/Download (Standard downloads folder)")
        "dcim" -> Pair("DCIM / Bypass", "/DCIM/Bypass (Camera roll)")
        else -> Pair("Movies / Bypass", "/Movies/Bypass (Gallery - Recommended)")
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Save Location: $title",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Platform Brand Icon or Emoji Badge
            val iconRes = PlatformRepository.getPlatformIconRes(platform.name)
            val brandColor = PlatformRepository.getPlatformBrandColor(platform.name, platform.colorHex)

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(brandColor),
                contentAlignment = Alignment.Center
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = platform.name,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(platform.emoji, fontSize = 20.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(platform.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    platform.downloaderUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
            }

            if (!platform.isDefault) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                }
            }

            Switch(
                checked = platform.isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
