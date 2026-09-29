package com.mcifu.usbx.ui.home

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcifu.usbx.domain.model.UsbStorage
import com.mcifu.usbx.domain.repository.StorageRepository
import com.mcifu.usbx.ui.common.appContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    val storages: StateFlow<List<UsbStorage>> = storageRepository.storages

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Almacenamiento para el que se abrió el selector (null = carpeta manual). */
    private var pendingStorageId: String? = null

    fun accessIntent(storage: UsbStorage?): Intent {
        pendingStorageId = storage?.id
        return storageRepository.createAccessIntent(storage)
    }

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
            initializer { HomeViewModel(appContainer.storageRepository) }
        }
    }
}
