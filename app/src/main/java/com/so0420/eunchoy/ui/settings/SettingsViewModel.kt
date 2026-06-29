package com.so0420.eunchoy.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.so0420.eunchoy.AppContainer
import com.so0420.eunchoy.data.NotifyMode
import com.so0420.eunchoy.data.SourceKey
import com.so0420.eunchoy.data.settings.AppSettings
import com.so0420.eunchoy.work.PollScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    container: AppContainer,
    private val appContext: Context,
) : ViewModel() {

    private val settings = container.settings

    val state: StateFlow<AppSettings?> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMode(key: SourceKey, mode: NotifyMode) = viewModelScope.launch {
        settings.setMode(key, mode)
    }

    fun setFastPolling(value: Boolean) = viewModelScope.launch {
        settings.setFastPolling(value)
        if (value) PollScheduler.startFast(appContext) else PollScheduler.stopFast(appContext)
    }

    fun setAutoOpenLive(value: Boolean) = viewModelScope.launch { settings.setAutoOpenLive(value) }

    fun setPollMinutes(minutes: Int) = viewModelScope.launch {
        settings.setPollMinutes(minutes)
        PollScheduler.ensurePeriodic(appContext, minutes)
    }

    fun setXBridge(url: String) = viewModelScope.launch { settings.setXBridge(url) }

    fun saveNaver(aut: String, ses: String) = viewModelScope.launch {
        settings.setNaverCookies(aut, ses)
    }

    fun logoutNaver() = viewModelScope.launch { settings.setNaverCookies(null, null) }
}
