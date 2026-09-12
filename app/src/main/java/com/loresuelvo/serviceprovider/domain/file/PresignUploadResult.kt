package com.loresuelvo.serviceprovider.domain.file

/**
 * Pre-signed upload target issued by the backend.
 */
data class PresignUploadResult(
    val fileId: String,
    val key: String,
    val uploadUrl: String,
    val headers: Map<String, String>,
)
