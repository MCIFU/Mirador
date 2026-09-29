package com.mcifu.usbx.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.StayCurrentLandscape
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcifu.usbx.domain.GestureZone
import com.mcifu.usbx.domain.LoopMode
import com.mcifu.usbx.domain.PlaybackRules
import com.mcifu.usbx.domain.model.OrientationMode
import com.mcifu.usbx.ui.common.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Botones centrales: −10 s, play/pausa grande, +10 s. */
@Composable
fun CenterControls(
    state: PlayerState,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(Icons.Filled.Replay10, "Retroceder 10 segundos", 52.dp, onSeekBack)
        Box(contentAlignment = Alignment.Center) {
            val icon = when {
                state.hasEnded -> Icons.Filled.Replay
                state.isPlaying -> Icons.Filled.Pause
                else -> Icons.Filled.PlayArrow
            }
            RoundButton(icon, if (state.isPlaying) "Pausa" else "Reproducir", 72.dp, onPlayPause)
            if (state.isBuffering) CircularProgressIndicator(Modifier.size(76.dp), color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
        }
        RoundButton(Icons.Filled.Forward10, "Avanzar 10 segundos", 52.dp, onSeekForward)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** Barra inferior: tiempos, barra de progreso y ajustes de reproducción. */
@Composable
fun PlayerBottomControls(
    state: PlayerState,
    orientation: OrientationMode,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onLock: () -> Unit,
    onSpeed: () -> Unit,
    onCycleLoop: () -> Unit,
    onVolume: (Float) -> Unit,
    onCycleOrientation: () -> Unit,
) {
    var volumeOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeLabel(state.positionMs)
            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                bufferedMs = state.bufferedMs,
                onScrubStart = onScrubStart,
                onScrub = onScrub,
                onScrubEnd = onScrubEnd,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            TimeLabel(state.durationMs)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BarButton(Icons.Outlined.Lock, "Bloquear controles", "Bloquear", onLock)
            BarButton(Icons.Outlined.Speed, "Velocidad ${speedLabel(state.speed)}", speedLabel(state.speed), onSpeed)
            val (loopIcon, loopLabel) = when (state.loopMode) {
                LoopMode.REPEAT_ONE -> Icons.Outlined.RepeatOne to "Repetir"
                LoopMode.PLAY_ONCE -> Icons.Outlined.TrendingFlat to "Una vez"
                LoopMode.REPEAT_FOLDER -> Icons.Outlined.Repeat to "Carpeta"
            }
            BarButton(loopIcon, "Modo de repetición: $loopLabel", loopLabel, onCycleLoop)
            Spacer(Modifier.weight(1f))
            Box {
                BarButton(
                    if (state.volume == 0f) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                    "Volumen",
                    "${(state.volume * 100).toInt()} %",
                ) { volumeOpen = true }
                DropdownMenu(expanded = volumeOpen, onDismissRequest = { volumeOpen = false }) {
                    Column(Modifier.width(240.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Volumen del vídeo", style = MaterialTheme.typography.labelLarge)
                        Slider(value = state.volume, onValueChange = onVolume)
                        Text(
                            "Los botones del móvil controlan el volumen general.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val (orientationIcon, orientationLabel) = when (orientation) {
                OrientationMode.AUTO -> Icons.Outlined.ScreenRotation to "Auto"
                OrientationMode.PORTRAIT -> Icons.Outlined.StayCurrentPortrait to "Vertical"
                OrientationMode.LANDSCAPE -> Icons.Outlined.StayCurrentLandscape to "Horizontal"
            }
            BarButton(orientationIcon, "Orientación: $orientationLabel", orientationLabel, onCycleOrientation)
        }
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(
        Formatters.duration(ms),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(52.dp),
    )
}

@Composable
private fun BarButton(icon: ImageVector, description: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = Color.White) }
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) "${speed.toInt()}" else speed.toString().trimEnd('0').replace('.', ',')) + "×"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedSheet(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Velocidad de reproducción", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaybackRules.SPEEDS.forEach { speed ->
                    FilterChip(
                        selected = speed == current,
                        onClick = { onSelect(speed) },
                        label = { Text(speedLabel(speed)) },
                    )
                }
            }
        }
    }
}

/** Indicador del avance/retroceso rápido mientras se mantiene el dedo. */
@Composable
fun FastSeekIndicator(fastSeek: FastSeek?, positionMs: Long, modifier: Modifier = Modifier) {
    AnimatedVisibility(fastSeek != null, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut(), modifier = modifier) {
        val seek = fastSeek ?: return@AnimatedVisibility
        Row(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .semantics { contentDescription = (if (seek.forward) "Avance" else "Retroceso") + " rápido ${seek.rate}×" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(if (seek.forward) Icons.Filled.FastForward else Icons.Filled.FastRewind, null, tint = Color.White)
            Text("${seek.rate}×", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(Formatters.duration(positionMs), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Confirmación visual del doble toque (±10 s) en el lado pulsado. */
@Composable
fun DoubleTapFeedback(zone: GestureZone?, trigger: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger == 0 || zone == null || zone == GestureZone.CENTER) return@LaunchedEffect
        visible = true
        delay(600)
        visible = false
    }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible && zone != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(if (zone == GestureZone.LEFT) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 48.dp),
        ) {
            Column(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(if (zone == GestureZone.LEFT) Icons.Filled.FastRewind else Icons.Filled.FastForward, null, tint = Color.White)
                Text(if (zone == GestureZone.LEFT) "−10 s" else "+10 s", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Capa de bloqueo: captura todos los toques para que no cambien la reproducción. Muestra
 * "Controles bloqueados" al tocar; mantener pulsado 1 segundo en cualquier punto desbloquea
 * (con un anillo que se va llenando).
 */
@Composable
fun LockOverlay(onUnlock: () -> Unit) {
    val unlock by rememberUpdatedState(onUnlock)
    val progress = remember { Animatable(0f) }
    var hintVisible by remember { mutableStateOf(true) }
    var hintToken by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(hintToken) {
        hintVisible = true
        delay(2_000)
        if (progress.value == 0f) hintVisible = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    hintToken++
                    val fill = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(UNLOCK_HOLD_MS, easing = LinearEasing))
                        unlock()
                    }
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    if (fill.isActive) {
                        fill.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(hintVisible || progress.value > 0f, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                    Icon(
                        if (progress.value > 0.95f) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Text("🔒 Controles bloqueados", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text("Mantén pulsado para desbloquear", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private const val UNLOCK_HOLD_MS = 1_000

/** Error de reproducción con alternativas: reintentar o abrir con otra app. */
@Composable
fun PlayerErrorOverlay(error: PlayerError, onRetry: () -> Unit, onOpenWith: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(
            when (error) {
                PlayerError.UNSUPPORTED_FORMAT -> "El reproductor integrado no admite este formato"
                PlayerError.READ_ERROR -> "No se puede leer el archivo"
                PlayerError.OTHER -> "No se puede reproducir este archivo"
            },
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            if (error == PlayerError.UNSUPPORTED_FORMAT) "Suele deberse a un códec que este móvil no decodifica. Otra app (p. ej. VLC) puede tener soporte propio."
            else "Comprueba que la memoria sigue conectada.",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry) { Text("Reintentar", color = Color.White) }
            Button(onClick = onOpenWith) { Text("Abrir con otra aplicación") }
        }
    }
}
