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
                    colorHex = "#FF010101",
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
                    name = "Twitter / X",
                    emoji = "🐦",
                    colorHex = "#FF000000",
                    downloaderUrl = "https://ssstwitter.com",
                    isDefault = true,
                    sortOrder = 2
                )
            )
            defaults.forEach { dao.insert(it) }
        }
    }
}
