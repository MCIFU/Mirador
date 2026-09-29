package com.mcifu.usbx.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mcifu.usbx.domain.model.BrowserSettings
import com.mcifu.usbx.domain.model.SortOrder
import com.mcifu.usbx.domain.model.ViewerSettings
import com.mcifu.usbx.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(
    context: Context,
    appScope: CoroutineScope,
) : SettingsRepository {

    private val store = context.settingsStore

    override val browserSettings: StateFlow<BrowserSettings> = store.data
        .map { it.toBrowserSettings() }
        .stateIn(appScope, SharingStarted.Eagerly, BrowserSettings())

    override suspend fun updateBrowserSettings(transform: (BrowserSettings) -> BrowserSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toBrowserSettings())
            prefs[VIEW_MODE] = updated.viewMode.name
            prefs[THUMBNAIL_SIZE] = updated.thumbnailSize.name
            prefs[COLUMNS] = updated.columns
            prefs[SORT_FIELD] = updated.sortOrder.field.name
            prefs[SORT_ASCENDING] = updated.sortOrder.ascending
            prefs[FOLDERS_FIRST] = updated.sortOrder.foldersFirst
            prefs[SHOW_HIDDEN] = updated.showHidden
        }
    }

    override val viewerSettings: StateFlow<ViewerSettings> = store.data
        .map { it.toViewerSettings() }
        .stateIn(appScope, SharingStarted.Eagerly, ViewerSettings())

    override suspend fun updateViewerSettings(transform: (ViewerSettings) -> ViewerSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toViewerSettings())
            prefs[VIEWER_SCOPE] = updated.scope.name
            prefs[VIEWER_ORIENTATION] = updated.orientation.name
            prefs[VIEWER_FILMSTRIP] = updated.showFilmstrip
        }
    }

    private fun Preferences.toViewerSettings(): ViewerSettings {
        val defaults = ViewerSettings()
        return ViewerSettings(
            scope = enumOrDefault(this[VIEWER_SCOPE], defaults.scope),
            orientation = enumOrDefault(this[VIEWER_ORIENTATION], defaults.orientation),
            showFilmstrip = this[VIEWER_FILMSTRIP] ?: defaults.showFilmstrip,
        )
    }

    private fun Preferences.toBrowserSettings(): BrowserSettings {
        val defaults = BrowserSettings()
        return BrowserSettings(
            viewMode = enumOrDefault(this[VIEW_MODE], defaults.viewMode),
            thumbnailSize = enumOrDefault(this[THUMBNAIL_SIZE], defaults.thumbnailSize),
            columns = this[COLUMNS] ?: defaults.columns,
            sortOrder = SortOrder(
                field = enumOrDefault(this[SORT_FIELD], defaults.sortOrder.field),
                ascending = this[SORT_ASCENDING] ?: defaults.sortOrder.ascending,
                foldersFirst = this[FOLDERS_FIRST] ?: defaults.sortOrder.foldersFirst,
            ),
            showHidden = this[SHOW_HIDDEN] ?: defaults.showHidden,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        val VIEW_MODE = stringPreferencesKey("browser_view_mode")
        val THUMBNAIL_SIZE = stringPreferencesKey("browser_thumbnail_size")
        val COLUMNS = intPreferencesKey("browser_columns")
        val SORT_FIELD = stringPreferencesKey("browser_sort_field")
        val SORT_ASCENDING = booleanPreferencesKey("browser_sort_ascending")
        val FOLDERS_FIRST = booleanPreferencesKey("browser_folders_first")
        val SHOW_HIDDEN = booleanPreferencesKey("browser_show_hidden")
        val VIEWER_SCOPE = stringPreferencesKey("viewer_scope")
        val VIEWER_ORIENTATION = stringPreferencesKey("viewer_orientation")
        val VIEWER_FILMSTRIP = booleanPreferencesKey("viewer_filmstrip")
    }
}
