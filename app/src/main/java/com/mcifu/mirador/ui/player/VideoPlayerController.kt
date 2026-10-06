package com.mcifu.mirador.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.mcifu.mirador.data.diagnostics.Diagnostics
import com.mcifu.mirador.data.storage.contentUri
import com.mcifu.mirador.domain.LoopMode
import com.mcifu.mirador.domain.PlaybackRules
import com.mcifu.mirador.domain.model.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Avance o retroceso rápido en curso (gesto de mantener pulsado). */
data class FastSeek(val forward: Boolean, val rate: Int)

/** Estado observable de un reproductor. Uno por panel: el multiview (Fase 4) usará dos. */
data class PlayerState(
    val item: FileItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasEnded: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = PlaybackRules.DEFAULT_SPEED,
    val volume: Float = 1f,
    val loopMode: LoopMode = LoopMode.REPEAT_ONE,
    val fastSeek: FastSeek? = null,
    val error: PlayerError? = null,
    /** Detalle técnico del error (código de Media3 y causa), visible en pantalla. */
    val errorDetail: String? = null,
)

enum class PlayerError { UNSUPPORTED_FORMAT, READ_ERROR, OTHER }

/**
 * Envuelve un [ExoPlayer] y traduce su estado a [PlayerState].
 *
 * - El `ExoPlayer` se crea al reproducir el primer elemento (abrir fotos no cuesta nada).
 * - Al cambiar de vídeo se recuerda la posición del anterior para retomarlo después.
 * - Barra de progreso: mientras se arrastra se busca al fotograma clave más cercano (rápido,
 *   la propia imagen del vídeo sirve de vista previa); al soltar, búsqueda exacta.
 * - Avance rápido 2×: velocidad real (con audio). 4×, 8×, 16× y todo el retroceso: saltos
 *   periódicos entre fotogramas clave, porque ExoPlayer no reproduce hacia atrás y a 16× los
 *   decodificadores no dan abasto.
 *
 * Todo debe llamarse desde el hilo principal (requisito de ExoPlayer).
 */
