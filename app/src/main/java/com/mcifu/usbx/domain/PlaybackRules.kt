package com.mcifu.usbx.domain

import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileType

/** Qué hace el reproductor al terminar un vídeo. */
enum class LoopMode {
    /** Vuelve al principio del mismo vídeo (predeterminado). */
    REPEAT_ONE,
    /** Se detiene al final. */
    PLAY_ONCE,
    /** Pasa al siguiente vídeo o audio de la carpeta y, tras el último, vuelve al primero. */
    REPEAT_FOLDER,
}

/** Zona horizontal de la pantalla del reproductor para los gestos. */
enum class GestureZone { LEFT, CENTER, RIGHT }

/**
 * Reglas del reproductor sin dependencias de Android (probadas con tests unitarios).
 */
object PlaybackRules {

    val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    const val DEFAULT_SPEED = 1f
    const val DOUBLE_TAP_SEEK_MS = 10_000L

    /** Izquierda y derecha ocupan un 35 % cada una: fáciles de acertar con el pulgar. */
    fun zoneOf(x: Float, width: Float): GestureZone = when {
        width <= 0f -> GestureZone.CENTER
        x < width * 0.35f -> GestureZone.LEFT
        x > width * 0.65f -> GestureZone.RIGHT
        else -> GestureZone.CENTER
    }

    /** Velocidades del avance/retroceso rápido mientras se mantiene el dedo. */
    val FAST_SEEK_RATES = listOf(2, 4, 8, 16)

    /** Tiempo que se mantiene cada velocidad antes de pasar a la siguiente. */
    const val FAST_SEEK_STAGE_MS = 1_500L

    /** Velocidad según el tiempo que lleva pulsado: 2× → 4× → 8× → 16× (y se queda en 16×). */
    fun fastSeekRate(heldMs: Long): Int {
        val stage = (heldMs.coerceAtLeast(0) / FAST_SEEK_STAGE_MS).toInt()
        return FAST_SEEK_RATES[stage.coerceAtMost(FAST_SEEK_RATES.lastIndex)]
    }

    /**
     * Siguiente posición del avance/retroceso por saltos: avanza `rate` segundos de vídeo por
     * cada segundo real, sin salirse de [0, duración].
     */
    fun fastSeekStep(positionMs: Long, rate: Int, forward: Boolean, elapsedMs: Long, durationMs: Long): Long {
        val delta = rate * elapsedMs
        val next = if (forward) positionMs + delta else positionMs - delta
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return next.coerceIn(0L, max)
    }

    fun doubleTapTarget(positionMs: Long, zone: GestureZone, durationMs: Long): Long? {
        val target = when (zone) {
            GestureZone.LEFT -> positionMs - DOUBLE_TAP_SEEK_MS
            GestureZone.RIGHT -> positionMs + DOUBLE_TAP_SEEK_MS
            GestureZone.CENTER -> return null
        }
        return target.coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
    }

    /** Posición (ms) para una fracción de la barra de progreso. */
    fun positionForFraction(fraction: Float, durationMs: Long): Long =
        if (durationMs <= 0) 0L else (fraction.coerceIn(0f, 1f) * durationMs).toLong()

    fun isPlayable(item: FileItem): Boolean = item.type == FileType.VIDEO || item.type == FileType.AUDIO

    /**
     * Índice del siguiente elemento reproducible después de [currentIndex] (con vuelta al
     * principio), o `null` si no hay ninguno más que el actual.
     */
    fun nextPlayableIndex(items: List<FileItem>, currentIndex: Int): Int? {
        if (items.isEmpty()) return null
        for (offset in 1..items.size) {
            val index = (currentIndex + offset) % items.size
            if (index != currentIndex && isPlayable(items[index])) return index
        }
        return null
    }

    /**
     * Al volver a un vídeo se retoma donde se dejó, salvo que estuviera prácticamente al final
     * (entonces empieza de nuevo).
     */
    fun resumePosition(savedMs: Long?, durationMs: Long): Long {
        if (savedMs == null || savedMs < 3_000) return 0L
        if (durationMs > 0 && savedMs > durationMs - 5_000) return 0L
        return savedMs
    }
}
