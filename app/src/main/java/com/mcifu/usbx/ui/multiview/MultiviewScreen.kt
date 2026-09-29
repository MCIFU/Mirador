package com.mcifu.usbx.ui.multiview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.StayCurrentLandscape
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcifu.usbx.domain.LoopMode
import com.mcifu.usbx.domain.PlaybackRules
import com.mcifu.usbx.domain.ZoomTransform
import com.mcifu.usbx.domain.model.OrientationMode
import com.mcifu.usbx.ui.browser.ErrorState
import com.mcifu.usbx.ui.common.ExternalActions
import com.mcifu.usbx.ui.theme.UsbxTheme
import com.mcifu.usbx.ui.viewer.OrientationEffect
import com.mcifu.usbx.ui.viewer.SystemBarsEffect

/**
 * Multiview (Fase 4): dos fotos o vídeos a la vez.
 *
 * - Vertical: uno encima del otro. Horizontal: lado a lado.
 * - Cada panel: cambiar archivo, cerrar, zoom (fotos) o controles propios (vídeos).
 * - Fotos: zoom sincronizado opcional. Vídeos: reproducción paralela con controles
 *   independientes y sincronización opcional que mantiene el desfase elegido.
 * - Intercambiar posiciones sin recargar nada.
 */
@Composable
fun MultiviewScreen(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: MultiviewViewModel = viewModel(factory = MultiviewViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stateA by viewModel.playerA.state.collectAsStateWithLifecycle()
    val stateB by viewModel.playerB.state.collectAsStateWithLifecycle()
    val syncEnabled by viewModel.sync.enabled.collectAsStateWithLifecycle()
    val syncOffset by viewModel.sync.offsetMs.collectAsStateWithLifecycle()
    var pickerFor by remember { mutableStateOf<Pane?>(null) }
    var zoomA by remember { mutableStateOf(ZoomTransform()) }
    var zoomB by remember { mutableStateOf(ZoomTransform()) }
    val context = LocalContext.current

    SystemBarsEffect(visible = false)
    OrientationEffect(settings.orientation)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pauseAll() }
    val view = LocalView.current
    val anyPlaying = stateA.isPlaying || stateB.isPlaying
    DisposableEffect(anyPlaying) {
        view.keepScreenOn = anyPlaying
        onDispose { view.keepScreenOn = false }
    }
    // Al cambiar la foto de un panel, su zoom vuelve a encajar.
    LaunchedEffect(state.paneA?.documentId) { zoomA = ZoomTransform() }
    LaunchedEffect(state.paneB?.documentId) { zoomB = ZoomTransform() }

    // Si hay dos fotos y el zoom está sincronizado, ambos paneles usan el mismo estado.
    val syncZoom = state.zoomSynced && state.bothImages
    fun zoomFor(pane: Pane) = if (syncZoom || pane == Pane.A) zoomA else zoomB
    fun setZoom(pane: Pane, t: ZoomTransform) {
        if (syncZoom || pane == Pane.A) zoomA = t else zoomB = t
    }

    UsbxTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.load() }, onGoHome = onGoHome)
                else -> {
                    val window = LocalWindowInfo.current.containerSize
                    val landscape = window.width > window.height
                    val order = if (state.swapped) listOf(Pane.B, Pane.A) else listOf(Pane.A, Pane.B)

                    @Composable
                    fun PaneSlot(pane: Pane, modifier: Modifier) {
                        val item = state.item(pane)
                        val controller = viewModel.controller(pane)
                        val playerState = if (pane == Pane.A) stateA else stateB
                        Box(modifier) {
                            when {
                                item == null -> EmptyPane(onPick = { pickerFor = pane })
                                PlaybackRules.isPlayable(item) -> VideoPane(
                                    item = item,
                                    player = controller.player,
                                    state = playerState,
                                    onPlayPause = { viewModel.sync.togglePlayPause(controller) },
                                    onScrubStart = { viewModel.sync.beginScrub(controller) },
                                    onScrub = { viewModel.sync.scrub(controller, it) },
                                    onScrubEnd = { viewModel.sync.endScrub(controller, it) },
                                    onVolume = controller::setVolume,
                                    onSpeed = { viewModel.sync.setSpeed(controller, it) },
                                    onToggleLoop = {
                                        controller.setLoopMode(
                                            if (playerState.loopMode == LoopMode.REPEAT_ONE) LoopMode.PLAY_ONCE else LoopMode.REPEAT_ONE,
                                        )
                                    },
                                    onRetry = controller::retry,
                                    onOpenWith = { ExternalActions.openWith(context, item) },
                                )
                                else -> ImagePane(
                                    item = item,
                                    transform = zoomFor(pane),
                                    onTransform = { setZoom(pane, it) },
                                )
                            }
                            if (item != null) {
                                PaneHeader(
                                    item = item,
                                    onChange = { pickerFor = pane },
                                    onClose = { viewModel.setPane(pane, null) },
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = if (pane == order.first() || landscape) 56.dp else 0.dp),
                                )
                            }
                        }
                    }

                    if (landscape) {
                        Row(Modifier.fillMaxSize()) {
                            PaneSlot(order[0], Modifier.weight(1f).fillMaxHeight())
                            Box(Modifier.width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.15f)))
                            PaneSlot(order[1], Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            PaneSlot(order[0], Modifier.weight(1f).fillMaxWidth())
                            Box(Modifier.height(2.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.15f)))
                            PaneSlot(order[1], Modifier.weight(1f).fillMaxWidth())
                        }
                    }

                    MultiviewTopBar(
                        landscape = landscape,
                        bothVideos = state.bothVideos,
                        bothImages = state.bothImages,
                        playbackSynced = syncEnabled,
                        syncOffsetMs = syncOffset,
                        zoomSynced = state.zoomSynced,
                        orientation = settings.orientation,
                        onBack = onBack,
                        onSwap = viewModel::swap,
                        onTogglePlaybackSync = { viewModel.sync.setEnabled(!syncEnabled) },
                        onAlign = viewModel.sync::alignToZero,
                        onToggleZoomSync = { viewModel.setZoomSynced(!state.zoomSynced) },
                        onCycleOrientation = {
                            viewModel.setOrientation(
                                when (settings.orientation) {
                                    OrientationMode.AUTO -> OrientationMode.PORTRAIT
                                    OrientationMode.PORTRAIT -> OrientationMode.LANDSCAPE
                                    OrientationMode.LANDSCAPE -> OrientationMode.AUTO
                                },
                            )
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }

        pickerFor?.let { pane ->
            MediaPickerSheet(
                media = state.media,
                selectedDocumentId = state.item(pane)?.documentId,
                onPick = { item ->
                    viewModel.setPane(pane, item)
                    pickerFor = null
                },
                onDismiss = { pickerFor = null },
            )
        }
    }
}

