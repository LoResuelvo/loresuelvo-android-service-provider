package com.loresuelvo.serviceprovider.acceptance.messaging

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.loresuelvo.serviceprovider.data.media.AndroidAudioPlayer
import com.loresuelvo.serviceprovider.data.media.AndroidAudioRecorder
import com.loresuelvo.serviceprovider.data.media.AndroidMediaReader
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real microphone/codec/metadata/player integration; executed only during final verification. */
@RunWith(AndroidJUnit4::class)
class AndroidVoiceMediaIntegrationTest {
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun webm_opus_recording_metadata_preview_seek_completion_and_discard_use_real_adapters() = runBlocking {
        val recorder = AndroidAudioRecorder(context)
        val player = AndroidAudioPlayer(context)
        var uri: String? = null
        try {
            instrumentation.runOnMainSync { assertTrue(recorder.start().isSuccess) }
            // Actual encoded media is the subject: allow the native microphone to capture a valid clip.
            delay(2200)
            instrumentation.runOnMainSync { uri = recorder.stop().getOrThrow() }
            val audio = AndroidMediaReader(context).read(uri!!) as MediaUpload.Audio
            assertEquals("audio/webm", audio.mimeType)
            assertTrue(audio.durationMillis in 1000..300_000)
            assertTrue(audio.bytes.isNotEmpty())
            instrumentation.runOnMainSync { player.play(uri!!) }
            withTimeout(10_000) { player.isPlaying.first { it } }
            instrumentation.runOnMainSync { player.pause(); player.seekTo(1000) }
            assertFalse(player.isPlaying.value)
            assertEquals(1000L, player.currentPositionMillis.value)
            instrumentation.runOnMainSync { player.play(uri!!, 1000) }
            withTimeout(10_000) { player.isPlaying.first { it } }
            withTimeout(10_000) { player.isPlaying.first { !it } }
            assertEquals(0L, player.currentPositionMillis.value)
        } finally {
            instrumentation.runOnMainSync { player.stop(); recorder.cancel(); uri?.let(recorder::discard) }
        }
        assertFalse(java.io.File(android.net.Uri.parse(uri).path!!).exists())
    }

    @Test
    fun cancel_recording_removes_native_temporary_output_and_allows_new_capture() = runBlocking {
        val recorder = AndroidAudioRecorder(context)
        val before = context.cacheDir.listFiles()?.filter { it.name.startsWith("audio-") }?.toSet().orEmpty()
        try {
            instrumentation.runOnMainSync { assertTrue(recorder.start().isSuccess) }
            delay(300)
            instrumentation.runOnMainSync { recorder.cancel(); assertTrue(recorder.start().isSuccess); recorder.cancel() }
            assertEquals(before, context.cacheDir.listFiles()?.filter { it.name.startsWith("audio-") }?.toSet().orEmpty())
        } finally { instrumentation.runOnMainSync { recorder.cancel() } }
    }
}
