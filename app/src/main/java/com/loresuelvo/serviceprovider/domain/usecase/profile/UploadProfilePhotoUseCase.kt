package com.loresuelvo.serviceprovider.domain.usecase.profile

import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.FilePurpose
import com.loresuelvo.serviceprovider.domain.file.FileRepository
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class UploadProfilePhotoUseCase @Inject constructor(
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(photo: SelectedProfilePhoto): UploadProfilePhotoOutcome {
        try {
            val presignOutcome = fileRepository.presign(
                PresignUploadRequest(
                    originalName = photo.originalName,
                    mimeType = photo.mimeType,
                    sizeBytes = photo.sizeBytes,
                    purpose = FilePurpose.PROFILE_PHOTO,
                ),
            )
            val presignResult = when (presignOutcome) {
                is PresignUploadOutcome.Success -> presignOutcome.result
                is PresignUploadOutcome.Failure.Network ->
                    return UploadProfilePhotoOutcome.Failure.Network("Network error during presign", presignOutcome.cause)
                is PresignUploadOutcome.Failure.Unauthorized ->
                    return UploadProfilePhotoOutcome.Failure.Unauthorized(presignOutcome.message)
                is PresignUploadOutcome.Failure.Server ->
                    return UploadProfilePhotoOutcome.Failure.Server(presignOutcome.code, presignOutcome.message)
            }

            val bytes = try {
                File(photo.localPath).readBytes()
            } catch (e: Exception) {
                return UploadProfilePhotoOutcome.Failure.LocalFileError("Cannot read local photo file")
            }

            val uploadOutcome = fileRepository.uploadBytes(
                uploadUrl = presignResult.uploadUrl,
                headers = presignResult.headers,
                bytes = bytes,
            )
            when (uploadOutcome) {
                is UploadBytesOutcome.Success -> Unit
                is UploadBytesOutcome.Failure.Network ->
                    return UploadProfilePhotoOutcome.Failure.Network("Network error during upload", uploadOutcome.cause)
                is UploadBytesOutcome.Failure.Unauthorized ->
                    return UploadProfilePhotoOutcome.Failure.Unauthorized(uploadOutcome.message)
                is UploadBytesOutcome.Failure.Server ->
                    return UploadProfilePhotoOutcome.Failure.Server(uploadOutcome.code, uploadOutcome.message)
            }

            val confirmOutcome = fileRepository.confirm(
                fileId = presignResult.fileId,
                request = ConfirmUploadRequest(
                    key = presignResult.key,
                    mimeType = photo.mimeType,
                    sizeBytes = photo.sizeBytes,
                ),
            )
            return when (confirmOutcome) {
                is ConfirmUploadOutcome.Success ->
                    UploadProfilePhotoOutcome.Success(confirmOutcome.file)
                is ConfirmUploadOutcome.Failure.Network ->
                    UploadProfilePhotoOutcome.Failure.Network("Network error during confirmation", confirmOutcome.cause)
                is ConfirmUploadOutcome.Failure.Unauthorized ->
                    UploadProfilePhotoOutcome.Failure.Unauthorized(confirmOutcome.message)
                is ConfirmUploadOutcome.Failure.Server ->
                    UploadProfilePhotoOutcome.Failure.Server(confirmOutcome.code, confirmOutcome.message)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }
}
