package com.mcifu.usbx.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mcifu.usbx.domain.PlaybackRules
import com.mcifu.usbx.ui.common.Formatters
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Barra de progreso pensada para el dedo:
 *
 * - Zona táctil de 48 dp aunque la barra dibujada sea fina.
 * - Arrastre libre adelante/atrás; el punto crece y una burbuja muestra el tiempo de destino.
 * - Mientras se arrastra, la barra sigue al dedo (no a la posición del reproductor) y el vídeo
 *   muestra el fotograma más cercano como vista previa.
 * - Al soltar, la barra se queda en el destino hasta que el reproductor llega: sin saltos atrás.
 * - Toque en cualquier punto: salta a esa posición.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableStateOf(0) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    /** Destino pendiente tras soltar: se muestra hasta que el reproductor lo alcanza. */
    var pendingMs by remember { mutableStateOf<Long?>(null) }
    val duration by rememberUpdatedState(durationMs)
    val scrubStart by rememberUpdatedState(onScrubStart)
    val scrub by rememberUpdatedState(onScrub)
    val scrubEnd by rememberUpdatedState(onScrubEnd)

    LaunchedEffect(pendingMs, positionMs) {
        val pending = pendingMs ?: return@LaunchedEffect
        if (abs(positionMs - pending) < 600) pendingMs = null
        else {
            delay(1_500) // por si el reproductor se detuvo en otro fotograma clave
            pendingMs = null
        }
    }

    val shownMs = dragFraction?.let { PlaybackRules.positionForFraction(it, durationMs) } ?: pendingMs ?: positionMs
    val fraction = if (durationMs > 0) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val dragging = dragFraction != null
    val thumbRadius by animateDpAsState(if (dragging) 9.dp else 6.dp, label = "thumb")
    val trackHeight by animateDpAsState(if (dragging) 6.dp else 4.dp, label = "track")
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { widthPx = it.width }
            .semantics {
                contentDescription = "Posición ${Formatters.duration(shownMs)} de ${Formatters.duration(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    scrubEnd(PlaybackRules.positionForFraction(target, duration))
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (widthPx <= 0 || duration <= 0) return@detectTapGestures
                    val target = PlaybackRules.positionForFraction(offset.x / widthPx, duration)
                    scrubStart()
                    pendingMs = target
                    scrubEnd(target)
                }
            }
            .pointerInput(Unit) {
                var lastSent = 0L
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (duration <= 0 || widthPx <= 0) return@detectHorizontalDragGestures
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        scrubStart()
                    },
                    onDragEnd = {
                        val f = dragFraction ?: return@detectHorizontalDragGestures
                        val target = PlaybackRules.positionForFraction(f, duration)
                        pendingMs = target
                        dragFraction = null
                        scrubEnd(target)
                    },
                    onDragCancel = {
                        val f = dragFraction ?: return@detectHorizontalDragGestures
                        val target = PlaybackRules.positionForFraction(f, duration)
                        pendingMs = target
                        dragFraction = null
                        scrubEnd(target)
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val f = dragFraction ?: return@detectHorizontalDragGestures
                    dragFraction = (f + dragAmount / widthPx).coerceIn(0f, 1f)
                    // Vista previa limitada a ~8 búsquedas por segundo: da tiempo a pintar fotogramas.
                    val now = System.currentTimeMillis()
                    if (now - lastSent > 120) {
                        lastSent = now
                        scrub(PlaybackRules.positionForFraction(dragFraction!!, duration))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val h = trackHeight.toPx()
            val y = size.height / 2 - h / 2
            val radius = CornerRadius(h / 2, h / 2)
            drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y), Size(size.width, h), radius)
            drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(0f, y), Size(size.width * bufferedFraction, h), radius)
            drawRoundRect(primary, Offset(0f, y), Size(size.width * fraction, h), radius)
            drawCircle(primary, thumbRadius.toPx(), Offset(size.width * fraction, size.height / 2))
        }
        if (dragging && widthPx > 0) {
            val density = LocalDensity.current
            val bubbleWidth = 72.dp
            val bubblePx = with(density) { bubbleWidth.toPx() }
            val x = (widthPx * fraction - bubblePx / 2).coerceIn(0f, widthPx - bubblePx)
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), -44.dp.roundToPx()) }
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(Formatters.duration(shownMs), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
