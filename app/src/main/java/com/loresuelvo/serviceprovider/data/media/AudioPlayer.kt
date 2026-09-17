package com.loresuelvo.serviceprovider.data.media

import kotlinx.coroutines.flow.StateFlow

/**
 * Port for playback of an audio clip rendered inside a chat
 * bubble. The implementation owns the underlying
 * `MediaPlayer` and exposes its playback state as observable
 * flows so the chat surface can drive the play / pause UI
 * without leaking Android types.
 */
interface AudioPlayer {

    /** `true` while the underlying `MediaPlayer` is playing. */
    val isPlaying: StateFlow<Boolean>

    /**
     * Live position in milliseconds since the start of the
     * currently loaded clip. Resets to `0L` on [stop] or when a
     * new clip is loaded via [play].
     */
    val currentPositionMillis: StateFlow<Long>

    /**
     * Loads and plays the audio clip at [url] from
     * [startPositionMillis]. Calling [play] while a clip is
     * already loaded swaps the source — the chat surface keeps a
     * single [AudioPlayer] for the whole conversation surface and
     * plays one bubble at a time.
     */
    fun play(
        url: String,
        startPositionMillis: Long = 0L,
    )

    /** Pauses the current playback; the position is preserved. */
    fun pause()

    /**
     * Releases the underlying `MediaPlayer` and resets the
     * position to `0L`. Safe to call when no clip is loaded
     * (no-op).
     */
    fun stop()
}
