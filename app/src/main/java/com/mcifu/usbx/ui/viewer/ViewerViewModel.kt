package com.mcifu.usbx.ui.viewer

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.mcifu.usbx.data.diagnostics.Diagnostics
import com.mcifu.usbx.domain.FileSorter
import com.mcifu.usbx.domain.LoopMode
import com.mcifu.usbx.domain.MediaNavigation
import com.mcifu.usbx.domain.connectionChanges
import com.mcifu.usbx.domain.model.FileDetails
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileTypeRules
import com.mcifu.usbx.domain.model.FolderLocation
import com.mcifu.usbx.domain.model.MountState
import com.mcifu.usbx.domain.model.OrientationMode
import com.mcifu.usbx.domain.model.StorageException
import com.mcifu.usbx.domain.model.ViewerScope
import com.mcifu.usbx.domain.model.ViewerSettings
import com.mcifu.usbx.domain.repository.FileDetailsRepository
import com.mcifu.usbx.domain.repository.FileOperations
import com.mcifu.usbx.domain.repository.FileRepository
import com.mcifu.usbx.domain.repository.SettingsRepository
import com.mcifu.usbx.domain.repository.StorageRepository
import com.mcifu.usbx.ui.common.appContainer
import com.mcifu.usbx.ui.navigation.MultiviewRoute
import com.mcifu.usbx.ui.navigation.ViewerRoute
import com.mcifu.usbx.ui.player.VideoPlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val fileOperations: FileOperations,
    appContext: Context,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ViewerRoute>()
    private val location = FolderLocation(route.storageId, route.treeUri.toUri(), route.folderDocumentId)

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    val settings: StateFlow<ViewerSettings> = settingsRepository.viewerSettings

    /** Reproductor de vídeo y audio del visor (uno solo, conectado a la página visible). */
    val player = VideoPlayerController(appContext, viewModelScope)

    /** Petición de pasar al siguiente vídeo (modo "Repetir carpeta"). */
    private val _advanceRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val advanceRequests: SharedFlow<Unit> = _advanceRequests.asSharedFlow()

    /** Archivo abierto desde otra app sin acceso a su carpeta (se muestra solo él). */
    private val externalItem: FileItem? = route.externalUri?.let { uri ->
        val name = route.externalName ?: "Archivo"
        val mime = FileTypeRules.effectiveMime(name, route.externalMime, isDirectory = false)
        FileItem(
            documentId = uri,
            uri = uri,
            name = name,
            mimeType = mime,
            type = FileTypeRules.classify(name, mime, isDirectory = false),
            size = route.externalSize,
            lastModified = 0L,
        )
    }
    val isExternal: Boolean get() = externalItem != null

    /** Documento visible ahora mismo. */
    private var currentDocumentId: String = route.startDocumentId
    private var loadJob: Job? = null

    init {
        load()
        player.onEndedInFolderMode = { _advanceRequests.tryEmit(Unit) }
        viewModelScope.launch {
            fileOperations.changes.collect { storages -> if (route.storageId in storages && !isExternal) load() }
        }
        viewModelScope.launch {
            settingsRepository.viewerSettings.map { it.loopMode }.distinctUntilChanged().collect(player::setLoopMode)
        }
        viewModelScope.launch {
            storageRepository.storages.connectionChanges(route.storageId).collect { connected ->
                fileRepository.invalidate(route.storageId)
                if (connected) {
                    load()
                } else {
                    // Sin memoria no hay nada que leer: se detiene el reproductor (la posición
                    // se recuerda para continuar al reconectar).
                    player.setItem(null)
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
        externalItem?.let { item ->
            _state.value = ViewerState(isLoading = false, items = listOf(item), generation = _state.value.generation + 1)
            return
        }
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
                Diagnostics.log("VISOR", "Lista del visor: ${playlist.size} elementos (ámbito $scope)")
                _state.value = ViewerState(
                    isLoading = false,
                    items = playlist,
                    initialIndex = MediaNavigation.startIndex(playlist, currentDocumentId),
                    generation = previous.generation + 1,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageException) {
                Diagnostics.log("VISOR", "No se pudo leer la carpeta (${e.reason})", e)
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

    /** Se puede borrar: archivo de una memoria autorizada y escribible (no abierto desde otra app). */
    val canDelete: Boolean
        get() = !isExternal && storageRepository.storages.value.firstOrNull { it.id == route.storageId }?.mountState == MountState.MOUNTED

    /** Elimina [item]; el visor queda en [neighbor] (el siguiente, o el anterior si era el último). */
    fun delete(item: FileItem, neighbor: FileItem?): Boolean {
        if (player.state.value.item?.documentId == item.documentId) player.setItem(null)
        neighbor?.let { currentDocumentId = it.documentId }
        return fileOperations.delete(listOf(item), location)
    }

    fun cycleLoopMode() {
        val next = when (settings.value.loopMode) {
            LoopMode.REPEAT_ONE -> LoopMode.PLAY_ONCE
            LoopMode.PLAY_ONCE -> LoopMode.REPEAT_FOLDER
            LoopMode.REPEAT_FOLDER -> LoopMode.REPEAT_ONE
        }
        viewModelScope.launch { settingsRepository.updateViewerSettings { it.copy(loopMode = next) } }
    }

    override fun onCleared() {
        player.release()
    }

    fun setFilmstrip(visible: Boolean) {
        viewModelScope.launch { settingsRepository.updateViewerSettings { it.copy(showFilmstrip = visible) } }
    }

    fun multiviewRoute(item: FileItem) = MultiviewRoute(
        storageId = route.storageId,
        treeUri = route.treeUri,
        folderDocumentId = route.folderDocumentId,
        firstDocumentId = item.documentId,
        path = route.path,
    )

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
                    fileOperations = appContainer.fileOperations,
                    appContext = appContainer.appContext,
                )
            }
        }
    }
}
