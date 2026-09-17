package com.loresuelvo.serviceprovider.domain.conversation

/**
 * Reference to a media file attached to a conversation message.
 * Pure domain: describes the persisted side of an attachment
 * (the remote URL the backend issued, the declared mime type,
 * the original file name the user picked).
 *
 * The bytes themselves never live in domain — the upload path
 * carries them through the data layer and discards them after
 * the multipart POST returns. The DTO carries `id` for
 * traceability with the conversation message and to support
 * future flows that re-use a previously uploaded file.
 *
 * Sealed so future variants (audio in US-C, video) can extend
 * without leaking extra fields through the parent. The UI
 * renders an exhaustive `when` over the sealed hierarchy so a
 * new variant never ships unhandled.
 */
sealed interface MediaReference {

    val url: String

    val mimeType: String

    val originalName: String

    /**
     * Image attachment. Carries the backend-issued [id]
     * (string UUID) for traceability, the private time-limited
     * [url] the bubble downloads, the declared [mimeType]
     * (`image/jpeg`, `image/png` or `image/webp` for the
     * provider ↔ consumer chat), and the [originalName] the
     * provider picked.
     */
    data class Image(
        val id: String,
        override val url: String,
        override val mimeType: String,
        override val originalName: String,
    ) : MediaReference
}
