package com.mcifu.usbx.domain.repository

import com.mcifu.usbx.domain.model.AppearanceSettings
import com.mcifu.usbx.domain.model.BrowserSettings
import com.mcifu.usbx.domain.model.ViewerSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val browserSettings: StateFlow<BrowserSettings>
    suspend fun updateBrowserSettings(transform: (BrowserSettings) -> BrowserSettings)

    val viewerSettings: StateFlow<ViewerSettings>
    suspend fun updateViewerSettings(transform: (ViewerSettings) -> ViewerSettings)

    val appearance: StateFlow<AppearanceSettings>
    suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings)
}
