package com.mcifu.usbx.ui.multiview

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mcifu.usbx.data.storage.contentUri
import com.mcifu.usbx.data.thumbnail.toThumbnail
import com.mcifu.usbx.domain.LoopMode
import com.mcifu.usbx.domain.MultiviewRules
import com.mcifu.usbx.domain.PlaybackRules
import com.mcifu.usbx.domain.ZoomTransform
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.ui.common.Formatters
import com.mcifu.usbx.ui.player.PlayerErrorOverlay
import com.mcifu.usbx.ui.player.PlayerState
import com.mcifu.usbx.ui.player.SeekBar
import com.mcifu.usbx.ui.player.speedLabel
import kotlinx.coroutines.delay

/** Cabecera de cada panel: nombre, cambiar archivo y cerrar panel. */
@Composable
internal fun PaneHeader(item: FileItem, onChange: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .padding(start = 12.dp, end = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.name,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onChange) { Icon(Icons.Outlined.Collections, contentDescription = "Cambiar archivo", tint = Color.White) }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar panel", tint = Color.White) }
    }
}

/** Panel vacío: invita a elegir la foto o el vídeo a comparar. */
@Composable
internal fun EmptyPane(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFF101414)), contentAlignment = Alignment.Center) {
        FilledTonalButton(onClick = onPick) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Elegir foto o vídeo")
        }
    }
}

/**
 * Foto con zoom propio. El estado de zoom vive fuera del panel: con "zoom sincronizado" los dos
 * paneles leen y escriben el mismo, así ampliar o mover uno mueve el otro igual.
 *
 * Sin subsampling (el zoom compartido no es compatible con el visor por teselas): la foto se carga
 * al doble del tamaño del panel, suficiente para comparar detalle hasta ~3×.
 */
@Composable
internal fun ImagePane(
    item: FileItem,
    transform: ZoomTransform,
    onTransform: (ZoomTransform) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(transform)
    val update by rememberUpdatedState(onTransform)
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { p ->
                    update(MultiviewRules.doubleTap(current, p.x - size.width / 2f, p.y - size.height / 2f, size.width.toFloat(), size.height.toFloat()))
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    update(
                        MultiviewRules.applyGesture(
                            current, zoom, pan.x, pan.y,
                            centroid.x - size.width / 2f, centroid.y - size.height / 2f,
                            size.width.toFloat(), size.height.toFloat(),
                        ),
                    )
                }
            },
    ) {
        if (size.width > 0) {
            val request = remember(item.uri, size) {
                ImageRequest.Builder(context)
                    .data(item.contentUri)
                    .size((size.width * 2).coerceAtMost(4096), (size.height * 2).coerceAtMost(4096))
                    .placeholderMemoryCacheKey(item.toThumbnail().cacheKey)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.offsetX
                    translationY = transform.offsetY
                },
            )
        }
    }
}

/** Vídeo en un panel con controles compactos propios (se ocultan solos al reproducir). */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPane(
    item: FileItem,
    player: Player?,
    state: PlayerState,
    onPlayPause: () -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
    onToggleLoop: () -> Unit,
    onRetry: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, state.isPlaying, tick) {
        if (controlsVisible && state.isPlaying) {
            delay(3_500)
            controlsVisible = false
        }
    }
    val attached = player != null && state.item?.documentId == item.documentId

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (attached) {
            ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, shutter = { Poster(item) })
        } else {
            Poster(item)
        }
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) {
                controlsVisible = !controlsVisible
                tick++
            },
        )
        AnimatedVisibility(
            visible = controlsVisible && attached,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            IconButton(
                onClick = { onPlayPause(); tick++ },
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    when {
                        state.hasEnded -> Icons.Filled.Replay
                        state.isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = if (state.isPlaying) "Pausa" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = controlsVisible && attached,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PaneControls(
                state = state,
                onScrubStart = { onScrubStart(); tick++ },
                onScrub = { onScrub(it); tick++ },
                onScrubEnd = { onScrubEnd(it); tick++ },
                onVolume = { onVolume(it); tick++ },
                onSpeed = { onSpeed(it); tick++ },
                onToggleLoop = { onToggleLoop(); tick++ },
            )
        }
        if (attached) state.error?.let { PlayerErrorOverlay(it, onRetry = onRetry, onOpenWith = onOpenWith) }
    }
}

@Composable
private fun PaneControls(
    state: PlayerState,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
    onToggleLoop: () -> Unit,
) {
    var speedOpen by remember { mutableStateOf(false) }
    var volumeOpen by remember { mutableStateOf(false) }
    var lastVolume by remember { mutableFloatStateOf(1f) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(horizontal = 8.dp)
            .padding(top = 16.dp),
    ) {
        SeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            bufferedMs = state.bufferedMs,
            onScrubStart = onScrubStart,
            onScrub = onScrub,
            onScrubEnd = onScrubEnd,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${Formatters.duration(state.positionMs)} / ${Formatters.duration(state.durationMs)}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            IconButton(onClick = onToggleLoop) {
                Icon(
                    if (state.loopMode == LoopMode.REPEAT_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.TrendingFlat,
                    contentDescription = if (state.loopMode == LoopMode.REPEAT_ONE) "Repetir vídeo" else "Reproducir una vez",
                    tint = Color.White,
                )
            }
            Box {
                TextButton(onClick = { speedOpen = true }) { Text(speedLabel(state.speed), color = Color.White) }
                DropdownMenu(expanded = speedOpen, onDismissRequest = { speedOpen = false }) {
                    PlaybackRules.SPEEDS.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text(speedLabel(speed)) },
                            onClick = { onSpeed(speed); speedOpen = false },
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { volumeOpen = true }) {
                    Icon(
                        if (state.volume == 0f) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = if (state.volume == 0f) "Silenciado" else "Volumen",
                        tint = Color.White,
                    )
                }
                DropdownMenu(expanded = volumeOpen, onDismissRequest = { volumeOpen = false }) {
                    Column(Modifier.width(220.dp).padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Slider(value = state.volume, onValueChange = { if (it > 0f) lastVolume = it; onVolume(it) })
                        TextButton(onClick = { onVolume(if (state.volume == 0f) lastVolume else 0f) }) {
                            Text(if (state.volume == 0f) "Activar sonido" else "Silenciar")
                        }
                    }
                }
            }
        }
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
    AsyncImage(model = request, contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
}

/** Etiqueta de sincronización, p. ej. "desfase +2,3 s". */
internal fun offsetLabel(offsetMs: Long): String {
    val seconds = offsetMs / 1000.0
    val sign = if (offsetMs > 0) "+" else if (offsetMs < 0) "−" else "±"
    return "desfase $sign${"%.1f".format(kotlin.math.abs(seconds))} s"
}

