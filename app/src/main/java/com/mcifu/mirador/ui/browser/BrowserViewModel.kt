package com.mcifu.mirador.ui.browser

import androidx.core.net.toUri
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.mcifu.mirador.domain.ExternalPaths
import com.mcifu.mirador.domain.FileSorter
import com.mcifu.mirador.domain.MediaNavigation
import com.mcifu.mirador.domain.PlaybackRules
import com.mcifu.mirador.domain.connectionChanges
import com.mcifu.mirador.domain.model.BrowserSettings
import com.mcifu.mirador.domain.model.FileDetails
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.domain.model.FileType
import com.mcifu.mirador.domain.model.FolderLocation
import com.mcifu.mirador.domain.model.MountState
import com.mcifu.mirador.domain.model.OperationProgress
import com.mcifu.mirador.domain.model.StorageException
import com.mcifu.mirador.domain.model.UsbStorage
import com.mcifu.mirador.domain.repository.FileDetailsRepository
import com.mcifu.mirador.domain.repository.FileOperations
import com.mcifu.mirador.domain.repository.FileRepository
import com.mcifu.mirador.domain.repository.SettingsRepository
import com.mcifu.mirador.domain.repository.StorageRepository
import com.mcifu.mirador.ui.common.appContainer
import com.mcifu.mirador.ui.navigation.BrowserRoute
import com.mcifu.mirador.ui.navigation.MultiviewRoute
import com.mcifu.mirador.ui.navigation.ViewerRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val fileOperations: FileOperations,
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

    /** Selección múltiple (documentIds). Va aparte del estado para no reordenar al seleccionar. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    /** Escritura permitida: memoria montada en lectura/escritura (no "solo lectura"). */
    val canWrite: StateFlow<Boolean> = storageRepository.storages
        .map { list -> list.firstOrNull { it.id == location.storageId }?.mountState == MountState.MOUNTED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val operation: StateFlow<OperationProgress?> = fileOperations.progress

    /** Destinos posibles para copiar/mover: memorias autorizadas, conectadas y escribibles. */
    val writableStorages: StateFlow<List<UsbStorage>> = storageRepository.storages
        .map { list -> list.filter { it.canBrowse && it.mountState == MountState.MOUNTED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Niveles iniciales del selector de destino: la carpeta actual con sus carpetas superiores, para
     * poder subir. Si el proveedor no usa rutas jerárquicas, solo la carpeta actual.
     */
    fun pickerStart(): List<PickerLevel> {
        val root = storageRepository.storages.value.firstOrNull { it.id == location.storageId }?.access?.rootDocumentId
        val ids = mutableListOf(location.documentId)
        while (root != null && ids.first() != root) {
            ids.add(0, ExternalPaths.parentOf(ids.first()) ?: break)
        }
        return if (ids.first() == root && ids.size == route.path.size) {
            ids.zip(route.path) { id, name -> PickerLevel(location.copy(documentId = id), name) }
        } else {
            listOf(PickerLevel(location, route.path.last()))
        }
    }

    /** Subcarpetas de una ubicación (para el selector de destino). */
    suspend fun foldersIn(location: FolderLocation): List<FileItem> =
        FileSorter.sort(fileRepository.listFolder(location), settingsRepository.browserSettings.value.sortOrder, showHidden = false)
            .filter { it.isDirectory }

    init {
        load(forceRefresh = false)
        observeConnection()
        // Tras copiar, mover, borrar, renombrar o crear: se recarga si afecta a esta memoria.
        viewModelScope.launch {
            fileOperations.changes.collect { storages ->
                if (location.storageId in storages) load(forceRefresh = true)
            }
        }
    }

    // --- Selección ---------------------------------------------------------------------------

    fun toggleSelection(item: FileItem) = _selection.update { if (item.documentId in it) it - item.documentId else it + item.documentId }

    fun selectAll() = _selection.update { state.value.items.map { it.documentId }.toSet() }

    fun clearSelection() = _selection.update { emptySet() }

    fun selectedItems(): List<FileItem> = state.value.items.filter { it.documentId in _selection.value }

    // --- Operaciones -------------------------------------------------------------------------

    /** Devuelve el mensaje de error o `null` si ha ido bien. */
    suspend fun createFolder(name: String): String? = runOperation { fileOperations.createFolder(location, name.trim()) }

    suspend fun rename(item: FileItem, newName: String): String? = runOperation {
        fileOperations.rename(item, location, newName.trim())
        clearSelection()
    }

    fun delete(items: List<FileItem>): Boolean = fileOperations.delete(items, location).also { if (it) clearSelection() }

    fun copyTo(items: List<FileItem>, target: FolderLocation): Boolean =
        fileOperations.copy(items, location, target).also { if (it) clearSelection() }

    fun moveTo(items: List<FileItem>, target: FolderLocation): Boolean =
        fileOperations.move(items, location, target).also { if (it) clearSelection() }

    fun cancelOperation() = fileOperations.cancel()

    fun dismissOperation() = fileOperations.dismiss()

    private suspend fun runOperation(block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: StorageException) {
        e.message ?: "No se pudo completar la operación"
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

    fun multiviewRoute(item: FileItem) = MultiviewRoute(
        storageId = route.storageId,
        treeUri = route.treeUri,
        folderDocumentId = route.documentId,
        firstDocumentId = item.documentId,
        path = route.path,
    )

    /** Fotos compatibles, vídeos y audio se abren en el visor/reproductor de Mirador. */
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
                    fileOperations = appContainer.fileOperations,
                )
            }
        }
    }
}
