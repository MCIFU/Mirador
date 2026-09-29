package com.mcifu.usbx.ui.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.size.Size
import com.mcifu.usbx.data.storage.contentUri
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.ui.browser.ErrorState
import com.mcifu.usbx.ui.common.ExternalActions
import com.mcifu.usbx.ui.common.FileInfoDialog
import com.mcifu.usbx.ui.theme.UsbxTheme
import kotlinx.coroutines.delay

/**
 * Visor de imágenes (Fase 1): pantalla completa, deslizar entre las imágenes de la carpeta,
 * precarga de las vecinas y controles que se ocultan con un toque.
 *
 * Zoom, doble toque, rotación y transiciones avanzadas llegan en la Fase 2.
 */
@Composable
fun ImageViewerScreen(
    onBack: (lastDocumentId: String?) -> Unit,
    onGoHome: () -> Unit,
    viewModel: ImageViewerViewModel = viewModel(factory = ImageViewerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var infoFor by remember { mutableStateOf<FileItem?>(null) }
    val context = LocalContext.current

    SystemBarsEffect(visible = controlsVisible)

    // El visor siempre es oscuro, sea cual sea el tema del sistema.
    UsbxTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load, onGoHome = onGoHome)
                state.items.isEmpty() -> Text(
                    "No hay imágenes en esta carpeta",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    val items = state.items
                    val pagerState = rememberPagerState(initialPage = state.initialIndex) { items.size }
                    val current = items.getOrNull(pagerState.currentPage)

                    BackHandler { onBack(current?.documentId) }
                LaunchedEffect(current) { current?.let(viewModel::onPageShown) }

                    val windowSize = LocalWindowInfo.current.containerSize
                    val targetSize = remember(windowSize) {
                        if (windowSize.width > 0) Size(windowSize.width, windowSize.height) else Size(1080, 1920)
                    }
                    PreloadNeighbours(items, pagerState.currentPage, targetSize)

                    HorizontalPager(
                        state = pagerState,
                        // Compone también la página anterior y la siguiente: ya están decodificadas
                        // cuando el usuario desliza.
                        beyondViewportPageCount = 1,
                        key = { items[it].documentId },
                        pageSpacing = 12.dp,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        ViewerPage(
                            item = items[page],
                            targetSize = targetSize,
                            onTap = { controlsVisible = !controlsVisible },
                            onOpenWith = { ExternalActions.openWith(context, items[page]) },
                        )
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
                            onBack = { onBack(current?.documentId) },
                            onInfo = { infoFor = current },
                            onShare = { current?.let { ExternalActions.share(context, listOf(it)) } },
                            onOpenWith = { current?.let { ExternalActions.openWith(context, it) } },
                        )
                    }
                }
            }
        }

        infoFor?.let { item ->
            FileInfoDialog(item = item, loadDetails = viewModel::loadDetails, onDismiss = { infoFor = null })
        }
    }
}

@Composable
private fun ViewerPage(
    item: FileItem,
    targetSize: Size,
    onTap: () -> Unit,
    onOpenWith: () -> Unit,
) {
    val context = LocalContext.current
    val request = remember(item.uri, targetSize) { viewerRequest(context, item, targetSize) }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        ) {
            val painterState by painter.state.collectAsStateWithLifecycle()
            when (painterState) {
                is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                is AsyncImagePainter.State.Error -> PageError(onOpenWith)
                else -> DelayedSpinner()
            }
        }
    }
}

/** El indicador solo aparece si la imagen tarda: con la precarga normalmente no se ve nunca. */
@Composable
private fun DelayedSpinner() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        show = true
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (show) CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun PageError(onOpenWith: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text("No se puede mostrar esta imagen", color = Color.White, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onOpenWith) { Text("Abrir con otra aplicación", color = Color.White) }
    }
}

@Composable
private fun ViewerTopBar(
    title: String,
    counter: String,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .statusBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al explorador", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.titleMedium)
                Text(counter, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, contentDescription = "Información", tint = Color.White) }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Compartir", tint = Color.White) }
            IconButton(onClick = onOpenWith) { Icon(Icons.Outlined.Apps, contentDescription = "Abrir con…", tint = Color.White) }
        }
    }
}

/**
 * Precarga en caché de memoria las dos imágenes a cada lado de la actual con el mismo tamaño
 * que usa la página, de modo que al deslizar la imagen ya está decodificada.
 */
@Composable
private fun PreloadNeighbours(items: List<FileItem>, currentPage: Int, targetSize: Size) {
    val context = LocalContext.current
    LaunchedEffect(items, targetSize, currentPage) {
        // Pequeña espera: al pasar fotos muy rápido no se encolan precargas que no se verán.
        delay(120)
        val loader = SingletonImageLoader.get(context)
        listOf(currentPage + 1, currentPage - 1, currentPage + 2, currentPage - 2)
            .mapNotNull { items.getOrNull(it) }
            .forEach { loader.enqueue(viewerRequest(context, it, targetSize)) }
    }
}

private fun viewerRequest(context: android.content.Context, item: FileItem, targetSize: Size) =
    ImageRequest.Builder(context)
        .data(item.contentUri)
        .size(targetSize)
        .memoryCacheKey("viewer:${item.uri}:${item.lastModified}:${targetSize.width}x${targetSize.height}")
        .build()

/** Oculta barras de estado y navegación cuando se ocultan los controles (modo inmersivo). */
@Composable
private fun SystemBarsEffect(visible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(visible) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = !isDarkUi(view)
        }
    }
}

private fun isDarkUi(view: android.view.View): Boolean =
    (view.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
