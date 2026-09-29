package com.mcifu.usbx.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.size.Size
import com.mcifu.usbx.domain.GestureZone
import com.mcifu.usbx.domain.PlaybackRules
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.OrientationMode
import com.mcifu.usbx.ui.browser.ErrorState
import com.mcifu.usbx.ui.common.CannotOpenDialog
import com.mcifu.usbx.ui.common.ExternalActions
import com.mcifu.usbx.ui.common.FileInfoDialog
import com.mcifu.usbx.ui.common.mediaSharedBounds
import com.mcifu.usbx.ui.player.CenterControls
import com.mcifu.usbx.ui.player.LockOverlay
import com.mcifu.usbx.ui.player.PlayerBottomControls
import com.mcifu.usbx.ui.player.SpeedSheet
import com.mcifu.usbx.ui.theme.UsbxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Tiempo sin tocar tras el que se ocultan los controles durante la reproducción. */
private const val PLAYER_CONTROLS_TIMEOUT_MS = 3_500L

/**
 * Visor a pantalla completa: fotos (Fase 2) y reproductor de vídeo y audio (Fase 3).
 *
 * - Deslizar entre los elementos de la carpeta según "qué recorrer" (fotos y vídeos por defecto).
 * - Fotos: zoom nítido, doble toque, girar, precarga, transición desde la miniatura.
 * - Vídeo/audio: reproducción en la misma página, barra de progreso con arrastre libre,
 *   velocidad, repetición, volumen, orientación, avance/retroceso rápido manteniendo pulsado,
 *   doble toque ±10 s y bloqueo de controles.
 */
