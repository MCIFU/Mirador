package com.mcifu.usbx.ui.browser

import androidx.core.net.toUri
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.mcifu.usbx.domain.FileSorter
import com.mcifu.usbx.domain.MediaNavigation
import com.mcifu.usbx.domain.PlaybackRules
import com.mcifu.usbx.domain.connectionChanges
import com.mcifu.usbx.domain.model.BrowserSettings
import com.mcifu.usbx.domain.model.FileDetails
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileType
import com.mcifu.usbx.domain.model.FolderLocation
import com.mcifu.usbx.domain.model.StorageException
import com.mcifu.usbx.domain.repository.FileDetailsRepository
import com.mcifu.usbx.domain.repository.FileRepository
import com.mcifu.usbx.domain.repository.SettingsRepository
import com.mcifu.usbx.domain.repository.StorageRepository
import com.mcifu.usbx.ui.common.appContainer
import com.mcifu.usbx.ui.navigation.BrowserRoute
import com.mcifu.usbx.ui.navigation.ViewerRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        treeUri = route.treeUri.toUri(),
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

    /** Mensajes puntuales para la interfaz (p. ej. "Memoria USB reconectada"). */
    // Sin buffer: si la carpeta no está en pantalla, el mensaje no se guarda para después.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load(forceRefresh = false)
        observeConnection()
    }

    /**
     * Desconexión: se descarta el contenido (ya no es legible) y se muestra el aviso.
     * Reconexión de la misma memoria: se recarga sola; la posición de scroll se conserva
     * porque el estado de la lista vive en la pantalla.
     */
    private fun observeConnection() {
        viewModelScope.launch {
            storageRepository.storages.connectionChanges(location.storageId).collect { connected ->
                if (connected) {
                    fileRepository.invalidate(location.storageId)
                    load(forceRefresh = true)
                    _messages.tryEmit("Memoria USB reconectada")
                } else {
                    loadJob?.cancel()
                    fileRepository.invalidate(location.storageId)
                    load.value = LoadState(isLoading = false, error = StorageException.Reason.DISCONNECTED)
                }
            }
        }
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
            if (!isStorageAvailable()) {
                load.value = LoadState(isLoading = false, error = StorageException.Reason.DISCONNECTED)
                return@launch
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
                load.update {
                    it.copy(
                        raw = null,
                        isLoading = false,
                        isRefreshing = false,
                        error = if (isStorageAvailable()) e.reason else StorageException.Reason.DISCONNECTED,
                    )
                }
            }
        }
    }

    private fun isStorageAvailable(): Boolean =
        storageRepository.storages.value.firstOrNull { it.id == location.storageId }?.canBrowse == true

    suspend fun loadDetails(item: FileItem): FileDetails =
        fileDetailsRepository.loadDetails(item, storageLabel = route.path.first(), parentPath = route.path)

    fun viewerRoute(image: FileItem) = ViewerRoute(
        storageId = route.storageId,
        treeUri = route.treeUri,
        folderDocumentId = route.documentId,
        startDocumentId = image.documentId,
        path = route.path,
    )

    /** Fotos compatibles, vídeos y audio se abren en el visor/reproductor de USBX. */
    fun canViewInternally(item: FileItem): Boolean =
        MediaNavigation.isViewableImage(item, Build.VERSION.SDK_INT) || PlaybackRules.isPlayable(item)

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
