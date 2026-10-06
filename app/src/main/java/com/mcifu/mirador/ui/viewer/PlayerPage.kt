package com.mcifu.mirador.ui.viewer

import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.mcifu.mirador.ui.player.rememberVideoZoomState
import com.mcifu.mirador.ui.player.videoZoomGestures
import com.mcifu.mirador.ui.player.videoZoomLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mcifu.mirador.data.thumbnail.toThumbnail
import com.mcifu.mirador.domain.GestureZone
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.domain.model.FileType
import com.mcifu.mirador.ui.browser.FileThumbnail
import com.mcifu.mirador.ui.common.LocalNavAnimatedScope
import com.mcifu.mirador.ui.player.DoubleTapFeedback
import com.mcifu.mirador.ui.player.FastSeekIndicator
import com.mcifu.mirador.ui.player.PlayerError
import com.mcifu.mirador.ui.player.PlayerErrorOverlay
import com.mcifu.mirador.ui.player.PlayerState
import com.mcifu.mirador.ui.player.playerGestures

/**
 * Página de vídeo o audio dentro del visor.
 *
 * Solo la página asentada (la que está en pantalla) tiene el reproductor conectado; las vecinas
 * muestran su portada, que también se ve mientras llega el primer fotograma.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerPage(
    item: FileItem,
    isActive: Boolean,
    player: Player?,
    playerState: PlayerState,
    locked: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (GestureZone) -> Unit,
    onHoldStart: (GestureZone) -> Unit,
    onHoldEnd: () -> Unit,
    onRetry: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier,
    /** Avisa al visor de si el vídeo está ampliado (entonces deslizar mueve la imagen, no cambia de vídeo). */
    onZoomChanged: (Boolean) -> Unit = {},
) {
    val attached = isActive && player != null && playerState.item?.documentId == item.documentId
    // Mientras la página crece desde la miniatura (o vuelve a ella al cerrar) se dibuja la portada:
    // la superficie de vídeo (SurfaceView) no sigue bien la escala y el recorte de la animación.
    val transitionRunning = LocalNavAnimatedScope.current?.transition?.isRunning == true
    val showVideo = attached && !transitionRunning
    val zoom = rememberVideoZoomState(item.documentId)
    val zoomChanged by rememberUpdatedState(onZoomChanged)
    val scope = rememberCoroutineScope()
    // Al salir de la página, el vídeo vuelve a encajar.
    LaunchedEffect(isActive) { if (!isActive) zoom.reset(animated = false) }
    LaunchedEffect(zoom) { snapshotFlow { zoom.isZoomed }.collect { zoomChanged(it) } }
    // Atrás con el vídeo ampliado: primero quita el zoom.
    BackHandler(enabled = isActive && zoom.isZoomed) { scope.launch { zoom.reset() } }
    var doubleTapZone by remember { mutableStateOf<GestureZone?>(null) }
    var doubleTapCount by remember { mutableIntStateOf(0) }

    Box(modifier.fillMaxSize().clipToBounds().background(Color.Black), contentAlignment = Alignment.Center) {
        if (item.type == FileType.VIDEO) {
            Box(Modifier.fillMaxSize().videoZoomLayer(zoom)) {
                if (showVideo) {
                    ContentFrame(
                        player = player,
                        modifier = Modifier.fillMaxSize(),
                        // TextureView: sigue el zoom y las animaciones como cualquier otra capa
                        // (SurfaceView en Android 13 ignora la escala de la capa gráfica).
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                        contentScale = ContentScale.Fit,
                        shutter = { Poster(item) },
                    )
                } else {
                    Poster(item)
                }
            }
        } else {
            AudioArt(item)
        }

        // Capa de gestos (debajo de los controles, que se dibujan en la pantalla del visor).
        Box(
            Modifier
                .fillMaxSize()
                .videoZoomGestures(zoom, enabled = attached && !locked && item.type == FileType.VIDEO)
                .playerGestures(
                    enabled = attached && !locked,
                    onTap = onTap,
                    onDoubleTap = { zone ->
                        doubleTapZone = zone
                        doubleTapCount++
                        onDoubleTap(zone)
                    },
                    onHoldStart = onHoldStart,
                    onHoldEnd = onHoldEnd,
                ),
        )

        if (attached && zoom.isZoomed) {
            ZoomChip(
                label = zoom.label,
                onReset = { scope.launch { zoom.reset() } },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 132.dp),
            )
        }

        if (attached) {
            DoubleTapFeedback(doubleTapZone, doubleTapCount)
            FastSeekIndicator(
                playerState.fastSeek,
                playerState.positionMs,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
            )
            val error: PlayerError? = playerState.error
            if (error != null) PlayerErrorOverlay(error, onRetry = onRetry, onOpenWith = onOpenWith, detail = playerState.errorDetail)
        }
    }
}

/** Nivel de zoom actual; al tocarlo vuelve a 1×. */
@Composable
private fun ZoomChip(label: String, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onReset)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
        Icon(Icons.Outlined.ZoomOutMap, contentDescription = "Quitar zoom", tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Poster(item: FileItem) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        val thumbnail = item.toThumbnail()
        ImageRequest.Builder(context)
            .data(thumbnail)
            .size(768)
            .memoryCacheKey(thumbnail.cacheKey)
            .placeholderMemoryCacheKey(thumbnail.cacheKey)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = item.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun AudioArt(item: FileItem) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        FileThumbnail(
            item = item,
            iconSize = 72.dp,
            showVideoBadge = false,
            modifier = Modifier.size(240.dp).clip(MaterialTheme.shapes.large),
        )
        Text(
            item.name,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.padding(top = 20.dp).widthIn(max = 420.dp),
        )
    }
}
