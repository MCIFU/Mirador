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
import com.mcifu.usbx.domain.model.BrowserSettings
import com.mcifu.usbx.domain.model.FileDetails
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FolderLocation
import com.mcifu.usbx.domain.model.StorageException
import com.mcifu.usbx.domain.repository.FileDetailsRepository
import com.mcifu.usbx.domain.repository.FileRepository
import com.mcifu.usbx.domain.repository.SettingsRepository
import com.mcifu.usbx.domain.repository.StorageRepository
import com.mcifu.usbx.ui.common.appContainer
import com.mcifu.usbx.ui.navigation.BrowserRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserState(
    val title: String,
    val path: List<String>,
    val settings: BrowserSettings,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val items: List<FileItem> = emptyList(),
    val folderCount: Int = 0,
    val fileCount: Int = 0,
    val error: StorageException.Reason? = null,
)

private data class LoadState(
    val raw: List<FileItem>? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: StorageException.Reason? = null,
)

class BrowserViewModel(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val storageRepository: StorageRepository,
    private val settingsRepository: SettingsRepository,
    private val fileDetailsRepository: FileDetailsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<BrowserRoute>()

    val location = FolderLocation(
        storageId = route.storageId,
        treeUri = Uri.parse(route.treeUri),
        documentId = route.documentId,
    )

    private val load = MutableStateFlow(LoadState())
    private var loadJob: Job? = null

    val state: StateFlow<BrowserState> =
        combine(load, settingsRepository.browserSettings) { load, settings ->
            // Ordenar miles de entradas no debe ocurrir en el hilo principal: flowOn(Default).
            val items = load.raw?.let { FileSorter.sort(it, settings.sortOrder, settings.showHidden) }.orEmpty()
            val folders = items.count { it.isDirectory }
            BrowserState(
                title = route.path.lastOrNull().orEmpty(),
                path = route.path,
                settings = settings,
                isLoading = load.isLoading,
                isRefreshing = load.isRefreshing,
                items = items,
                folderCount = folders,
                fileCount = items.size - folders,
                error = load.error,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                BrowserState(route.path.lastOrNull().orEmpty(), route.path, settingsRepository.browserSettings.value),
            )

    init {
        load(forceRefresh = false)
    }

    fun refresh() = load(forceRefresh = true)

    fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) {
        viewModelScope.launch { settingsRepository.updateBrowserSettings(transform) }
    }

    private fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = if (forceRefresh) null else fileRepository.cachedFolder(location)
            load.update {
                it.copy(
                    raw = cached ?: it.raw,
                    isLoading = cached == null && it.raw == null,
                    isRefreshing = forceRefresh && it.raw != null,
                    error = null,
                )
            }
            if (cached != null) {
                load.update { it.copy(isLoading = false) }
                return@launch
            }
            try {
                val items = fileRepository.listFolder(location, forceRefresh)
                load.update { LoadState(raw = items, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageException) {
                val storageGone = storageRepository.storages.value
                    .firstOrNull { it.id == location.storageId }?.isAvailable == false
                load.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (storageGone) StorageException.Reason.DISCONNECTED else e.reason,
                    )
                }
            }
        }
    }

    suspend fun loadDetails(item: FileItem): FileDetails =
        fileDetailsRepository.loadDetails(item, storageLabel = route.path.first(), parentPath = route.path)

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
                    settingsRepository = appContainer.settingsRepository,
                    fileDetailsRepository = appContainer.fileDetailsRepository,
                )
            }
        }
    }
}
