package com.bypass.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {

    @Query("SELECT * FROM platforms ORDER BY sortOrder ASC, id ASC")
    fun getAllPlatforms(): Flow<List<Platform>>

    @Query("SELECT * FROM platforms WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getEnabledPlatforms(): Flow<List<Platform>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(platform: Platform): Long

    @Update
    suspend fun update(platform: Platform)

    @Delete
    suspend fun delete(platform: Platform)

    @Query("SELECT COUNT(*) FROM platforms")
    suspend fun getCount(): Int

    @Query("UPDATE platforms SET sortOrder = :order WHERE id = :id")
    suspend fun updateOrder(id: Int, order: Int)
}
