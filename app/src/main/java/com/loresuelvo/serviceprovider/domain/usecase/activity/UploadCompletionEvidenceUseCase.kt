package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidenceReader
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.file.*
import javax.inject.Inject

enum class CompletionUploadStage { PRESIGN, LOCAL_FILE, TRANSFER, CONFIRM }
enum class CompletionUploadFailure { NETWORK, UNAUTHORIZED, SERVER, UNREADABLE, INVALID_RESPONSE }

sealed interface CompletionEvidenceUpload {
    data class Success(val confirmedFileId: String) : CompletionEvidenceUpload
    data class Failure(
        val stage: CompletionUploadStage,
        val reason: CompletionUploadFailure,
        val statusCode: Int? = null,
    ) : CompletionEvidenceUpload
}

class UploadCompletionEvidenceUseCase @Inject constructor(
    private val files: FileRepository,
    private val reader: CompletionEvidenceReader,
) {
    suspend operator fun invoke(image: PreparedEvidenceImage): CompletionEvidenceUpload {
        val presigned = when (val outcome = files.presign(
            PresignUploadRequest(image.originalName, image.mimeType, image.sizeBytes, FilePurpose.WORK_ORDER_COMPLETION_IMAGE),
        )) {
            is PresignUploadOutcome.Success -> outcome.result
            is PresignUploadOutcome.Failure.Network -> return CompletionEvidenceUpload.Failure(CompletionUploadStage.PRESIGN, CompletionUploadFailure.NETWORK)
            is PresignUploadOutcome.Failure.Unauthorized -> return CompletionEvidenceUpload.Failure(CompletionUploadStage.PRESIGN, CompletionUploadFailure.UNAUTHORIZED)
            is PresignUploadOutcome.Failure.Server -> return CompletionEvidenceUpload.Failure(CompletionUploadStage.PRESIGN, CompletionUploadFailure.SERVER, outcome.code)
        }
        if (presigned.fileId.isBlank() || presigned.key.isBlank() || presigned.uploadUrl.isBlank()) {
            return CompletionEvidenceUpload.Failure(CompletionUploadStage.PRESIGN, CompletionUploadFailure.INVALID_RESPONSE)
        }

        val bytes = reader.read(image)
            ?: return CompletionEvidenceUpload.Failure(CompletionUploadStage.LOCAL_FILE, CompletionUploadFailure.UNREADABLE)
        when (val outcome = files.uploadBytes(presigned.uploadUrl, presigned.headers, bytes)) {
            UploadBytesOutcome.Success -> Unit
            is UploadBytesOutcome.Failure.Network -> return CompletionEvidenceUpload.Failure(CompletionUploadStage.TRANSFER, CompletionUploadFailure.NETWORK)
            is UploadBytesOutcome.Failure.Unauthorized -> return CompletionEvidenceUpload.Failure(CompletionUploadStage.TRANSFER, CompletionUploadFailure.SERVER)
            is UploadBytesOutcome.Failure.Server -> return CompletionEvidenceUpload.Failure(CompletionUploadStage.TRANSFER, CompletionUploadFailure.SERVER, outcome.code)
        }

        return when (val outcome = files.confirm(
            presigned.fileId,
            ConfirmUploadRequest(presigned.key, image.mimeType, image.sizeBytes),
        )) {
            is ConfirmUploadOutcome.Success -> if (outcome.file.id.isNotBlank() && outcome.file.id == presigned.fileId) {
                CompletionEvidenceUpload.Success(outcome.file.id)
            } else {
                CompletionEvidenceUpload.Failure(CompletionUploadStage.CONFIRM, CompletionUploadFailure.INVALID_RESPONSE)
            }
            is ConfirmUploadOutcome.Failure.Network -> CompletionEvidenceUpload.Failure(CompletionUploadStage.CONFIRM, CompletionUploadFailure.NETWORK)
            is ConfirmUploadOutcome.Failure.Unauthorized -> CompletionEvidenceUpload.Failure(CompletionUploadStage.CONFIRM, CompletionUploadFailure.UNAUTHORIZED)
            is ConfirmUploadOutcome.Failure.Server -> CompletionEvidenceUpload.Failure(CompletionUploadStage.CONFIRM, CompletionUploadFailure.SERVER, outcome.code)
        }
    }
}
