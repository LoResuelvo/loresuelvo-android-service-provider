package com.loresuelvo.serviceprovider.data.media

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidCompletionEvidencePreparerTest {
    private lateinit var context: Context
    private lateinit var preparer: AndroidCompletionEvidencePreparer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preparer = AndroidCompletionEvidencePreparer(AndroidProfilePhotoPreparer(context), context)
    }

    @Test
    fun `prepares decodable JPEG PNG and WebP in private cache`() = runTest {
        listOf(
            Triple("evidence.jpg", Bitmap.CompressFormat.JPEG, "image/jpeg"),
            Triple("evidence.png", Bitmap.CompressFormat.PNG, "image/png"),
            Triple("evidence.webp", Bitmap.CompressFormat.WEBP_LOSSLESS, "image/webp"),
        ).forEach { (name, format, mime) ->
            val outcome = preparer.prepare(file(name, format).toURI().toString())
            assertTrue(outcome is EvidenceImagePreparation.Ready)
            val image = (outcome as EvidenceImagePreparation.Ready).image
            assertEquals(mime, image.mimeType)
            assertTrue(image.sizeBytes in 1..5_242_880)
            assertTrue(File(image.localPath).exists())
            assertTrue(preparer.isAvailable(image))
            assertFalse(preparer.isAvailable(image.copy(sizeBytes = image.sizeBytes + 1)))
            assertFalse(preparer.isAvailable(image.copy(localPath = File(context.cacheDir, name).absolutePath)))
            assertFalse(preparer.isAvailable(image.copy(localPath = File(image.localPath).parent)))
            preparer.clean(image)
            assertFalse(File(image.localPath).exists())
            assertFalse(preparer.isAvailable(image))
        }
    }

    @Test
    fun `rejects invalid inputs and removes incomplete cache files`() = runTest {
        val cache = File(context.cacheDir, "completion_evidence")
        val inputs = listOf(
            File(context.cacheDir, "missing.jpg").also { it.delete() }.toURI().toString() to EvidenceImagePreparation.Invalid.Unreadable,
            File(context.cacheDir, "unsupported.gif").apply { writeText("GIF89a") }.toURI().toString() to EvidenceImagePreparation.Invalid.UnsupportedFormat,
            File(context.cacheDir, "empty.jpg").apply { writeBytes(byteArrayOf()) }.toURI().toString() to EvidenceImagePreparation.Invalid.EmptyFile,
            File(context.cacheDir, "corrupt.jpg").apply { writeText("not an image") }.toURI().toString() to EvidenceImagePreparation.Invalid.CorruptContent,
            File(context.cacheDir, "oversize.jpg").apply {
                FileOutputStream(this).use { output ->
                    repeat(5) { output.write(ByteArray(1024 * 1024)) }
                    output.write(0)
                }
            }.toURI().toString() to EvidenceImagePreparation.Invalid.ExceedsMaxSize,
        )
        inputs.forEach { (source, expected) ->
            assertEquals(expected, preparer.prepare(source))
            assertTrue(cache.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun `accepts an image at exactly 5 MiB`() = runTest {
        val source = file("exact_limit.jpg", Bitmap.CompressFormat.JPEG)
        FileOutputStream(source, true).use { output ->
            val padding = 5_242_880L - source.length()
            assertTrue(padding > 0)
            repeat((padding / 8192).toInt()) { output.write(ByteArray(8192)) }
            output.write(ByteArray((padding % 8192).toInt()))
        }

        val outcome = preparer.prepare(source.toURI().toString())
        assertTrue(outcome is EvidenceImagePreparation.Ready)
        val image = (outcome as EvidenceImagePreparation.Ready).image
        assertEquals(5_242_880L, image.sizeBytes)
        preparer.clean(image)
        assertFalse(File(image.localPath).exists())
    }

    private fun file(name: String, format: Bitmap.CompressFormat): File =
        File(context.cacheDir, name).also { file ->
            FileOutputStream(file).use { output ->
                Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).compress(format, 90, output)
            }
        }
}
