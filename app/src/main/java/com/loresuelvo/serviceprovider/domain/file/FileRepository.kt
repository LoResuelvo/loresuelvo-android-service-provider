package com.loresuelvo.serviceprovider.domain.file

/**
 * Domain port for presigning, uploading bytes to storage, and confirming uploads.
 */
interface FileRepository {
    suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome
    suspend fun uploadBytes(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray): UploadBytesOutcome
    suspend fun confirm(fileId: String, request: ConfirmUploadRequest): ConfirmUploadOutcome
}
