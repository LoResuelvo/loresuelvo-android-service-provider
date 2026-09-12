package com.loresuelvo.serviceprovider.domain.profile

/**
 * Pure domain representation of a verified, selected profile photo candidate.
 * Contains metadata and the opaque local path of the prepared cache file.
 */
data class SelectedProfilePhoto(
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
)
