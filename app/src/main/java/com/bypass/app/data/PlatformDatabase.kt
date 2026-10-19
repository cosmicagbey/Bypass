package com.bypass.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Platform::class], version = 1, exportSchema = false)
abstract class PlatformDatabase : RoomDatabase() {

    abstract fun platformDao(): PlatformDao

    companion object {
        @Volatile
        private var INSTANCE: PlatformDatabase? = null

        fun getDatabase(context: Context): PlatformDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlatformDatabase::class.java,
                    "bypass_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
