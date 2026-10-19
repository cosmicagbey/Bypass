package com.bypass.app.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bypass.app.BypassApp
import com.bypass.app.data.Platform
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as BypassApp).repository
    private val prefs: SharedPreferences =
        app.getSharedPreferences("bypass_prefs", android.content.Context.MODE_PRIVATE)

    val enabledPlatforms = repo.enabledPlatforms.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    var startOnBoot: Boolean
        get() = prefs.getBoolean("start_on_boot", false)
        set(value) = prefs.edit().putBoolean("start_on_boot", value).apply()
}
