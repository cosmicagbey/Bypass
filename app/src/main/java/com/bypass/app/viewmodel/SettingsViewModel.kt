package com.bypass.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bypass.app.BypassApp
import com.bypass.app.data.Platform
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as BypassApp).repository

    val allPlatforms = repo.allPlatforms.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun addPlatform(platform: Platform) = viewModelScope.launch {
        repo.insert(platform.copy(sortOrder = allPlatforms.value.size))
    }

    fun updatePlatform(platform: Platform) = viewModelScope.launch {
        repo.update(platform)
    }

    fun deletePlatform(platform: Platform) = viewModelScope.launch {
        repo.delete(platform)
    }

    fun toggleEnabled(platform: Platform) = viewModelScope.launch {
        repo.update(platform.copy(isEnabled = !platform.isEnabled))
    }

    fun reorder(from: Int, to: Int) = viewModelScope.launch {
        val list = allPlatforms.value.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        list.forEachIndexed { index, p -> repo.updateOrder(p.id, index) }
    }
}
