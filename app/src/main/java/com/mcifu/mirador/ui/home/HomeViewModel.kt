package com.mcifu.mirador.ui.home

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcifu.mirador.domain.model.AppearanceSettings
import com.mcifu.mirador.domain.model.UsbStorage
import com.mcifu.mirador.domain.repository.SettingsRepository
import com.mcifu.mirador.domain.repository.StorageRepository
import com.mcifu.mirador.ui.common.appContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface HomeEvent {
    data class OpenStorage(val storage: UsbStorage) : HomeEvent
    data object AccessDenied : HomeEvent
    data object InvalidSelection : HomeEvent
    data class StorageNotReady(val storage: UsbStorage) : HomeEvent
}

class HomeViewModel(
    private val storageRepository: StorageRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val storages: StateFlow<List<UsbStorage>> = storageRepository.storages

    /** Incluye si hay que confirmar el bloqueo del móvil antes de mostrar las carpetas guardadas. */
    val appearance: StateFlow<AppearanceSettings> = settingsRepository.appearance

    /**
     * Carpetas desbloqueadas en esta sesión: tras confirmar el bloqueo una vez no se vuelve a
     * pedir hasta que la app pasa a segundo plano ([lockFolders]).
     */
    private val _foldersUnlocked = MutableStateFlow(false)
    val foldersUnlocked: StateFlow<Boolean> = _foldersUnlocked.asStateFlow()

    fun unlockFolders() {
        _foldersUnlocked.value = true
    }

    fun lockFolders() {
        _foldersUnlocked.value = false
    }

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun accessIntent(storage: UsbStorage?): Intent = storageRepository.createAccessIntent(storage)

    fun onAccessResult(treeUri: Uri?, flags: Int) {
        if (treeUri == null) {
            _events.trySend(HomeEvent.AccessDenied)
            return
        }
        viewModelScope.launch {
            val storage = storageRepository.onAccessGranted(treeUri, flags)
            when {
                storage == null -> _events.send(HomeEvent.InvalidSelection)
                storage.canBrowse -> _events.send(HomeEvent.OpenStorage(storage))
                else -> _events.send(HomeEvent.StorageNotReady(storage))
            }
        }
    }

    fun open(storage: UsbStorage) {
        if (storage.canBrowse) _events.trySend(HomeEvent.OpenStorage(storage))
        else _events.trySend(HomeEvent.StorageNotReady(storage))
    }

    fun forget(storage: UsbStorage) {
        viewModelScope.launch { storageRepository.forget(storage) }
    }

    fun refresh() = storageRepository.refresh()

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer.storageRepository, appContainer.settingsRepository) }
        }
    }
}
