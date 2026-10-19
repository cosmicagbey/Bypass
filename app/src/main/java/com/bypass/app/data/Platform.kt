package com.bypass.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a social media platform and its associated video downloader URL.
 *
 * @param id          Auto-generated primary key
 * @param name        Display name (e.g. "TikTok", "Facebook")
 * @param emoji       Emoji icon shown on the floating picker
 * @param colorHex    ARGB hex color string (e.g. "#FF010101")
 * @param downloaderUrl Full URL of the downloader website
 * @param isEnabled   Whether to show this platform in the picker
 * @param isDefault   True for the pre-seeded platforms
 * @param sortOrder   Display order in the picker
 */
@Entity(tableName = "platforms")
data class Platform(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val downloaderUrl: String,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
)
