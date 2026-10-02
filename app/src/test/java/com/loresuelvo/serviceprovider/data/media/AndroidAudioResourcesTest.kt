package com.loresuelvo.serviceprovider.data.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAudioResourcesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun recorder_partial_start_stop_failure_cancel_and_success_own_the_file() {
        for (stage in listOf("prepare", "start", "permission", "stop", "cancel", "success")) {
            val native = mockk<MediaRecorder>(relaxed = true)
            var path = ""
            every { native.setOutputFile(any<String>()) } answers {
                path = firstArg(); java.io.File(path).writeBytes(byteArrayOf(1))
            }
            if (stage == "prepare") every { native.prepare() } throws java.io.IOException("prepare")
            if (stage == "permission") every { native.start() } throws SecurityException("revoked")
            if (stage == "start") every { native.start() } throws IllegalStateException("start")
            if (stage == "stop") every { native.stop() } throws IllegalStateException("short")
            val recorder = object : AndroidAudioRecorder(context) {
                override fun createRecorder() = native
            }
            val started = recorder.start()
            if (stage in listOf("prepare", "start", "permission")) assertTrue(started.isFailure) else {
                assertTrue(started.isSuccess)
                if (stage == "cancel") recorder.cancel() else {
                    val stopped = recorder.stop()
                    assertEquals(stage == "success", stopped.isSuccess)
                    if (stage == "success") {
                        assertTrue(java.io.File(path).exists())
                        recorder.discard(stopped.getOrThrow())
                    }
                }
            }
            verify(exactly = 1) { native.release() }
            assertFalse(java.io.File(path).exists())
            recorder.cancel()
            verify(exactly = 1) { native.release() }
        }
    }

    @Test
    fun discard_does_not_delete_an_unowned_file() {
        val unrelated = java.io.File(context.cacheDir, "unrelated.webm").apply { writeBytes(byteArrayOf(1)) }
        try {
            AndroidAudioRecorder(context).discard(android.net.Uri.fromFile(unrelated).toString())
            assertTrue(unrelated.exists())
        } finally { unrelated.delete() }
    }

    @Test
    fun player_preparation_pause_replacement_stale_callbacks_completion_and_error_release() {
        val first = mockk<MediaPlayer>(relaxed = true)
        val second = mockk<MediaPlayer>(relaxed = true)
        val prepared = slot<MediaPlayer.OnPreparedListener>()
        val completed = slot<MediaPlayer.OnCompletionListener>()
        val failed = slot<MediaPlayer.OnErrorListener>()
        every { first.setOnPreparedListener(capture(prepared)) } just Runs
        every { first.setOnCompletionListener(capture(completed)) } just Runs
        every { first.setOnErrorListener(capture(failed)) } just Runs
        val players = ArrayDeque(listOf(first, second))
        val player = object : AndroidAudioPlayer(context) { override fun createPlayer() = players.removeFirst() }
        player.play("https://example.test/one")
        player.pause()
        prepared.captured.onPrepared(first)
        verify(exactly = 0) { first.start() }
        player.play("https://example.test/two", 2000)
        prepared.captured.onPrepared(first)
        completed.captured.onCompletion(first)
        failed.captured.onError(first, 1, 1)
        verify(exactly = 1) { first.release() }
        verify(exactly = 0) { second.release() }
        player.stop()
        verify(exactly = 1) { second.release() }
        assertFalse(player.isPlaying.value)
        assertEquals(0L, player.currentPositionMillis.value)
    }

    @Test
    fun seeking_a_prepared_paused_clip_does_not_prepare_or_start_again_and_resume_reuses_player() {
        val native = mockk<MediaPlayer>(relaxed = true)
        val prepared = slot<MediaPlayer.OnPreparedListener>()
        every { native.setOnPreparedListener(capture(prepared)) } just Runs
        every { native.duration } returns 5000
        every { native.isPlaying } returns true
        val player = object : AndroidAudioPlayer(context) { override fun createPlayer() = native }
        player.play("https://example.test/audio")
        prepared.captured.onPrepared(native)
        player.pause()
        player.seekTo(2000)
        assertFalse(player.isPlaying.value)
        assertEquals(2000L, player.currentPositionMillis.value)
        verify(exactly = 1) { native.seekTo(2000) }
        verify(exactly = 1) { native.prepareAsync() }
        verify(exactly = 1) { native.start() }
        player.play("https://example.test/audio", 2000)
        verify(exactly = 1) { native.prepareAsync() }
        verify(exactly = 2) { native.start() }
        player.stop()
    }

    @Test
    fun player_prepare_failure_completion_and_error_release_the_owned_player() {
        for (stage in listOf("source", "prepare", "completion", "error")) {
            val native = mockk<MediaPlayer>(relaxed = true)
            val complete = slot<MediaPlayer.OnCompletionListener>()
            val error = slot<MediaPlayer.OnErrorListener>()
            every { native.setOnCompletionListener(capture(complete)) } just Runs
            every { native.setOnErrorListener(capture(error)) } just Runs
            if (stage == "source") every { native.setDataSource(any<Context>(), any<android.net.Uri>()) } throws IllegalArgumentException("source")
            if (stage == "prepare") every { native.prepareAsync() } throws IllegalStateException("prepare")
            val player = object : AndroidAudioPlayer(context) { override fun createPlayer() = native }
            player.play("https://example.test/audio")
            if (stage == "completion") complete.captured.onCompletion(native)
            if (stage == "error") error.captured.onError(native, 1, 1)
            verify(exactly = 1) { native.release() }
            assertFalse(player.isPlaying.value)
            player.stop()
            verify(exactly = 1) { native.release() }
        }
    }
}
