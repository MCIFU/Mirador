package com.mcifu.mirador.domain.repository

import com.mcifu.mirador.domain.model.AppearanceSettings
import com.mcifu.mirador.domain.model.BrowserSettings
import com.mcifu.mirador.domain.model.ViewerSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val browserSettings: StateFlow<BrowserSettings>
    suspend fun updateBrowserSettings(transform: (BrowserSettings) -> BrowserSettings)

    val viewerSettings: StateFlow<ViewerSettings>
    suspend fun updateViewerSettings(transform: (ViewerSettings) -> ViewerSettings)

    val appearance: StateFlow<AppearanceSettings>
    suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings)
}