@Composable
fun ViewerScreen(
    onBack: (lastDocumentId: String?) -> Unit,
    onGoHome: () -> Unit,
    viewModel: ViewerViewModel = viewModel(factory = ViewerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playerState by viewModel.player.state.collectAsStateWithLifecycle()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<FileItem?>(null) }
    var cannotOpen by remember { mutableStateOf<Pair<FileItem, Boolean>?>(null) }
    var showScopeDialog by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    /** Cambia con cada interacción: reinicia la cuenta atrás para ocultar controles. */
    var interactionTick by remember { mutableIntStateOf(0) }
    /** Giro visual por archivo (grados acumulados, para animar siempre por el camino corto). */
    val rotations = remember { mutableStateMapOf<String, Int>() }
    val context = LocalContext.current
    val controller = viewModel.player

    SystemBarsEffect(visible = controlsVisible && !locked)
    OrientationEffect(settings.orientation)

    // Pausa al salir de la app; la pantalla no se apaga mientras se reproduce.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.pause() }
    val view = LocalView.current
    DisposableEffect(playerState.isPlaying) {
        view.keepScreenOn = playerState.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    fun touch() {
        interactionTick++
    }

    fun openExternally(item: FileItem) {
        controller.pause()
        when (val result = ExternalActions.open(context, item)) {
            ExternalActions.OpenResult.Opened -> Unit
            is ExternalActions.OpenResult.NoApp -> cannotOpen = item to result.canOpenGeneric
        }
    }

    fun cycleOrientation() {
        viewModel.setOrientation(
            when (settings.orientation) {
                OrientationMode.AUTO -> OrientationMode.PORTRAIT
                OrientationMode.PORTRAIT -> OrientationMode.LANDSCAPE
                OrientationMode.LANDSCAPE -> OrientationMode.AUTO
            },
        )
    }

    // El visor siempre es oscuro, sea cual sea el tema del sistema.
    UsbxTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load, onGoHome = onGoHome)
                state.items.isEmpty() -> Text(
                    "No hay nada que mostrar con el filtro actual",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                // Nueva generación (reconexión, cambio de filtro) = pager nuevo en la posición correcta.
                else -> key(state.generation) {
                    val items = state.items
                    val pagerState = rememberPagerState(initialPage = state.initialIndex) { items.size }
                    val scope = rememberCoroutineScope()
                    val current = items.getOrNull(pagerState.currentPage)
                    // El reproductor sigue a la página asentada, no a la que pasa por delante al deslizar.
                    val settled = items.getOrNull(pagerState.settledPage)
                    val currentIsPlayable = current != null && PlaybackRules.isPlayable(current)

                    BackHandler {
                        // Con los controles bloqueados, Atrás no sale del vídeo por accidente.
                        if (!locked) onBack(current?.documentId)
                    }
                    LaunchedEffect(current) { current?.let(viewModel::onPageShown) }
                    LaunchedEffect(settled?.documentId) {
                        controller.setItem(settled?.takeIf(PlaybackRules::isPlayable))
                        if (settled == null || !PlaybackRules.isPlayable(settled)) locked = false
                    }
                    // "Repetir carpeta": al terminar, pasa al siguiente vídeo/audio (o repite si no hay otro).
                    LaunchedEffect(items) {
                        viewModel.advanceRequests.collect {
                            val next = PlaybackRules.nextPlayableIndex(items, pagerState.settledPage)
                            if (next == null) {
                                controller.seekTo(0)
                                controller.togglePlayPause()
                            } else {
                                pagerState.jumpOrAnimateTo(next)
                            }
                        }
                    }
                    // Controles del reproductor: se ocultan solos tras unos segundos reproduciendo.
                    LaunchedEffect(controlsVisible, playerState.isPlaying, currentIsPlayable, interactionTick) {
                        if (controlsVisible && currentIsPlayable && playerState.isPlaying) {
                            delay(PLAYER_CONTROLS_TIMEOUT_MS)
                            controlsVisible = false
                        }
                    }

                    // Al terminar (modo "una vez") vuelven los controles para repetir o pasar al siguiente.
                    LaunchedEffect(playerState.hasEnded) {
                        if (playerState.hasEnded && currentIsPlayable) controlsVisible = true
                    }

                    val windowSize = LocalWindowInfo.current.containerSize
                    val targetSize = remember(windowSize) {
                        if (windowSize.width > 0) Size(windowSize.width, windowSize.height) else Size(1080, 1920)
                    }
                    PreloadNeighbours(items, pagerState.currentPage, targetSize, viewModel::isViewableImage)

                    HorizontalPager(
                        state = pagerState,
                        // Compone también la página anterior y la siguiente: ya están listas al deslizar.
                        beyondViewportPageCount = 1,
                        key = { items[it].documentId },
                        pageSpacing = 16.dp,
                        // Bloqueado o con avance rápido activo, el deslizamiento no cambia de elemento.
                        userScrollEnabled = !locked && playerState.fastSeek == null,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val item = items[page]
                        val isCurrent = page == pagerState.currentPage
                        // Solo la página visible participa en la transición desde la miniatura.
                        val pageModifier = Modifier.mediaSharedBounds(item.documentId, enabled = isCurrent && item.type.isVisualMedia)
                        when {
                            viewModel.isViewableImage(item) -> ImagePage(
                                item = item,
                                isCurrent = isCurrent,
                                rotation = rotations[item.documentId] ?: 0,
                                targetSize = targetSize,
                                onTap = { controlsVisible = !controlsVisible },
                                onZoomedIn = { controlsVisible = false },
                                onOpenWith = { ExternalActions.openWith(context, item) },
                                modifier = pageModifier,
                            )
                            PlaybackRules.isPlayable(item) -> PlayerPage(
                                item = item,
                                isActive = page == pagerState.settledPage,
                                player = controller.player,
                                playerState = playerState,
                                locked = locked,
                                onTap = { controlsVisible = !controlsVisible; touch() },
                                onDoubleTap = { zone ->
                                    touch()
                                    if (zone == GestureZone.CENTER) {
                                        controller.togglePlayPause()
                                    } else {
                                        PlaybackRules.doubleTapTarget(playerState.positionMs, zone, playerState.durationMs)
                                            ?.let(controller::seekTo)
                                    }
                                },
                                onHoldStart = { zone -> controller.startFastSeek(forward = zone == GestureZone.RIGHT) },
                                onHoldEnd = controller::stopFastSeek,
                                onRetry = controller::retry,
                                onOpenWith = { ExternalActions.openWith(context, item) },
                                modifier = pageModifier,
                            )
                            else -> FilePage(
                                item = item,
                                onTap = { controlsVisible = !controlsVisible },
                                onOpen = { openExternally(item) },
                                onOpenWith = { ExternalActions.openWith(context, item) },
                            )
                        }
                    }

                    val showControls = controlsVisible && !locked
                    val playerActive = currentIsPlayable && playerState.item?.documentId == current?.documentId &&
                        playerState.error == null

                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        ViewerTopBar(
                            title = current?.name.orEmpty(),
                            counter = "${pagerState.currentPage + 1} / ${items.size}",
                            showFilmstrip = settings.showFilmstrip,
                            onBack = { onBack(current?.documentId) },
                            onInfo = { infoFor = current },
                            onShare = { current?.let { ExternalActions.share(context, listOf(it)) } },
                            onOpenWith = { current?.let { controller.pause(); ExternalActions.openWith(context, it) } },
                            onChooseScope = { showScopeDialog = true },
                            onToggleFilmstrip = { viewModel.setFilmstrip(!settings.showFilmstrip) },
                        )
                    }

                    AnimatedVisibility(
                        visible = showControls && playerActive,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        CenterControls(
                            state = playerState,
                            onSeekBack = { touch(); controller.seekTo((playerState.positionMs - PlaybackRules.DOUBLE_TAP_SEEK_MS).coerceAtLeast(0)) },
                            onPlayPause = { touch(); controller.togglePlayPause() },
                            onSeekForward = {
                                touch()
                                PlaybackRules.doubleTapTarget(playerState.positionMs, GestureZone.RIGHT, playerState.durationMs)
                                    ?.let(controller::seekTo)
                            },
                        )
                    }

                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        if (playerActive) {
                            PlayerBottomControls(
                                state = playerState,
                                orientation = settings.orientation,
                                onScrubStart = { touch(); controller.beginScrub() },
                                onScrub = { touch(); controller.scrubTo(it) },
                                onScrubEnd = { touch(); controller.endScrub(it) },
                                onLock = { locked = true; controlsVisible = false },
                                onSpeed = { touch(); showSpeedSheet = true },
                                onCycleLoop = { touch(); viewModel.cycleLoopMode() },
                                onVolume = { touch(); controller.setVolume(it) },
                                onCycleOrientation = { touch(); cycleOrientation() },
                            )
                        } else {
                            ViewerBottomBar(
                                items = items,
                                currentIndex = pagerState.currentPage,
                                showFilmstrip = settings.showFilmstrip,
                                canRotate = current != null && viewModel.isViewableImage(current),
                                orientation = settings.orientation,
                                onSelect = { index -> scope.launch { pagerState.jumpOrAnimateTo(index) } },
                                onRotateLeft = { current?.let { rotations[it.documentId] = (rotations[it.documentId] ?: 0) - 90 } },
                                onRotateRight = { current?.let { rotations[it.documentId] = (rotations[it.documentId] ?: 0) + 90 } },
                                onCycleOrientation = ::cycleOrientation,
                            )
                        }
                    }

                    if (locked) {
                        LockOverlay(onUnlock = { locked = false; controlsVisible = true; touch() })
                    }
                }
            }
        }

        infoFor?.let { item ->
            FileInfoDialog(item = item, loadDetails = viewModel::loadDetails, onDismiss = { infoFor = null })
        }
        cannotOpen?.let { (item, canOpenGeneric) ->
            CannotOpenDialog(
                fileName = item.name,
                canOpenGeneric = canOpenGeneric,
                onOpenWithOther = {
                    cannotOpen = null
                    ExternalActions.openWith(context, item, anyType = true)
                },
                onDismiss = { cannotOpen = null },
            )
        }
        if (showScopeDialog) {
            ScopeDialog(
                current = settings.scope,
                onSelect = { viewModel.setScope(it); showScopeDialog = false },
                onDismiss = { showScopeDialog = false },
            )
        }
        if (showSpeedSheet) {
            SpeedSheet(
                current = playerState.speed,
                onSelect = { controller.setSpeed(it); showSpeedSheet = false },
                onDismiss = { showSpeedSheet = false },
            )
        }
    }
}

/** Saltos largos (tira de miniaturas) sin animar decenas de páginas intermedias. */
private suspend fun PagerState.jumpOrAnimateTo(index: Int) {
    if (abs(index - currentPage) > 3) scrollToPage(index) else animateScrollToPage(index)
}
