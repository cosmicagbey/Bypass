package com.bypass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bypass.app.data.Platform

// Preset emoji options for quick selection
private val EMOJI_PRESETS = listOf(
    "🎵", "👤", "🐦", "📸", "▶️", "🎬", "📹", "💬",
    "🔗", "⬇️", "💾", "🌐", "📱", "🎮", "🎶", "🔥"
)

// Preset color options
private val COLOR_PRESETS = listOf(
    "#FF010101", "#FF1877F2", "#FF000000", "#FFFF0050",
    "#FF25D366", "#FFFF4500", "#FF6441A5", "#FFBD081C",
    "#FFE1306C", "#FF0088CC", "#FF7C3AED", "#FF14171A"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPlatformSheet(
    platform: Platform?,
    existingCount: Int,
    onDismiss: () -> Unit,
    onSave: (Platform) -> Unit
) {
    val isEditing = platform != null

    var name by remember { mutableStateOf(platform?.name ?: "") }
    var emoji by remember { mutableStateOf(platform?.emoji ?: "🔗") }
    var colorHex by remember { mutableStateOf(platform?.colorHex ?: "#FF7C3AED") }
    var url by remember { mutableStateOf(platform?.downloaderUrl ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isEditing) "Edit Platform" else "Add Platform",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Preview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(parseColor(colorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 26.sp)
                }
                Column {
                    Text(
                        name.ifBlank { "Platform Name" },
                        fontWeight = FontWeight.SemiBold,
                        color = if (name.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                               else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        url.ifBlank { "https://downloader.example.com" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }

            Divider()

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Platform Name") },
                placeholder = { Text("e.g. Instagram") },
                isError = nameError,
                supportingText = if (nameError) ({ Text("Name cannot be empty") }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // URL field
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; urlError = false },
                label = { Text("Downloader URL") },
                placeholder = { Text("https://example.com/downloader") },
                isError = urlError,
                supportingText = if (urlError) ({ Text("Enter a valid URL starting with https://") }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Emoji picker
            Text("Icon Emoji", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(EMOJI_PRESETS) { e ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (e == emoji) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(e, fontSize = 20.sp)
                    }
                }
            }

            // Color picker
            Text("Accent Color", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(COLOR_PRESETS) { hex ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parseColor(hex))
                            .border(
                                width = if (hex == colorHex) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { colorHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (hex == colorHex) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Save button
            Button(
                onClick = {
                    nameError = name.isBlank()
                    urlError = url.isBlank() || !url.startsWith("http")
                    if (!nameError && !urlError) {
                        val saved = (platform ?: Platform(
                            name = name.trim(),
                            emoji = emoji,
                            colorHex = colorHex,
                            downloaderUrl = url.trim(),
                            sortOrder = existingCount
                        )).copy(
                            name = name.trim(),
                            emoji = emoji,
                            colorHex = colorHex,
                            downloaderUrl = url.trim()
                        )
                        onSave(saved)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isEditing) "Save Changes" else "Add Platform")
            }
        }
    }
}

/** Parses a color hex string like "#FFRRGGBB" or "#RRGGBB" into a Compose Color. */
fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF7C3AED)
    }
}
