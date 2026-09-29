package com.mcifu.usbx.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.mcifu.usbx.domain.GestureZone
import com.mcifu.usbx.domain.PlaybackRules

private enum class FirstPhase { TAP, MOVED, HOLD }

/**
 * Gestos del reproductor sobre la imagen del vídeo:
 *
 * - Toque: mostrar/ocultar controles (tras descartar que sea un doble toque).
 * - Doble toque izquierda/derecha: ±10 s. Doble toque en el centro: play/pausa.
 * - Mantener pulsado izquierda/derecha: retroceso/avance rápido mientras dure la pulsación.
 *
 * Para no interferir con el resto de la app:
 * - Si el dedo se desplaza más allá del umbral del sistema antes de la pulsación larga, el gesto
 *   se abandona sin consumir nada: el deslizamiento entre vídeos (pager) sigue funcionando.
 * - Varios dedos cancelan el gesto.
 * - Los botones y la barra de progreso están por encima y consumen sus propios toques.
 * - Una pulsación larga no mueve el dedo, así que no activa los gestos de borde del sistema.
 */
@Composable
fun Modifier.playerGestures(
    enabled: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (GestureZone) -> Unit,
    onHoldStart: (GestureZone) -> Unit,
    onHoldEnd: () -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val holdStart by rememberUpdatedState(onHoldStart)
    val holdEnd by rememberUpdatedState(onHoldEnd)
    if (!enabled) return this
    return pointerInput(Unit) {
        val longPressMs = viewConfiguration.longPressTimeoutMillis
        val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
        val slop = viewConfiguration.touchSlop

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val zone = PlaybackRules.zoneOf(down.position.x, size.width.toFloat())

            val phase = withTimeoutOrNull(longPressMs) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.size > 1) return@withTimeoutOrNull FirstPhase.MOVED
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull FirstPhase.MOVED
                    if (change.isConsumed) return@withTimeoutOrNull FirstPhase.MOVED
                    if (change.changedToUp()) return@withTimeoutOrNull FirstPhase.TAP
                    if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull FirstPhase.MOVED
                }
                @Suppress("UNREACHABLE_CODE")
                FirstPhase.MOVED
            } ?: FirstPhase.HOLD

            when (phase) {
                FirstPhase.MOVED -> Unit
                FirstPhase.HOLD -> {
                    if (zone == GestureZone.CENTER) {
                        // Centro: sin acción; se deja terminar el gesto sin hacer nada.
                        return@awaitEachGesture
                    }
                    holdStart(zone)
                    try {
                        // A partir de aquí el gesto es nuestro: nada de desplazar el pager.
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        holdEnd()
                    }
                }
                FirstPhase.TAP -> {
                    val second = withTimeoutOrNull(doubleTapMs) { awaitFirstDown(requireUnconsumed = true) }
                    if (second == null) {
                        tap()
                    } else {
                        second.consume()
                        val secondZone = PlaybackRules.zoneOf(second.position.x, size.width.toFloat())
                        // Espera a que se levante el segundo toque para no disparar otros gestos.
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        doubleTap(secondZone)
                    }
                }
            }
        }
    }
}
