package com.bypass.app.data

import kotlinx.coroutines.flow.Flow

class PlatformRepository(private val dao: PlatformDao) {

    val allPlatforms: Flow<List<Platform>> = dao.getAllPlatforms()
    val enabledPlatforms: Flow<List<Platform>> = dao.getEnabledPlatforms()

    suspend fun insert(platform: Platform) = dao.insert(platform)

    suspend fun update(platform: Platform) = dao.update(platform)

    suspend fun delete(platform: Platform) = dao.delete(platform)

    suspend fun updateOrder(id: Int, order: Int) = dao.updateOrder(id, order)

    /** Seeds default platforms only on the very first launch */
    suspend fun seedDefaults() {
        if (dao.getCount() == 0) {
            val defaults = listOf(
                Platform(
                    name = "TikTok",
                    emoji = "🎵",
                    colorHex = "#FF000000",
                    downloaderUrl = "https://snaptik.app",
                    isDefault = true,
                    sortOrder = 0
                ),
                Platform(
                    name = "Facebook",
                    emoji = "👤",
                    colorHex = "#FF1877F2",
                    downloaderUrl = "https://fdown.net",
                    isDefault = true,
                    sortOrder = 1
                ),
                Platform(
                    name = "X",
                    emoji = "𝕏",
                    colorHex = "#FF000000",
                    downloaderUrl = "https://ssstwitter.com",
                    isDefault = true,
                    sortOrder = 2
                )
            )
            defaults.forEach { dao.insert(it) }
        }
    }

    companion object {
        fun getPlatformIconRes(platformName: String): Int? {
            val lower = platformName.lowercase()
            return when {
                lower.contains("tiktok") -> com.bypass.app.R.drawable.ic_logo_tiktok
                lower.contains("facebook") || lower == "fb" -> com.bypass.app.R.drawable.ic_logo_facebook
                lower.contains("twitter") || lower == "x" || lower.startsWith("x ") || lower.endsWith(" x") || lower.contains("/ x") -> com.bypass.app.R.drawable.ic_logo_x
                lower.contains("instagram") || lower.contains("insta") -> com.bypass.app.R.drawable.ic_logo_instagram
                lower.contains("youtube") || lower.contains("yt") -> com.bypass.app.R.drawable.ic_logo_youtube
                else -> null
            }
        }

        fun getPlatformBrandColor(platformName: String, fallbackColorHex: String): Int {
            val lower = platformName.lowercase()
            return when {
                lower.contains("tiktok") -> android.graphics.Color.parseColor("#FF000000")
                lower.contains("facebook") || lower == "fb" -> android.graphics.Color.parseColor("#FF1877F2")
                lower.contains("twitter") || lower == "x" || lower.contains("/ x") -> android.graphics.Color.parseColor("#FF000000")
                lower.contains("instagram") || lower.contains("insta") -> android.graphics.Color.parseColor("#FFE1306C")
                lower.contains("youtube") || lower.contains("yt") -> android.graphics.Color.parseColor("#FFFF0000")
                else -> try {
                    android.graphics.Color.parseColor(fallbackColorHex)
                } catch (_: Exception) {
                    android.graphics.Color.parseColor("#FF7C3AED")
                }
            }
        }
    }
}
