package com.loresuelvo.serviceprovider.data.media

import com.loresuelvo.serviceprovider.domain.conversation.MediaReader
import com.loresuelvo.serviceprovider.domain.conversation.MAX_AUDIO_BYTES
import com.loresuelvo.serviceprovider.domain.conversation.MAX_IMAGE_BYTES
import com.loresuelvo.serviceprovider.domain.conversation.SUPPORTED_IMAGE_MIME_TYPES
import com.loresuelvo.serviceprovider.domain.conversation.MediaReadException
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidMediaReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaReader {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun read(uri: String): MediaUpload = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uri)
            val mimeType = resolver.getType(uri)
                ?: inferMimeFromUri(uri)
                ?: DEFAULT_MIME
            val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "attachment"
            if (!mimeType.startsWith("audio/") && mimeType !in SUPPORTED_IMAGE_MIME_TYPES) {
                throw MediaReadException(SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.UnsupportedFormat))
            }
            val limit = if (mimeType.startsWith("audio/")) MAX_AUDIO_BYTES else MAX_IMAGE_BYTES
            val bytes = resolver.openInputStream(uri)?.use { stream -> readBoundedMedia(stream, limit) } ?: throw java.io.IOException(
                "Could not open local attachment",
            )
            if (bytes.isEmpty()) throw MediaReadException(SendMessageOutcome.Failure.Server(0, "Media payload is empty"))
            val media = if (mimeType.startsWith("audio/")) MediaUpload.Audio(
                bytes = bytes, mimeType = mimeType, originalName = displayName, durationMillis = readAudioDuration(uri),
            ) else MediaUpload.Image(bytes = bytes, mimeType = mimeType, originalName = displayName)
            com.loresuelvo.serviceprovider.domain.conversation.validateMediaUploads(listOf(media))?.let { throw MediaReadException(it) }
            media
        } catch (e: SecurityException) {
            throw java.io.IOException("Local attachment is inaccessible", e)
        } catch (e: IllegalArgumentException) {
            throw java.io.IOException("Local attachment is unreadable", e)
        }
    }

    private fun readAudioDuration(uri: Uri): Long {
        val metadata = android.media.MediaMetadataRetriever()
        return try {
            metadata.setDataSource(context, uri)
            metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: RuntimeException) {
            throw java.io.IOException("Audio metadata is unreadable", e)
        } finally {
            metadata.release()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return null
        return cursor.use { c ->
            if (!c.moveToFirst()) return@use null
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0) null else c.getString(idx)
        }
    }

    // Recorder-owned WebM files contain audio, so infer audio/webm.
    private fun inferMimeFromUri(uri: Uri): String? {
        val last = uri.lastPathSegment ?: return null
        val dot = last.lastIndexOf('.')
        if (dot <= 0 || dot == last.length - 1) return null
        val ext = last.substring(dot + 1).lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "webm" -> "audio/webm"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "3gp" -> "audio/3gpp"
            else -> null
        }
    }

    private companion object {
        const val DEFAULT_MIME: String = "application/octet-stream"
    }
}
