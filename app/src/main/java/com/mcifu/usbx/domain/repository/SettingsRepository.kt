package com.mcifu.usbx.domain.repository

import com.mcifu.usbx.domain.model.BrowserSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val browserSettings: StateFlow<BrowserSettings>
    suspend fun updateBrowserSettings(transform: (BrowserSettings) -> BrowserSettings)
}
