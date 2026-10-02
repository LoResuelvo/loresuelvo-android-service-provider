package com.loresuelvo.serviceprovider.domain.conversation

/**
 * Media payload the provider is about to upload to a
 * conversation. Carries the bytes (read locally from a
 * `content://` URI), the declared mime type, and the original
 * file name. The data layer maps each variant to its own
 * multipart / file-purpose flow before posting the JSON
 * message body.
 *
 * Sealed for the same forward-compatibility reason as
 * [MediaReference]: each variant declares its own extra fields
 * and the UI / data layer renders an exhaustive `when`.
 *
 * Pure domain. The only non-pure surface is [bytes] — a JDK
 * type, not Android — so the abstraction stays testable in
 * plain JUnit.
 */
sealed interface MediaUpload {

    val bytes: ByteArray

    val mimeType: String

    val originalName: String

    /**
     * Image the provider picked from the gallery or just
     * captured with the camera. Bytes are cached locally so
     * the upload can survive the temporary URI permission
     * being revoked between attach and confirm.
     */
    data class Image(
        override val bytes: ByteArray,
        override val mimeType: String,
        override val originalName: String,
    ) : MediaUpload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return bytes.contentEquals(other.bytes) &&
                mimeType == other.mimeType &&
                originalName == other.originalName
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + originalName.hashCode()
            return result
        }
    }

    /**
     * Audio clip the provider recorded in-app. [durationMillis]
     * is measured at recording time so the server can echo it
     * back without re-decoding the file.
     */
    data class Audio(
        override val bytes: ByteArray,
        override val mimeType: String,
        override val originalName: String,
        val durationMillis: Long,
    ) : MediaUpload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Audio) return false
            return bytes.contentEquals(other.bytes) &&
                mimeType == other.mimeType &&
                originalName == other.originalName &&
                durationMillis == other.durationMillis
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + originalName.hashCode()
            result = 31 * result + durationMillis.hashCode()
            return result
        }
    }
}

/** Backend audio policy: WebM/Opus, five MiB, five minutes. */
const val MAX_AUDIO_BYTES: Long = 5L * 1024L * 1024L
const val MIN_AUDIO_DURATION_MILLIS: Long = 1000L
const val MAX_AUDIO_DURATION_MILLIS: Long = 300_000L

const val MAX_IMAGE_BYTES: Long = 5L * 1024L * 1024L
const val MAX_MESSAGE_IMAGES: Int = 3
val SUPPORTED_IMAGE_MIME_TYPES: Set<String> = setOf("image/jpeg", "image/png", "image/webp")

/** Checked at both local input and repository trust boundaries before any upload. */
fun validateMediaUploads(media: List<MediaUpload>): SendMessageOutcome.Failure? {
    if (media.isEmpty() || media.any { it.bytes.isEmpty() }) {
        return SendMessageOutcome.Failure.Server(0, "Media payload is empty")
    }
    if (media.any { it is MediaUpload.Image } && media.any { it is MediaUpload.Audio }) {
        return SendMessageOutcome.Failure.Server(0, "Mixed media is unsupported")
    }
    if (media.first() is MediaUpload.Image && media.size > MAX_MESSAGE_IMAGES) {
        return SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.TooManyImages)
    }
    if (media.first() is MediaUpload.Audio && media.size != 1) {
        return SendMessageOutcome.Failure.Server(0, "Audio messages accept exactly one clip")
    }
    media.forEach { attachment ->
        if (attachment is MediaUpload.Image && attachment.mimeType !in SUPPORTED_IMAGE_MIME_TYPES) {
            return SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.UnsupportedFormat)
        }
        if (attachment is MediaUpload.Audio) {
            if (attachment.mimeType != "audio/webm") return SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.UnsupportedAudioFormat)
            if (attachment.durationMillis !in MIN_AUDIO_DURATION_MILLIS..MAX_AUDIO_DURATION_MILLIS) {
                return SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.InvalidAudioDuration)
            }
        }
        val limit = if (attachment is MediaUpload.Image) MAX_IMAGE_BYTES else MAX_AUDIO_BYTES
        if (attachment.bytes.size.toLong() > limit) return SendMessageOutcome.Failure.PayloadTooLarge(limit)
    }
    return null
}
