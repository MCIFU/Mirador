package com.mcifu.usbx.ui.player

import com.mcifu.usbx.domain.MultiviewRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sincroniza dos reproductores (multiview).
 *
 * Al activarla se guarda el desfase actual entre B y A: así puedes alinear dos tomas a mano
 * (p. ej. B empieza 2,3 s después) y después enlazarlas. Con la sincronización activa, play,
 * pausa, búsquedas y velocidad se aplican a los dos, y cada 2 s se corrige la deriva si supera
 * 250 ms.
 */
class PlaybackSync(
    private val a: VideoPlayerController,
    private val b: VideoPlayerController,
    private val scope: CoroutineScope,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _offsetMs = MutableStateFlow(0L)
    /** Desfase de B respecto a A (positivo: B va por delante). */
    val offsetMs: StateFlow<Long> = _offsetMs.asStateFlow()

    private var driftJob: Job? = null

    fun setEnabled(value: Boolean) {
        if (value == _enabled.value) return
        if (!value) {
            driftJob?.cancel()
            _enabled.value = false
            return
        }
        _offsetMs.value = b.currentPositionMs() - a.currentPositionMs()
        b.setSpeed(a.state.value.speed)
        if (a.isPlayingNow) b.play() else b.pause()
        _enabled.value = true
        driftJob = scope.launch {
            while (isActive) {
                delay(DRIFT_CHECK_MS)
                correctDrift()
            }
        }
    }

    /** Reinicia el desfase a cero (ambos al mismo tiempo relativo). */
    fun alignToZero() {
        _offsetMs.value = 0
        seekBoth(a.currentPositionMs(), from = a)
    }

    fun togglePlayPause(from: VideoPlayerController) {
        val playing = from.isPlayingNow
        if (!_enabled.value) {
            from.togglePlayPause()
            return
        }
        if (playing) { a.pause(); b.pause() } else { a.play(); b.play() }
    }

    fun seek(from: VideoPlayerController, positionMs: Long) {
        if (!_enabled.value) from.seekTo(positionMs) else seekBoth(positionMs, from)
    }

    fun beginScrub(from: VideoPlayerController) {
        from.beginScrub()
        if (_enabled.value) other(from).beginScrub()
    }

    fun scrub(from: VideoPlayerController, positionMs: Long) {
        from.scrubTo(positionMs)
        if (_enabled.value) other(from).scrubTo(counterpart(from, positionMs))
    }

    fun endScrub(from: VideoPlayerController, positionMs: Long) {
        from.endScrub(positionMs)
        if (_enabled.value) other(from).endScrub(counterpart(from, positionMs))
    }

    fun setSpeed(from: VideoPlayerController, speed: Float) {
        from.setSpeed(speed)
        if (_enabled.value) other(from).setSpeed(speed)
    }

    private fun seekBoth(positionMs: Long, from: VideoPlayerController) {
        from.seekTo(positionMs)
        other(from).seekTo(counterpart(from, positionMs))
    }

    private fun other(c: VideoPlayerController) = if (c === a) b else a

    /** Posición equivalente en el otro reproductor según el desfase. */
    private fun counterpart(from: VideoPlayerController, positionMs: Long): Long =
        if (from === a) MultiviewRules.syncedPosition(positionMs, _offsetMs.value, b.state.value.durationMs)
        else (positionMs - _offsetMs.value).coerceAtLeast(0)

    private fun correctDrift() {
        if (!a.isPlayingNow) return
        val posA = a.currentPositionMs()
        val posB = b.currentPositionMs()
        val durationB = b.state.value.durationMs
        if (MultiviewRules.needsResync(posA, posB, _offsetMs.value, durationB)) {
            b.seekTo(MultiviewRules.syncedPosition(posA, _offsetMs.value, durationB))
            if (!b.isPlayingNow) b.play()
        }
    }

    fun release() {
        driftJob?.cancel()
    }

    private companion object {
        const val DRIFT_CHECK_MS = 2_000L
    }
}
