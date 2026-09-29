package com.mcifu.usbx.ui.viewer

import android.os.Build
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.mcifu.usbx.domain.FileSorter
import com.mcifu.usbx.domain.MediaNavigation
import com.mcifu.usbx.domain.connectionChanges
import com.mcifu.usbx.domain.model.FileDetails
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FolderLocation
import com.mcifu.usbx.domain.model.OrientationMode
import com.mcifu.usbx.domain.model.StorageException
import com.mcifu.usbx.domain.model.ViewerScope
import com.mcifu.usbx.domain.model.ViewerSettings
import com.mcifu.usbx.domain.repository.FileDetailsRepository
import com.mcifu.usbx.domain.repository.FileRepository
import com.mcifu.usbx.domain.repository.SettingsRepository
import com.mcifu.usbx.domain.repository.StorageRepository
import com.mcifu.usbx.ui.common.appContainer
import com.mcifu.usbx.ui.navigation.ViewerRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ViewerState(
    val isLoading: Boolean = true,
    val items: List<FileItem> = emptyList(),
    val initialIndex: Int = 0,
    /** Cambia cada vez que se reconstruye la lista: el pager se recrea en la posición correcta. */
    val generation: Int = 0,
    val error: StorageException.Reason? = null,
)

/**
 * Visor multimedia: construye la secuencia de la carpeta según el ámbito elegido
 * ("Solo imágenes", "Fotos y vídeos"…), recuerda el elemento visible para recuperarlo tras una
 * reconexión o un cambio de ámbito y guarda las preferencias del visor.
 */
class ViewerViewModel(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val settingsRepository: SettingsRepository,
    private val fileDetailsRepository: FileDetailsRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ViewerRoute>()
    private val location = FolderLocation(route.storageId, route.treeUri.toUri(), route.folderDocumentId)

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    val settings: StateFlow<ViewerSettings> = settingsRepository.viewerSettings

    /** Documento visible ahora mismo. */
    private var currentDocumentId: String = route.startDocumentId
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            storageRepository.storages.connectionChanges(route.storageId).collect { connected ->
                fileRepository.invalidate(route.storageId)
                if (connected) {
                    load()
                } else {
                    loadJob?.cancel()
                    _state.value = ViewerState(isLoading = false, error = StorageException.Reason.DISCONNECTED)
                }
            }
        }
        // Al cambiar "qué recorrer", se rehace la lista manteniendo el elemento visible.
        viewModelScope.launch {
            settingsRepository.viewerSettings.map { it.scope }.distinctUntilChanged().drop(1).collect { load() }
        }
    }

    fun onPageShown(item: FileItem) {
        currentDocumentId = item.documentId
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previous = _state.value
            _state.value = previous.copy(isLoading = previous.items.isEmpty(), error = null)
            try {
                val raw = fileRepository.listFolder(location)
                val browser = settingsRepository.browserSettings.value
                val scope = settingsRepository.viewerSettings.value.scope
                val playlist = withContext(Dispatchers.Default) {
                    val sorted = FileSorter.sort(raw, browser.sortOrder, browser.showHidden)
                    MediaNavigation.playlist(sorted, scope, Build.VERSION.SDK_INT, alwaysIncludeDocumentId = currentDocumentId)
                }
                _state.value = ViewerState(
                    isLoading = false,
                    items = playlist,
                    initialIndex = MediaNavigation.startIndex(playlist, currentDocumentId),
                    generation = previous.generation + 1,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageException) {
                val available = storageRepository.storages.value.firstOrNull { it.id == route.storageId }?.canBrowse == true
                _state.value = ViewerState(
                    isLoading = false,
                    error = if (available) e.reason else StorageException.Reason.DISCONNECTED,
                )
            }
        }
    }

    fun setScope(scope: ViewerScope) {
        viewModelScope.launch { settingsRepository.updateViewerSettings { it.copy(scope = scope) } }
    }

    fun setOrientation(mode: OrientationMode) {
        viewModelScope.launch { settingsRepository.updateViewerSettings { it.copy(orientation = mode) } }
    }

    fun setFilmstrip(visible: Boolean) {
        viewModelScope.launch { settingsRepository.updateViewerSettings { it.copy(showFilmstrip = visible) } }
    }

    fun isViewableImage(item: FileItem) = MediaNavigation.isViewableImage(item, Build.VERSION.SDK_INT)

    suspend fun loadDetails(item: FileItem): FileDetails =
        fileDetailsRepository.loadDetails(item, storageLabel = route.path.first(), parentPath = route.path)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ViewerViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    fileRepository = appContainer.fileRepository,
                    settingsRepository = appContainer.settingsRepository,
                    fileDetailsRepository = appContainer.fileDetailsRepository,
                    storageRepository = appContainer.storageRepository,
                )
            }
        }
    }
}
