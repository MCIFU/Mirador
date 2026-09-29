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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.size.Size
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileType
import com.mcifu.usbx.domain.model.OrientationMode
import com.mcifu.usbx.ui.browser.ErrorState
import com.mcifu.usbx.ui.common.CannotOpenDialog
import com.mcifu.usbx.ui.common.ExternalActions
import com.mcifu.usbx.ui.common.FileInfoDialog
import com.mcifu.usbx.ui.common.mediaSharedBounds
import com.mcifu.usbx.ui.theme.UsbxTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Visor a pantalla completa (Fase 2).
 *
 * - Deslizar entre los elementos de la carpeta según "qué recorrer" (fotos y vídeos por defecto).
 * - Zoom con pellizco y doble toque, arrastre al estar ampliada, nitidez real por subsampling.
 * - Precarga de las imágenes vecinas; transición animada desde la miniatura.
 * - Toque para mostrar/ocultar controles; al ampliar se ocultan solos.
 * - Girar la imagen, bloquear orientación, tira de miniaturas, información y compartir.
 *
 * Los vídeos muestran su portada y se reproducen con la app instalada hasta la Fase 3.
 */
@Composable
fun ViewerScreen(
    onBack: (lastDocumentId: String?) -> Unit,
    onGoHome: () -> Unit,
    viewModel: ViewerViewModel = viewModel(factory = ViewerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var infoFor by remember { mutableStateOf<FileItem?>(null) }
    var cannotOpen by remember { mutableStateOf<Pair<FileItem, Boolean>?>(null) }
    var showScopeDialog by remember { mutableStateOf(false) }
    /** Giro visual por archivo (grados acumulados, para animar siempre por el camino corto). */
    val rotations = remember { mutableStateMapOf<String, Int>() }
    val context = LocalContext.current

    SystemBarsEffect(visible = controlsVisible)
    OrientationEffect(settings.orientation)

    fun openExternally(item: FileItem) {
        when (val result = ExternalActions.open(context, item)) {
            ExternalActions.OpenResult.Opened -> Unit
            is ExternalActions.OpenResult.NoApp -> cannotOpen = item to result.canOpenGeneric
        }
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

                    BackHandler { onBack(current?.documentId) }
                    LaunchedEffect(current) { current?.let(viewModel::onPageShown) }

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
                            item.type == FileType.VIDEO -> VideoPage(
                                item = item,
                                onTap = { controlsVisible = !controlsVisible },
                                onPlay = { openExternally(item) },
                                modifier = pageModifier,
                            )
                            item.type == FileType.AUDIO -> AudioPage(
                                item = item,
                                onTap = { controlsVisible = !controlsVisible },
                                onPlay = { openExternally(item) },
                            )
                            else -> FilePage(
                                item = item,
                                onTap = { controlsVisible = !controlsVisible },
                                onOpen = { openExternally(item) },
                                onOpenWith = { ExternalActions.openWith(context, item) },
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = controlsVisible,
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
                            onOpenWith = { current?.let { ExternalActions.openWith(context, it) } },
                            onChooseScope = { showScopeDialog = true },
                            onToggleFilmstrip = { viewModel.setFilmstrip(!settings.showFilmstrip) },
                        )
                    }

                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        ViewerBottomBar(
                            items = items,
                            currentIndex = pagerState.currentPage,
                            showFilmstrip = settings.showFilmstrip,
                            canRotate = current != null && viewModel.isViewableImage(current),
                            orientation = settings.orientation,
                            onSelect = { index -> scope.launch { pagerState.jumpOrAnimateTo(index) } },
                            onRotateLeft = { current?.let { rotations[it.documentId] = (rotations[it.documentId] ?: 0) - 90 } },
                            onRotateRight = { current?.let { rotations[it.documentId] = (rotations[it.documentId] ?: 0) + 90 } },
                            onCycleOrientation = {
                                viewModel.setOrientation(
                                    when (settings.orientation) {
                                        OrientationMode.AUTO -> OrientationMode.PORTRAIT
                                        OrientationMode.PORTRAIT -> OrientationMode.LANDSCAPE
                                        OrientationMode.LANDSCAPE -> OrientationMode.AUTO
                                    },
                                )
                            },
                        )
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
    }
}

/** Saltos largos (tira de miniaturas) sin animar decenas de páginas intermedias. */
private suspend fun PagerState.jumpOrAnimateTo(index: Int) {
    if (abs(index - currentPage) > 3) scrollToPage(index) else animateScrollToPage(index)
}
