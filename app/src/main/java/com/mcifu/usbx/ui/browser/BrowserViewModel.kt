package com.mcifu.usbx.ui.browser

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.mcifu.usbx.domain.FileSorter
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FolderLocation
import com.mcifu.usbx.domain.model.SortOrder
import com.mcifu.usbx.domain.model.StorageException
import com.mcifu.usbx.domain.repository.FileRepository
import com.mcifu.usbx.domain.repository.StorageRepository
import com.mcifu.usbx.ui.common.appContainer
import com.mcifu.usbx.ui.navigation.BrowserRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserState(
    val title: String,
    val path: List<String>,
    val isLoading: Boolean = true,
    val items: List<FileItem> = emptyList(),
    val error: StorageException.Reason? = null,
)

class BrowserViewModel(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<BrowserRoute>()

    val location = FolderLocation(
        storageId = route.storageId,
        treeUri = Uri.parse(route.treeUri),
        documentId = route.documentId,
    )

    private val _state = MutableStateFlow(BrowserState(title = route.path.lastOrNull().orEmpty(), path = route.path))
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
    }

    fun refresh() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = if (forceRefresh) null else fileRepository.cachedFolder(location)
            _state.update { it.copy(isLoading = cached == null, error = null) }
            try {
                val raw = cached ?: fileRepository.listFolder(location, forceRefresh)
                val sorted = FileSorter.sort(raw, SortOrder(), showHidden = false)
                _state.update { it.copy(isLoading = false, items = sorted, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageException) {
                val storageGone = storageRepository.storages.value
                    .firstOrNull { it.id == location.storageId }?.isAvailable == false
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (storageGone) StorageException.Reason.DISCONNECTED else e.reason,
                    )
                }
            }
        }
    }

    fun childRoute(folder: FileItem) = BrowserRoute(
        storageId = route.storageId,
        treeUri = route.treeUri,
        documentId = folder.documentId,
        path = route.path + folder.name,
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                BrowserViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    fileRepository = appContainer.fileRepository,
                    storageRepository = appContainer.storageRepository,
                )
            }
        }
    }
}
