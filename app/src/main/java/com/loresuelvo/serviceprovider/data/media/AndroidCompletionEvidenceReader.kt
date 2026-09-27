package com.loresuelvo.serviceprovider.data.media

import android.content.Context
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidenceReader
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoLimits
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class AndroidCompletionEvidenceReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : CompletionEvidenceReader {
    override suspend fun read(image: PreparedEvidenceImage): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = File(image.localPath).canonicalFile
            val privateDir = File(context.cacheDir, "completion_evidence").canonicalFile
            if (file.parentFile != privateDir || image.sizeBytes !in 1..ProfilePhotoLimits.MAX_BYTES || file.length() != image.sizeBytes) {
                return@withContext null
            }
            file.inputStream().use { input ->
                val bytes = ByteArray(image.sizeBytes.toInt() + 1)
                var count = 0
                while (count < bytes.size) {
                    val read = input.read(bytes, count, bytes.size - count)
                    if (read < 0) break
                    count += read
                }
                if (count == image.sizeBytes.toInt()) bytes.copyOf(count) else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
