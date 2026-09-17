package com.loresuelvo.serviceprovider.data.media

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

/**
 * Android-backed [MediaReader] — the only place in the
 * codebase that touches `ContentResolver` /
 * `OpenableColumns`. Resolves a `content://` or `file://` URI
 * into bytes + mime + display name and packages them as the
 * domain [MediaUpload] the use case layer can hand to the
 * repository.
 *
 * IO dispatch: every blocking call (`openInputStream`,
 * `query`) runs on [Dispatchers.IO] via [withContext] so the
 * caller (the VM) can stay on the main dispatcher without
 * tripping the strict-mode ANR detector. The JVM unit tests
 * stub the [MediaReader] interface itself, so this impl never
 * needs to be instantiated off-device.
 *
 * The mime detection prefers `ContentResolver.getType(uri)`
 * (the Android-publisher's declared type) and falls back to
 * inferring from the file-extension tail when the resolver
 * returns `null`. The display name comes from
 * `OpenableColumns.DISPLAY_NAME` and falls back to the URI's
 * last path segment when the cursor returns no column.
 *
 * US-B only emits [MediaUpload.Image] (audio lands in US-C).
 * A mime that doesn't start with `image/` still packages as
 * `MediaUpload.Image` so the backend's presign endpoint —
 * which expects `conversation_message_image` as the purpose —
 * doesn't reject the upload with `ErrUnsupportedMessageImage`.
 * The backend validates the actual bytes after the upload.
 */
@Singleton
class AndroidMediaReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaReader {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun read(uri: Uri): MediaUpload = withContext(Dispatchers.IO) {
        val mimeType = resolver.getType(uri)
            ?: inferMimeFromUri(uri)
            ?: DEFAULT_MIME
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "attachment"
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            stream.readBytes()
        } ?: throw java.io.IOException(
            "Could not open input stream for $uri",
        )
        MediaUpload.Image(
            bytes = bytes,
            mimeType = mimeType,
            originalName = displayName,
        )
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

    /**
     * Best-effort mime inference from the URI's last path
     * segment. Returns `null` when no extension matches; the
     * caller falls back to [DEFAULT_MIME] so the upload still
     * ships rather than crashing on a backend that rejects
     * empty `Content-Type`.
     */
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
            else -> null
        }
    }

    private companion object {
        const val DEFAULT_MIME: String = "application/octet-stream"
    }
}
