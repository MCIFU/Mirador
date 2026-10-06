package com.mcifu.usbx.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcifu.usbx.domain.model.AppearanceSettings
import com.mcifu.usbx.domain.model.BrowserSettings
import com.mcifu.usbx.domain.model.ViewerSettings
import com.mcifu.usbx.domain.repository.SettingsRepository
import com.mcifu.usbx.ui.common.appContainer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val appearance: StateFlow<AppearanceSettings> = settings.appearance
    val viewer: StateFlow<ViewerSettings> = settings.viewerSettings
    val browser: StateFlow<BrowserSettings> = settings.browserSettings

    fun updateAppearance(t: (AppearanceSettings) -> AppearanceSettings) = viewModelScope.launch { settings.updateAppearance(t) }
    fun updateViewer(t: (ViewerSettings) -> ViewerSettings) = viewModelScope.launch { settings.updateViewerSettings(t) }
    fun updateBrowser(t: (BrowserSettings) -> BrowserSettings) = viewModelScope.launch { settings.updateBrowserSettings(t) }

    companion object {
        val Factory = viewModelFactory { initializer { SettingsViewModel(appContainer.settingsRepository) } }
    }
}