@Composable
private fun MultiviewTopBar(
    landscape: Boolean,
    bothVideos: Boolean,
    bothImages: Boolean,
    playbackSynced: Boolean,
    syncOffsetMs: Long,
    zoomSynced: Boolean,
    orientation: OrientationMode,
    onBack: () -> Unit,
    onSwap: () -> Unit,
    onTogglePlaybackSync: () -> Unit,
    onAlign: () -> Unit,
    onToggleZoomSync: () -> Unit,
    onCycleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var syncMenu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            .statusBarsPadding()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Salir del multiview", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text("Multiview", color = Color.White, style = MaterialTheme.typography.titleMedium)
            val subtitle = when {
                bothVideos && playbackSynced -> "Sincronizado · ${offsetLabel(syncOffsetMs)}"
                bothImages && zoomSynced -> "Zoom sincronizado"
                else -> null
            }
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        if (bothVideos) {
            Box {
                IconButton(onClick = { syncMenu = true }) {
                    Icon(
                        if (playbackSynced) Icons.Outlined.Link else Icons.Outlined.LinkOff,
                        contentDescription = "Sincronización de reproducción",
                        tint = if (playbackSynced) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                DropdownMenu(expanded = syncMenu, onDismissRequest = { syncMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Sincronizar reproducción") },
                        trailingIcon = { Checkbox(checked = playbackSynced, onCheckedChange = null) },
                        onClick = { syncMenu = false; onTogglePlaybackSync() },
                    )
                    DropdownMenuItem(
                        text = { Text("Alinear (desfase 0)") },
                        enabled = playbackSynced,
                        onClick = { syncMenu = false; onAlign() },
                    )
                }
            }
        }
        if (bothImages) {
            IconButton(onClick = onToggleZoomSync) {
                Icon(
                    if (zoomSynced) Icons.Outlined.Link else Icons.Outlined.LinkOff,
                    contentDescription = if (zoomSynced) "Desactivar zoom sincronizado" else "Sincronizar zoom",
                    tint = if (zoomSynced) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
        IconButton(onClick = onSwap) {
            Icon(if (landscape) Icons.Outlined.SwapHoriz else Icons.Outlined.SwapVert, contentDescription = "Intercambiar paneles", tint = Color.White)
        }
        IconButton(onClick = onCycleOrientation) {
            Icon(
                when (orientation) {
                    OrientationMode.AUTO -> Icons.Outlined.ScreenRotation
                    OrientationMode.PORTRAIT -> Icons.Outlined.StayCurrentPortrait
                    OrientationMode.LANDSCAPE -> Icons.Outlined.StayCurrentLandscape
                },
                contentDescription = "Orientación",
                tint = Color.White,
            )
        }
    }
}
