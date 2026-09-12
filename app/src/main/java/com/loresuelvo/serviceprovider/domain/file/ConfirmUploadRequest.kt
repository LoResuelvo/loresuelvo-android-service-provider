package com.loresuelvo.serviceprovider.domain.file

/**
 * Domain request parameters for confirming an uploaded file with the backend.
 */
data class ConfirmUploadRequest(
    val key: String,
    val mimeType: String,
    val sizeBytes: Long,
)