@OptIn(UnstableApi::class)
class VideoPlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    /**
     * Solo un reproductor por pantalla debe gestionar el foco de audio: si dos lo piden,
     * Android pausa el primero. En el multiview el segundo panel lo lleva desactivado.
     */
    private val handleAudioFocus: Boolean = true,
    initialVolume: Float = 1f,
) {
    private var _player: ExoPlayer? = null

    /** `null` hasta que se reproduce algo. */
    val player: Player? get() = _player

    private val _state = MutableStateFlow(PlayerState(volume = initialVolume))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Se invoca al terminar un elemento con [LoopMode.REPEAT_FOLDER]. */
    var onEndedInFolderMode: (() -> Unit)? = null

    private val savedPositions = HashMap<String, Long>()
    private var ticker: Job? = null
    private var fastSeekJob: Job? = null
    private var wasPlayingBeforeGesture = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && _state.value.loopMode == LoopMode.REPEAT_FOLDER) {
                onEndedInFolderMode?.invoke()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Diagnostics.log(
                "REPRODUCTOR",
                "Error ${error.errorCodeName} en ${_state.value.item?.name} (${_state.value.item?.mimeType})",
                error.cause ?: error,
            )
            val cause = error.cause
            _state.update {
                it.copy(
                    error = error.toPlayerError(),
                    errorDetail = error.errorCodeName + (cause?.let { c -> " · ${c.javaClass.simpleName}: ${c.message}" }.orEmpty()),
                    isPlaying = false,
                    isBuffering = false,
                )
            }
        }
    }

    private fun requirePlayer(): ExoPlayer = _player ?: ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            handleAudioFocus,
        )
        .setHandleAudioBecomingNoisy(true) // pausa al desconectar auriculares
        // Archivo local: basta con 250 ms en búfer para empezar (por defecto, ExoPlayer espera 1 s
        // pensando en streaming), así el primer fotograma aparece antes al abrir el vídeo.
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 250, 1_000)
                .build(),
        )
        .build()
        .also { player ->
            player.addListener(listener)
            player.repeatMode = repeatModeFor(_state.value.loopMode)
            player.volume = _state.value.volume
            _player = player
            startTicker()
        }

    /** Carga y reproduce [item]; si ya es el actual no hace nada. `null` detiene la reproducción. */
    fun setItem(item: FileItem?, playWhenReady: Boolean = true) {
        val current = _state.value.item
        if (item?.documentId == current?.documentId && _state.value.error == null) return
        stopFastSeek()
        rememberPosition()
        if (item == null) {
            _player?.stop()
            _player?.clearMediaItems()
            _state.update { it.copy(item = null, isPlaying = false, positionMs = 0, durationMs = 0, error = null, hasEnded = false) }
            return
        }
        Diagnostics.log("REPRODUCTOR", "Cargar ${item.name} (${item.mimeType}, ${item.size} bytes)")
        val player = requirePlayer()
        player.setSeekParameters(SeekParameters.DEFAULT)
        player.setMediaItem(MediaItem.fromUri(item.contentUri))
        player.setPlaybackSpeed(_state.value.speed)
        player.prepare()
        val resume = savedPositions[item.documentId] ?: 0L
        if (resume > 0) player.seekTo(resume)
        player.playWhenReady = playWhenReady
        _state.update { it.copy(item = item, error = null, hasEnded = false, positionMs = resume, durationMs = 0) }
    }

    fun retry() {
        val item = _state.value.item ?: return
        _state.update { it.copy(error = null, item = null) }
        setItem(item)
    }

    fun togglePlayPause() {
        val player = _player ?: return
        when {
            player.playbackState == Player.STATE_ENDED -> { player.seekTo(0); player.play() }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
    }

    fun pause() {
        _player?.pause()
    }

    fun play() {
        val player = _player ?: return
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    /** Posición exacta en este instante (el estado se publica cada 250 ms). */
    fun currentPositionMs(): Long = _player?.currentPosition ?: _state.value.positionMs

    val isPlayingNow: Boolean get() = _player?.isPlaying == true

    fun seekTo(positionMs: Long) {
        val player = _player ?: return
        player.seekTo(positionMs.coerceAtLeast(0))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0), hasEnded = false) }
    }

    // --- Barra de progreso ----------------------------------------------------------------

    fun beginScrub() {
        val player = _player ?: return
        stopFastSeek()
        wasPlayingBeforeGesture = player.isPlaying
        player.pause()
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }

    fun scrubTo(positionMs: Long) {
        _player?.seekTo(positionMs)
    }

    fun endScrub(positionMs: Long) {
        val player = _player ?: return
        player.setSeekParameters(SeekParameters.EXACT) // búsqueda final precisa al fotograma
        seekTo(positionMs)
        if (wasPlayingBeforeGesture) player.play()
    }

    // --- Avance / retroceso rápido -------------------------------------------------------

    fun startFastSeek(forward: Boolean) {
        val player = _player ?: return
        if (fastSeekJob != null) return
        wasPlayingBeforeGesture = player.isPlaying || player.playWhenReady
        val normalSpeed = _state.value.speed
        fastSeekJob = scope.launch {
            val start = System.currentTimeMillis()
            var virtualPosition = player.currentPosition
            var last = start
            while (isActive) {
                val now = System.currentTimeMillis()
                val rate = PlaybackRules.fastSeekRate(now - start)
                _state.update { it.copy(fastSeek = FastSeek(forward, rate)) }
                if (forward && rate == 2) {
                    // 2×: reproducción real acelerada, fluida y con sonido.
                    player.setPlaybackSpeed(2f)
                    player.play()
                    virtualPosition = player.currentPosition
                } else {
                    if (player.isPlaying) player.pause()
                    player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    virtualPosition = PlaybackRules.fastSeekStep(virtualPosition, rate, forward, now - last, player.duration)
                    player.seekTo(virtualPosition)
                    _state.update { it.copy(positionMs = virtualPosition) }
                }
                last = now
                delay(FAST_SEEK_TICK_MS)
            }
        }.also { job ->
            job.invokeOnCompletion {
                player.setPlaybackSpeed(normalSpeed)
                player.setSeekParameters(SeekParameters.DEFAULT)
                if (wasPlayingBeforeGesture) player.play() else player.pause()
                _state.update { it.copy(fastSeek = null) }
            }
        }
    }

    fun stopFastSeek() {
        fastSeekJob?.cancel()
        fastSeekJob = null
    }

    // --- Ajustes -------------------------------------------------------------------------

    fun setSpeed(speed: Float) {
        _player?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setVolume(volume: Float) {
        val value = volume.coerceIn(0f, 1f)
        _player?.volume = value
        _state.update { it.copy(volume = value) }
    }

    fun setLoopMode(mode: LoopMode) {
        _player?.repeatMode = repeatModeFor(mode)
        _state.update { it.copy(loopMode = mode) }
    }

    fun release() {
        stopFastSeek()
        ticker?.cancel()
        _player?.removeListener(listener)
        _player?.release()
        _player = null
    }

    // --- Interno -------------------------------------------------------------------------

    private fun rememberPosition() {
        val player = _player ?: return
        val item = _state.value.item ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        savedPositions[item.documentId] = PlaybackRules.resumePosition(player.currentPosition, duration)
    }

    private fun publish() {
        val player = _player ?: return
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                hasEnded = player.playbackState == Player.STATE_ENDED,
                positionMs = if (it.fastSeek != null && !(it.fastSeek.forward && it.fastSeek.rate == 2)) it.positionMs
                else player.currentPosition,
                durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0,
                bufferedMs = player.bufferedPosition,
            )
        }
    }

    /** Actualiza la posición mientras se reproduce (la barra avanza de forma continua). */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                if (_player?.isPlaying == true) publish()
                delay(if (_state.value.fastSeek != null) 100 else 250)
            }
        }
    }

    private fun repeatModeFor(mode: LoopMode) =
        if (mode == LoopMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

    private fun PlaybackException.toPlayerError(): PlayerError = when (errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> PlayerError.UNSUPPORTED_FORMAT
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> PlayerError.READ_ERROR
        else -> PlayerError.OTHER
    }

    private companion object {
        const val FAST_SEEK_TICK_MS = 150L
    }
}
