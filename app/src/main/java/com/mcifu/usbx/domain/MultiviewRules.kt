package com.mcifu.usbx.domain

import kotlin.math.abs

/** Transformación de zoom de un panel: escala y desplazamiento en píxeles desde el centro. */
data class ZoomTransform(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {
    val isZoomed: Boolean get() = scale > 1.01f
}

/**
 * Reglas del multiview, puras y probadas con tests.
 */
object MultiviewRules {

    const val MIN_SCALE = 1f
    const val MAX_SCALE = 6f
    const val DOUBLE_TAP_SCALE = 2.5f

    /** Desviación máxima tolerada entre dos vídeos sincronizados antes de corregir. */
    const val SYNC_TOLERANCE_MS = 250L

    /**
     * Aplica un gesto de pellizco/arrastre. [focusX]/[focusY] son el punto del gesto respecto al
     * centro del panel: ese punto de la imagen queda bajo los dedos mientras se amplía.
     */
    fun applyGesture(
        current: ZoomTransform,
        zoom: Float,
        panX: Float,
        panY: Float,
        focusX: Float,
        focusY: Float,
        width: Float,
        height: Float,
    ): ZoomTransform {
        val newScale = (current.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val factor = newScale / current.scale
        val x = focusX - (focusX - current.offsetX) * factor + panX
        val y = focusY - (focusY - current.offsetY) * factor + panY
        return clamp(ZoomTransform(newScale, x, y), width, height)
    }

    /** Doble toque: amplía alrededor del punto o, si ya está ampliado, vuelve a encajar. */
    fun doubleTap(current: ZoomTransform, focusX: Float, focusY: Float, width: Float, height: Float): ZoomTransform =
        if (current.isZoomed) ZoomTransform()
        else applyGesture(current, DOUBLE_TAP_SCALE / current.scale, 0f, 0f, focusX, focusY, width, height)

    /** Impide que la imagen se aleje de los bordes del panel. */
    fun clamp(t: ZoomTransform, width: Float, height: Float): ZoomTransform {
        val maxX = (t.scale - 1f) * width / 2f
        val maxY = (t.scale - 1f) * height / 2f
        return t.copy(offsetX = t.offsetX.coerceIn(-maxX, maxX), offsetY = t.offsetY.coerceIn(-maxY, maxY))
    }

    /** Posición que debe tener el vídeo B para seguir a A manteniendo el desfase elegido. */
    fun syncedPosition(positionA: Long, offsetMs: Long, durationB: Long): Long {
        val target = positionA + offsetMs
        return if (durationB > 0) target.coerceIn(0L, durationB) else target.coerceAtLeast(0L)
    }

    fun needsResync(positionA: Long, positionB: Long, offsetMs: Long, durationB: Long): Boolean {
        val expected = syncedPosition(positionA, offsetMs, durationB)
        // Si B ya terminó (A va por delante de su duración) no hay nada que corregir.
        if (durationB > 0 && positionA + offsetMs >= durationB && positionB >= durationB - SYNC_TOLERANCE_MS) return false
        return abs(positionB - expected) > SYNC_TOLERANCE_MS
    }
}
