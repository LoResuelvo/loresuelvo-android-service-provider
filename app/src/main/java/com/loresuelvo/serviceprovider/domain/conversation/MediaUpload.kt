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
}
