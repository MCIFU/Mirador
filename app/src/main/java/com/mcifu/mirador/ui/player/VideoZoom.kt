package com.mcifu.mirador.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import com.mcifu.mirador.domain.MultiviewRules
import com.mcifu.mirador.domain.ZoomTransform
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Zoom del vídeo: pellizcar para ampliar (hasta 6×) o reducir, arrastrar para moverse por la imagen
 * ampliada. Al soltar por debajo de 1,05× vuelve a encajar con una animación de muelle.
 *
 * La transformación solo se lee en la capa gráfica ([videoZoomLayer]): durante el gesto no se
 * recompone nada, solo se redibuja la capa del vídeo.
 */
@Stable
class VideoZoomState {
    var transform by mutableStateOf(ZoomTransform())
        private set

    val isZoomed: Boolean by derivedStateOf { transform.isZoomed }

    /** Nivel de zoom redondeado a una décima, para la etiqueta (cambia pocas veces por gesto). */
    val label: String by derivedStateOf { "%.1f×".format(transform.scale).replace('.', ',') }

    internal var size: IntSize = IntSize.Zero

    /** Animación de vuelta a 1× en curso; un gesto nuevo la interrumpe en el acto. */
    private var animation: Job? = null

    /** Se aplica en el mismo evento táctil, sin esperar a otro fotograma. */
    internal fun gesture(zoom: Float, pan: Offset, centroid: Offset) {
        animation?.cancel()
        animation = null
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        transform = MultiviewRules.applyGesture(
            transform, zoom, pan.x, pan.y, centroid.x - w / 2f, centroid.y - h / 2f, w, h,
            minScale = RUBBER_BAND_MIN_SCALE,
        )
    }

    /** Tras soltar: casi sin zoom (o estirado por debajo de 1) → vuelve a 1× con animación. */
    internal suspend fun settle() {
        if (transform.scale < SNAP_BACK_SCALE) reset()
    }

    suspend fun reset(animated: Boolean = true) {
        animation?.cancel()
        val from = transform
        if (from == ZoomTransform()) return
        if (!animated) {
            transform = ZoomTransform()
            return
        }
        coroutineScope {
            animation = coroutineContext[Job]
            animate(0f, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { t, _ ->
                transform = ZoomTransform(
                    scale = from.scale + (1f - from.scale) * t,
                    offsetX = from.offsetX * (1f - t),
                    offsetY = from.offsetY * (1f - t),
                )
            }
        }
    }

    private companion object {
        const val RUBBER_BAND_MIN_SCALE = 0.8f
        const val SNAP_BACK_SCALE = 1.05f
    }
}

@Composable
fun rememberVideoZoomState(key: Any?): VideoZoomState = remember(key) { VideoZoomState() }

/** Aplica el zoom a la imagen del vídeo (o a su portada) sin recomponer. */
fun Modifier.videoZoomLayer(state: VideoZoomState): Modifier = graphicsLayer {
    val t = state.transform
    scaleX = t.scale
    scaleY = t.scale
    translationX = t.offsetX
    translationY = t.offsetY
}

/**
 * Gestos de zoom. Debe ir antes (más externo) que [playerGestures] en la cadena de modificadores:
 * así los toques, dobles toques y pulsaciones largas siguen siendo del reproductor, y aquí solo se
 * atienden los gestos de dos dedos y, con el vídeo ampliado, el arrastre de un dedo. Al consumirlos,
 * el carrusel no cambia de vídeo mientras se amplía o se mueve la imagen.
 */
@Composable
fun Modifier.videoZoomGestures(state: VideoZoomState, enabled: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    if (!enabled) return this
    return pointerInput(state) {
        state.size = size
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var transforming = false
            var dragged = Offset.Zero
            do {
                val event = awaitPointerEvent()
                // Otro gesto del reproductor (pulsación larga, doble toque) ya es dueño del toque.
                if (!transforming && event.changes.any { it.isConsumed }) break
                val fingers = event.changes.count { it.pressed }
                if (!transforming) {
                    if (fingers >= 2) {
                        transforming = true
                    } else if (state.isZoomed) {
                        dragged += event.calculatePan()
                        if (dragged.getDistance() > slop) transforming = true
                    }
                }
                if (transforming) {
                    state.size = size
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (centroid != Offset.Unspecified) state.gesture(zoom, pan, centroid)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (transforming) scope.launch { state.settle() }
        }
    }
}
