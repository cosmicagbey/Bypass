package com.bypass.app

import android.app.Application
import com.bypass.app.data.PlatformDatabase
import com.bypass.app.data.PlatformRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BypassApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { PlatformDatabase.getDatabase(this) }
    val repository by lazy { PlatformRepository(database.platformDao()) }

    override fun onCreate() {
        super.onCreate()
        // Seed default platforms on first launch
        applicationScope.launch {
            repository.seedDefaults()
        }
    }
}
