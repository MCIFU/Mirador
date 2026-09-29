package com.mcifu.usbx.ui.viewer

import android.net.Uri
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
import com.mcifu.usbx.domain.ViewerScope
import com.mcifu.usbx.domain.model.FileDetails
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FolderLocation
import com.mcifu.usbx.domain.model.StorageException
import com.mcifu.usbx.domain.repository.FileDetailsRepository
import com.mcifu.usbx.domain.repository.FileRepository
import com.mcifu.usbx.domain.repository.SettingsRepository
import com.mcifu.usbx.ui.common.appContainer
import com.mcifu.usbx.ui.navigation.ImageViewerRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ViewerState(
    val isLoading: Boolean = true,
    val items: List<FileItem> = emptyList(),
    val initialIndex: Int = 0,
    val error: StorageException.Reason? = null,
)

class ImageViewerViewModel(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val settingsRepository: SettingsRepository,
    private val fileDetailsRepository: FileDetailsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ImageViewerRoute>()
    val storageId: String get() = route.storageId

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val location = FolderLocation(route.storageId, Uri.parse(route.treeUri), route.folderDocumentId)
                val raw = fileRepository.listFolder(location)
                val settings = settingsRepository.browserSettings.value
                val playlist = withContext(Dispatchers.Default) {
                    val sorted = FileSorter.sort(raw, settings.sortOrder, settings.showHidden)
                    MediaNavigation.playlist(sorted, ViewerScope.IMAGES, Build.VERSION.SDK_INT)
                }
                _state.value = ViewerState(
                    isLoading = false,
                    items = playlist,
                    initialIndex = MediaNavigation.startIndex(playlist, route.startDocumentId),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageException) {
                _state.value = ViewerState(isLoading = false, error = e.reason)
            }
        }
    }

    suspend fun loadDetails(item: FileItem): FileDetails =
        fileDetailsRepository.loadDetails(item, storageLabel = route.path.first(), parentPath = route.path)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ImageViewerViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    fileRepository = appContainer.fileRepository,
                    settingsRepository = appContainer.settingsRepository,
                    fileDetailsRepository = appContainer.fileDetailsRepository,
                )
            }
        }
    }
}
