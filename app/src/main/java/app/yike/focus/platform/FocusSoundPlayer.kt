package app.yike.focus.platform

import android.content.Context
import android.media.*
import app.yike.focus.R
import app.yike.focus.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** A single player coordinates previews and focus playback, so they never overlap. */
class FocusSoundPlayer(private val context: Context, private val scope: CoroutineScope) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var current = Ambient.OFF
    private var running = false
    private var settings = FocusSettings()
    private var transition: Job? = null
    private var previewJob: Job? = null
    private var granted = false
    private var ducked = false
    private var generation = 0
    private var prepared = false
    val preview = MutableStateFlow(Ambient.OFF)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> { granted = true; ducked = false; if (prepared) { player?.setVolume(volume(), volume()); player?.start() } }
                AudioManager.AUDIOFOCUS_LOSS -> { granted = false; if (prepared) player?.pause(); previewJob?.cancel(); preview.value = Ambient.OFF }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { granted = false; if (prepared) player?.pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { ducked = true; if (prepared) player?.setVolume(volume(), volume()) }
            }
        }.build()
    private fun volume(): Float = settings.ambientVolume * if (ducked) 0.15f else 1f
    fun update(focusRunning: Boolean, config: FocusSettings) {
        if (running && !focusRunning) { previewJob?.cancel(); preview.value = Ambient.OFF }
        running = focusRunning; settings = config; refresh()
    }
    fun preview(source: Ambient) {
        if (source == Ambient.OFF) return
        if (preview.value == source) { cancelPreview(); return }
        previewJob?.cancel(); preview.value = source; refresh()
        previewJob = scope.launch { delay(12_000); preview.value = Ambient.OFF; refresh() }
    }
    fun cancelPreview() { previewJob?.cancel(); preview.value = Ambient.OFF; refresh() }
    private fun refresh() {
        val desired = if (preview.value != Ambient.OFF) preview.value else if (running) settings.ambient else Ambient.OFF
        if (desired == current) { if (granted && prepared && transition?.isActive != true) player?.setVolume(volume(), volume()); return }
        current = desired; generation++; val token = generation
        transition?.cancel()
        // The outgoing decoder is stopped before the next is created.
        val old = player; val wasPrepared = prepared; player = null; prepared = false
        transition = scope.launch {
            try { if (old != null && wasPrepared) { repeat(8) { old.setVolume(volume() * (7 - it) / 8, volume() * (7 - it) / 8); delay(25) } } }
            finally { old?.release() }
            if (token != generation) return@launch
            if (desired == Ambient.OFF) { manager.abandonAudioFocusRequest(request); granted = false; return@launch }
            granted = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            val resource = when (desired) {
                Ambient.RAIN -> R.raw.rain; Ambient.STREAM -> R.raw.stream; Ambient.WAVES -> R.raw.waves
                Ambient.FIRE -> R.raw.fire; Ambient.WHITE -> R.raw.white_noise; else -> R.raw.brown_noise
            }
            try {
                val next = MediaPlayer(); player = next
                next.setAudioAttributes(attributes)
                context.resources.openRawResourceFd(resource).use { next.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                next.isLooping = true; next.setVolume(0f, 0f)
                next.setOnErrorListener { failed, _, _ -> if (player === failed) { player = null; current = Ambient.OFF }; failed.release(); true }
                next.setOnPreparedListener { ready -> if (token == generation && player === ready) {
                    prepared = true
                    if (granted) { ready.start(); transition = scope.launch { repeat(16) { if (player !== ready || !granted) return@launch
                        ready.setVolume(volume() * (it + 1) / 16, volume() * (it + 1) / 16); delay(25) } }
                    }
                } }
                next.prepareAsync()
            } catch (_: Exception) { player?.release(); player = null; current = Ambient.OFF; manager.abandonAudioFocusRequest(request); granted = false }
        }
    }
}
