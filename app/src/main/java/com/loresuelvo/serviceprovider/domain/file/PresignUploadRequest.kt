package com.loresuelvo.serviceprovider.domain.file

/**
 * Domain request parameters for requesting a presigned upload URL.
 */
data class PresignUploadRequest(
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val purpose: FilePurpose = FilePurpose.PROFILE_PHOTO,
)
