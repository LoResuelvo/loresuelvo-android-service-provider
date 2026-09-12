package com.loresuelvo.serviceprovider.domain.file

/**
 * Confirmed file metadata after successful presign, storage upload, and backend confirmation.
 */
data class ConfirmedFile(
    val id: String,
    val url: String? = null,
    val mimeType: String,
    val originalName: String,
)
