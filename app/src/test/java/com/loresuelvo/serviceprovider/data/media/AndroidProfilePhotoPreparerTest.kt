package com.loresuelvo.serviceprovider.data.media

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoLimits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class AndroidProfilePhotoPreparerTest {

    private lateinit var context: Context
    private lateinit var preparer: AndroidProfilePhotoPreparer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preparer = AndroidProfilePhotoPreparer(context)
    }

    @Test
    fun `preparePhoto accepts valid JPEG image`() = runTest {
        val file = createTestBitmapFile("valid.jpg", Bitmap.CompressFormat.JPEG)
        val outcome = preparer.preparePhoto(file.toURI().toString())

        assertTrue("Expected Valid outcome but got $outcome", outcome is PhotoValidationOutcome.Valid)
        val valid = outcome as PhotoValidationOutcome.Valid
        assertEquals("image/jpeg", valid.photo.mimeType)
        assertTrue(valid.photo.sizeBytes > 0)
    }

    @Test
    fun `preparePhoto accepts valid PNG image`() = runTest {
        val file = createTestBitmapFile("valid.png", Bitmap.CompressFormat.PNG)
        val outcome = preparer.preparePhoto(file.toURI().toString())

        assertTrue("Expected Valid outcome but got $outcome", outcome is PhotoValidationOutcome.Valid)
        val valid = outcome as PhotoValidationOutcome.Valid
        assertEquals("image/png", valid.photo.mimeType)
    }

    @Test
    fun `preparePhoto rejects unsupported GIF format`() = runTest {
        val file = File(context.cacheDir, "test.gif").apply {
            writeBytes("GIF89a".toByteArray())
        }
        val outcome = preparer.preparePhoto(file.toURI().toString())

        assertEquals(PhotoValidationOutcome.Invalid.UnsupportedFormat, outcome)
    }

    @Test
    fun `preparePhoto rejects empty file`() = runTest {
        val file = File(context.cacheDir, "empty.jpg").apply {
            createNewFile()
        }
        val outcome = preparer.preparePhoto(file.toURI().toString())

        assertEquals(PhotoValidationOutcome.Invalid.EmptyFile, outcome)
    }

    @Test
    fun `preparePhoto rejects corrupt content declared as jpeg`() = runTest {
        val file = File(context.cacheDir, "corrupt.jpg").apply {
            writeBytes("not a real image content".toByteArray())
        }
        val outcome = preparer.preparePhoto(file.toURI().toString())

        assertEquals(PhotoValidationOutcome.Invalid.CorruptContent, outcome)
    }

    @Test
    fun `preparePhoto rejects file exceeding 5 MiB`() = runTest {
        val file = File(context.cacheDir, "oversized.jpg").apply {
            // Write 5MB + 1 byte
            FileOutputStream(this).use { fos ->
                val chunk = ByteArray(1024 * 1024)
                repeat(5) { fos.write(chunk) }
                fos.write(ByteArray(1))
            }
        }
        val outcome = preparer.preparePhoto(file.toURI().toString())

        assertEquals(PhotoValidationOutcome.Invalid.ExceedsMaxSize, outcome)
    }

    @Test
    fun `cleanPhoto deletes temporary photo file`() = runTest {
        val file = createTestBitmapFile("to_delete.jpg", Bitmap.CompressFormat.JPEG)
        val outcome = preparer.preparePhoto(file.toURI().toString()) as PhotoValidationOutcome.Valid

        assertTrue(File(outcome.photo.localPath).exists())
        preparer.cleanPhoto(outcome.photo)
        assertTrue(!File(outcome.photo.localPath).exists())
    }

    private fun createTestBitmapFile(name: String, format: Bitmap.CompressFormat): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bitmap.compress(format, 90, out)
        }
        return file
    }
}
