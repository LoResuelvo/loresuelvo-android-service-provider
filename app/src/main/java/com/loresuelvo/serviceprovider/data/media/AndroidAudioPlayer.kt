package com.loresuelvo.serviceprovider.data.media

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [AudioPlayer]. Owns a single
 * `MediaPlayer` instance — the chat surface keeps one
 * [AudioPlayer] for the whole conversation and plays one
 * bubble at a time, so the "swap" semantic in [play] is
 * release-the-current-then-load-the-new rather than
 * parallel-players.
 *
 * Position polling uses a `Handler` on the main looper at 250 ms
 * intervals — fine for the `mm:ss` granularity the bubble shows.
 */
@Singleton
class AndroidAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioPlayer {

    private var player: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMillis = MutableStateFlow(0L)
    override val currentPositionMillis: StateFlow<Long> = _currentPositionMillis.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val positionPollRunnable = object : Runnable {
        override fun run() {
            val current = player
            if (current != null && current.isPlaying) {
                _currentPositionMillis.value = safePosition(current)
                handler.postDelayed(this, 250L)
            }
        }
    }

    override fun play(url: String, startPositionMillis: Long) {
        releaseCurrentPlayer()
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener {
                if (startPositionMillis > 0) seekTo(startPositionMillis.toInt())
                start()
                _isPlaying.value = true
                handler.post(positionPollRunnable)
            }
            setOnCompletionListener {
                _isPlaying.value = false
                _currentPositionMillis.value = 0L
            }
            setOnErrorListener { _, _, _ ->
                _isPlaying.value = false
                true
            }
            prepareAsync()
        }
        player = mediaPlayer
    }

    override fun pause() {
        val current = player ?: return
        if (current.isPlaying) {
            current.pause()
            handler.removeCallbacks(positionPollRunnable)
            _isPlaying.value = false
            _currentPositionMillis.value = safePosition(current)
        }
    }

    override fun stop() {
        releaseCurrentPlayer()
        _isPlaying.value = false
        _currentPositionMillis.value = 0L
    }

    private fun releaseCurrentPlayer() {
        handler.removeCallbacks(positionPollRunnable)
        player?.runCatching { stop() }
        player?.runCatching { release() }
        player = null
    }

    private fun safePosition(player: MediaPlayer): Long = runCatching {
        player.currentPosition.toLong()
    }.getOrDefault(0L)
}
