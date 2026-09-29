package com.mcifu.usbx.ui.multiview

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
import com.mcifu.usbx.domain.FileSorter
import com.mcifu.usbx.domain.MediaNavigation
import com.mcifu.usbx.domain.PlaybackRules
import com.mcifu.usbx.domain.connectionChanges
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FolderLocation
import com.mcifu.usbx.domain.model.OrientationMode
import com.mcifu.usbx.domain.model.StorageException
import com.mcifu.usbx.domain.model.ViewerScope
import com.mcifu.usbx.domain.model.ViewerSettings
import com.mcifu.usbx.domain.repository.FileRepository
import com.mcifu.usbx.domain.repository.SettingsRepository
import com.mcifu.usbx.domain.repository.StorageRepository
import com.mcifu.usbx.ui.common.appContainer
import com.mcifu.usbx.ui.navigation.MultiviewRoute
import com.mcifu.usbx.ui.player.PlaybackSync
import com.mcifu.usbx.ui.player.VideoPlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Pane { A, B }

data class MultiviewState(
    val isLoading: Boolean = true,
    /** Fotos y vídeos de la carpeta, para elegir qué mostrar en cada panel. */
    val media: List<FileItem> = emptyList(),
    val paneA: FileItem? = null,
    val paneB: FileItem? = null,
    /** Intercambio izquierda/derecha (o arriba/abajo): solo cambia el orden en pantalla. */
    val swapped: Boolean = false,
    val zoomSynced: Boolean = false,
    val error: StorageException.Reason? = null,
) {
    val bothVideos: Boolean
        get() = paneA != null && paneB != null && PlaybackRules.isPlayable(paneA) && PlaybackRules.isPlayable(paneB)
    val bothImages: Boolean
        get() = paneA != null && paneB != null && !PlaybackRules.isPlayable(paneA) && !PlaybackRules.isPlayable(paneB)

    fun item(pane: Pane) = if (pane == Pane.A) paneA else paneB
}

/**
 * Multiview: dos paneles independientes, cada uno con su reproductor. El panel B empieza
 * silenciado y sin foco de audio para no mezclar sonidos ni pausar el panel A.
 */
class MultiviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val settingsRepository: SettingsRepository,
    private val storageRepository: StorageRepository,
    appContext: Context,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MultiviewRoute>()
    private val location = FolderLocation(route.storageId, route.treeUri.toUri(), route.folderDocumentId)

    val playerA = VideoPlayerController(appContext, viewModelScope, handleAudioFocus = true)
    val playerB = VideoPlayerController(appContext, viewModelScope, handleAudioFocus = false, initialVolume = 0f)
    val sync = PlaybackSync(playerA, playerB, viewModelScope)

    private val _state = MutableStateFlow(MultiviewState())
    val state: StateFlow<MultiviewState> = _state.asStateFlow()

    val settings: StateFlow<ViewerSettings> = settingsRepository.viewerSettings

    init {
        load(firstLoad = true)
        viewModelScope.launch {
            storageRepository.storages.connectionChanges(route.storageId).collect { connected ->
                fileRepository.invalidate(route.storageId)
                if (connected) {
                    load(firstLoad = false)
                } else {
                    playerA.setItem(null)
                    playerB.setItem(null)
                    sync.setEnabled(false)
                    _state.update { it.copy(error = StorageException.Reason.DISCONNECTED) }
                }
            }
        }
    }

    fun controller(pane: Pane) = if (pane == Pane.A) playerA else playerB

    fun load(firstLoad: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.media.isEmpty(), error = null) }
            try {
                val raw = fileRepository.listFolder(location)
                val browser = settingsRepository.browserSettings.value
                val media = withContext(Dispatchers.Default) {
                    val sorted = FileSorter.sort(raw, browser.sortOrder, browser.showHidden)
                    MediaNavigation.playlist(sorted, ViewerScope.VISUAL_MEDIA, Build.VERSION.SDK_INT)
                }
                _state.update { current ->
                    val a = if (firstLoad) media.firstOrNull { it.documentId == route.firstDocumentId } else current.paneA
                    current.copy(isLoading = false, media = media, paneA = a, paneB = current.paneB)
                }
                // Tras una reconexión se vuelven a cargar los vídeos que había.
                attach(Pane.A)
                attach(Pane.B)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageException) {
                val available = storageRepository.storages.value.firstOrNull { it.id == route.storageId }?.canBrowse == true
                _state.update {
                    it.copy(isLoading = false, error = if (available) e.reason else StorageException.Reason.DISCONNECTED)
                }
            }
        }
    }

    fun setPane(pane: Pane, item: FileItem?) {
        _state.update { if (pane == Pane.A) it.copy(paneA = item) else it.copy(paneB = item) }
        // El desfase guardado ya no tiene sentido con otro archivo: se vuelve a sincronizar a mano.
        sync.setEnabled(false)
        attach(pane)
    }

    fun swap() = _state.update { it.copy(swapped = !it.swapped) }

    fun setZoomSynced(value: Boolean) = _state.update { it.copy(zoomSynced = value) }

    fun setOrientation(mode: OrientationMode) {
        viewModelScope.launch { settingsRepository.updateViewerSettings { it.copy(orientation = mode) } }
    }

    fun pauseAll() {
        playerA.pause()
        playerB.pause()
    }

    private fun attach(pane: Pane) {
        val item = _state.value.item(pane)
        controller(pane).setItem(item?.takeIf(PlaybackRules::isPlayable))
    }

    fun isViewableImage(item: FileItem) = MediaNavigation.isViewableImage(item, Build.VERSION.SDK_INT)

    override fun onCleared() {
        sync.release()
        playerA.release()
        playerB.release()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                MultiviewViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    fileRepository = appContainer.fileRepository,
                    settingsRepository = appContainer.settingsRepository,
                    storageRepository = appContainer.storageRepository,
                    appContext = appContainer.appContext,
                )
            }
        }
    }
}
