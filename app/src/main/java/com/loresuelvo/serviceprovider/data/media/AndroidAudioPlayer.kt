package com.loresuelvo.serviceprovider.data.media

import com.loresuelvo.serviceprovider.domain.conversation.AudioPlayer
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
open class AndroidAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioPlayer {

    private var player: MediaPlayer? = null
    private var playWhenPrepared = false
    private var prepared = false
    private var requestedPosition = 0L
    private var loadedUrl: String? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMillis = MutableStateFlow(0L)
    override val currentPositionMillis: StateFlow<Long> = _currentPositionMillis.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val positionPollRunnable = object : Runnable {
        override fun run() {
            val current = player
            if (current != null) runCatching {
                if (current.isPlaying) {
                    _currentPositionMillis.value = safePosition(current)
                    handler.postDelayed(this, 250L)
                }
            }.onFailure { stop() }
        }
    }

    protected open fun createPlayer(): MediaPlayer = MediaPlayer()

    override fun play(url: String, startPositionMillis: Long) {
        val current = player
        if (current != null && loadedUrl == url && prepared) {
            runCatching {
                seekTo(startPositionMillis)
                current.start()
                playWhenPrepared = true
                _isPlaying.value = true
                handler.post(positionPollRunnable)
            }.onFailure { stop() }
            return
        }
        stop()
        loadedUrl = url
        val mediaPlayer = runCatching { createPlayer() }.getOrNull() ?: return
        player = mediaPlayer
        playWhenPrepared = true
        requestedPosition = startPositionMillis.coerceAtLeast(0L)
        runCatching {
            mediaPlayer.setDataSource(context, android.net.Uri.parse(url))
            mediaPlayer.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                prepared = true
                runCatching {
                    if (requestedPosition > 0) it.seekTo(requestedPosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    if (playWhenPrepared) {
                        it.start()
                        _isPlaying.value = true
                        handler.post(positionPollRunnable)
                    }
                }.onFailure { stop() }
            }
            mediaPlayer.setOnCompletionListener { if (player === it) stop() }
            mediaPlayer.setOnErrorListener { current, _, _ ->
                if (player === current) stop()
                true
            }
            mediaPlayer.prepareAsync()
        }.onFailure { stop() }
    }

    override fun seekTo(positionMillis: Long) {
        val current = player ?: return
        requestedPosition = positionMillis.coerceAtLeast(0L)
        if (prepared) runCatching {
            requestedPosition = requestedPosition.coerceAtMost(current.duration.toLong().coerceAtLeast(0L))
            current.seekTo(requestedPosition.toInt())
        }.onFailure { stop() }
        _currentPositionMillis.value = requestedPosition
    }

    override fun pause() {
        playWhenPrepared = false
        val current = player ?: return
        if (runCatching { current.isPlaying }.getOrDefault(false)) {
            runCatching {
                current.pause()
                handler.removeCallbacks(positionPollRunnable)
                _isPlaying.value = false
                _currentPositionMillis.value = safePosition(current)
            }.onFailure { stop() }
        }
    }

    override fun stop() {
        playWhenPrepared = false
        releaseCurrentPlayer()
        _isPlaying.value = false
        _currentPositionMillis.value = 0L
    }

    private fun releaseCurrentPlayer() {
        handler.removeCallbacks(positionPollRunnable)
        player?.runCatching { stop() }
        player?.runCatching { release() }
        player = null
        prepared = false
        loadedUrl = null
    }

    private fun safePosition(player: MediaPlayer): Long = runCatching {
        player.currentPosition.toLong()
    }.getOrDefault(0L)
}
