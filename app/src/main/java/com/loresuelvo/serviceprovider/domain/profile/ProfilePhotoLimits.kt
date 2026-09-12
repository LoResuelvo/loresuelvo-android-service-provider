package com.loresuelvo.serviceprovider.domain.profile

/**
 * Technical and business limits for the provider profile photo.
 * Exactly 5 MiB (5 * 1024 * 1024 bytes) is the maximum allowed size.
 */
object ProfilePhotoLimits {
    const val MAX_BYTES: Long = 5L * 1024L * 1024L // 5,242,880 bytes

    val ALLOWED_MIME_TYPES: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
    )
}
