package com.bypass.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Platform::class], version = 2, exportSchema = false)
abstract class PlatformDatabase : RoomDatabase() {

    abstract fun platformDao(): PlatformDao

    companion object {
        @Volatile
        private var INSTANCE: PlatformDatabase? = null

        /**
         * Migration 1→2: Add Instagram & YouTube platforms, update TikTok & Facebook URLs.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Update existing platform URLs
                db.execSQL("UPDATE platforms SET downloaderUrl = 'https://en1.savefrom.net/16Em/download-from-tiktok' WHERE name = 'TikTok'")
                db.execSQL("UPDATE platforms SET downloaderUrl = 'https://en1.savefrom.net/1-youtube-video-downloader-22wW/' WHERE name = 'Facebook'")

                // Insert new platforms (only if not already added by user)
                db.execSQL(
                    "INSERT INTO platforms (name, emoji, colorHex, downloaderUrl, isEnabled, isDefault, sortOrder) " +
                    "SELECT 'Instagram', '📸', '#FFE1306C', 'https://en1.savefrom.net/15xA/download-from-instagram', 1, 1, 3 " +
                    "WHERE NOT EXISTS (SELECT 1 FROM platforms WHERE name = 'Instagram')"
                )
                db.execSQL(
                    "INSERT INTO platforms (name, emoji, colorHex, downloaderUrl, isEnabled, isDefault, sortOrder) " +
                    "SELECT 'YouTube', '▶️', '#FFFF0000', 'https://en1.savefrom.net/1-youtube-video-downloader-22wW/', 1, 1, 4 " +
                    "WHERE NOT EXISTS (SELECT 1 FROM platforms WHERE name = 'YouTube')"
                )
            }
        }

        fun getDatabase(context: Context): PlatformDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlatformDatabase::class.java,
                    "bypass_db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
