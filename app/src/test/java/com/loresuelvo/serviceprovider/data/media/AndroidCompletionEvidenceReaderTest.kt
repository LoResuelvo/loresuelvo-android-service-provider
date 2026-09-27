package com.loresuelvo.serviceprovider.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidCompletionEvidenceReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reader = AndroidCompletionEvidenceReader(context)

    @Test fun `reads exact bytes from private completion cache`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val file = File(context.cacheDir, "completion_evidence/image.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(bytes)
        }
        try {
            assertArrayEquals(bytes, reader.read(PreparedEvidenceImage("image.jpg", "image/jpeg", 3, file.path)))
        } finally {
            file.delete()
        }
    }

    @Test fun `rejects changed oversized or outside-cache files`() = runTest {
        val file = File(context.cacheDir, "completion_evidence/changed.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val outside = File(context.cacheDir, "outside.jpg").apply { writeBytes(byteArrayOf(1)) }
        try {
            assertNull(reader.read(PreparedEvidenceImage("changed.jpg", "image/jpeg", 3, file.path)))
            assertNull(reader.read(PreparedEvidenceImage("changed.jpg", "image/jpeg", 5_242_881, file.path)))
            assertNull(reader.read(PreparedEvidenceImage("outside.jpg", "image/jpeg", 1, outside.path)))
        } finally {
            file.delete()
            outside.delete()
        }
    }
}
