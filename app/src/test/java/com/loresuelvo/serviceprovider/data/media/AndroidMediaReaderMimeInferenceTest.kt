package com.loresuelvo.serviceprovider.data.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the mime-type dispatch in [AndroidMediaReader]. The
 * provider's 01-PCA audio flow depends on `file://` URIs from
 * the audio recorder (which Android's `ContentResolver.getType`
 * cannot resolve) falling through to the extension-based
 * inference; a previous bug mapped `.webm` to `null` (because
 * the inference table only listed image extensions), which
 * then routed the audio upload through the
 * `MediaUpload.Image` branch with `mime = application/octet-stream`,
 * and the backend rejected the presign with
 * `ErrUnsupportedMessageImage`.
 *
 * Robolectric runs the JVM-side `ContentResolver` against the
 * Robolectric shadow filesystem so `Uri.fromFile(...)` URIs
 * have a real `ContentResolver` (returning `null` for the
 * recorder's cacheDir paths, as production does).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidMediaReaderMimeInferenceTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var reader: AndroidMediaReader

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resolver = context.contentResolver
        reader = AndroidMediaReader(context)
    }

    @Test
    fun audio_recorder_webm_uri_is_typed_as_audio_not_image() = runTest {
        // The recorder writes files named `audio-<uuid>.webm` in
        // `cacheDir`. We materialise a tiny placeholder so the
        // reader's `openInputStream` succeeds; the test only
        // pins the mime inference + dispatch, not the bytes.
        val file = java.io.File(context.cacheDir, "audio-1234.webm")
        file.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        val uri = Uri.fromFile(file)

        // Defensive: confirm the resolver doesn't accidentally
        // return a mime for this URI (it shouldn't — cacheDir
        // isn't indexed by MediaStore). If a future Robolectric
        // change starts returning a mime here, the test pins
        // the behaviour change so we notice.
        val resolverMime = resolver.getType(uri)
        assertEquals(
            "ContentResolver must return null for cacheDir file URIs " +
                "so the inference fallback fires (got '$resolverMime')",
            null,
            resolverMime,
        )

        val upload = reader.read(uri)
        assertTrue(
            "audio recorder .webm must produce MediaUpload.Audio, was $upload",
            upload is MediaUpload.Audio,
        )
        val audio = upload as MediaUpload.Audio
        assertEquals("audio/webm", audio.mimeType)
        assertEquals("audio-1234.webm", audio.originalName)
    }
}
