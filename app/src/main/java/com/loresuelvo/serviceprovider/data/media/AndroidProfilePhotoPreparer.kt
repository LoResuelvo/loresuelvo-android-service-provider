package com.loresuelvo.serviceprovider.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoLimits
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoPreparer
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [ProfilePhotoPreparer].
 * Safely streams image content from ContentResolver into a private cache file,
 * strictly enforcing the 5 MiB ceiling and verifying format and decodability.
 */
@Singleton
class AndroidProfilePhotoPreparer @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProfilePhotoPreparer {

    override suspend fun preparePhoto(source: String): PhotoValidationOutcome =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(source)
            val resolver = context.contentResolver

            val originalName = queryDisplayName(uri) ?: "profile_photo"
            val resolvedMime = (resolver.getType(uri) ?: inferMimeFromExtension(originalName))?.lowercase()

            if (resolvedMime == null || resolvedMime !in ProfilePhotoLimits.ALLOWED_MIME_TYPES) {
                return@withContext PhotoValidationOutcome.Invalid.UnsupportedFormat
            }

            val photoDir = File(context.cacheDir, "profile_photos").apply { mkdirs() }
            val tempFile = File.createTempFile("photo_", ".tmp", photoDir)

            try {
                val input = openInputStream(uri, source) ?: run {
                    tempFile.delete()
                    return@withContext PhotoValidationOutcome.Invalid.Unreadable
                }

                val totalBytes = copyWithLimit(input, tempFile, ProfilePhotoLimits.MAX_BYTES)
                    ?: run {
                        tempFile.delete()
                        return@withContext PhotoValidationOutcome.Invalid.ExceedsMaxSize
                    }

                if (totalBytes == 0L) {
                    tempFile.delete()
                    return@withContext PhotoValidationOutcome.Invalid.EmptyFile
                }

                if (!isValidImageHeader(tempFile)) {
                    tempFile.delete()
                    return@withContext PhotoValidationOutcome.Invalid.CorruptContent
                }

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tempFile.absolutePath, options)

                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    tempFile.delete()
                    return@withContext PhotoValidationOutcome.Invalid.CorruptContent
                }

                val actualMime = options.outMimeType?.lowercase() ?: resolvedMime
                if (actualMime !in ProfilePhotoLimits.ALLOWED_MIME_TYPES) {
                    tempFile.delete()
                    return@withContext PhotoValidationOutcome.Invalid.UnsupportedFormat
                }

                PhotoValidationOutcome.Valid(
                    SelectedProfilePhoto(
                        originalName = originalName,
                        mimeType = actualMime,
                        sizeBytes = totalBytes,
                        localPath = tempFile.absolutePath,
                    ),
                )
            } catch (e: Exception) {
                tempFile.delete()
                PhotoValidationOutcome.Invalid.Unreadable
            }
        }

    private fun openInputStream(uri: Uri, fallbackPath: String): java.io.InputStream? =
        try {
            if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path ?: fallbackPath
                java.io.FileInputStream(File(path))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (_: Exception) {
            null
        }

    private fun copyWithLimit(input: java.io.InputStream, destination: File, maxBytes: Long): Long? {
        var totalBytes = 0L
        val buffer = ByteArray(8192)
        FileOutputStream(destination).use { output ->
            input.use { stream ->
                var read = stream.read(buffer)
                while (read != -1) {
                    totalBytes += read
                    if (totalBytes > maxBytes) {
                        return null
                    }
                    output.write(buffer, 0, read)
                    read = stream.read(buffer)
                }
            }
        }
        return totalBytes
    }

    override suspend fun cleanPhoto(photo: SelectedProfilePhoto) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(photo.localPath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment
    }

    private fun isValidImageHeader(file: File): Boolean {
        if (file.length() < 12) return false
        val header = ByteArray(12)
        try {
            java.io.FileInputStream(file).use { input ->
                val read = input.read(header)
                if (read < 12) return false
            }
        } catch (_: Exception) {
            return false
        }

        val isJpeg = (header[0].toInt() and 0xFF) == 0xFF &&
                (header[1].toInt() and 0xFF) == 0xD8 &&
                (header[2].toInt() and 0xFF) == 0xFF
        val isPng = (header[0].toInt() and 0xFF) == 0x89 &&
                (header[1].toInt() and 0xFF) == 0x50 &&
                (header[2].toInt() and 0xFF) == 0x4E &&
                (header[3].toInt() and 0xFF) == 0x47 &&
                (header[4].toInt() and 0xFF) == 0x0D &&
                (header[5].toInt() and 0xFF) == 0x0A &&
                (header[6].toInt() and 0xFF) == 0x1A &&
                (header[7].toInt() and 0xFF) == 0x0A
        val isWebp = header[0] == 'R'.code.toByte() &&
                header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() &&
                header[3] == 'F'.code.toByte() &&
                header[8] == 'W'.code.toByte() &&
                header[9] == 'E'.code.toByte() &&
                header[10] == 'B'.code.toByte() &&
                header[11] == 'P'.code.toByte()

        return isJpeg || isPng || isWebp
    }

    private fun inferMimeFromExtension(filename: String): String? {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> null
        }
    }
}
