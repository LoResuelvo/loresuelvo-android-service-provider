package com.loresuelvo.serviceprovider.data.api.upload

import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome

/**
 * Port for executing a direct PUT upload to a signed storage URL.
 */
interface FileUploader {
    suspend fun upload(
        uploadUrl: String,
        headers: Map<String, String>,
        bytes: ByteArray,
    ): UploadBytesOutcome
}
