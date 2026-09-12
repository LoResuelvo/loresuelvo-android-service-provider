package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.data.api.mapper.toDto
import com.loresuelvo.serviceprovider.data.api.upload.FileUploader
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.FileRepository
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [FileRepository] communicating with the backend's presign and confirm endpoints
 * and delegating storage PUT to [FileUploader].
 */
@Singleton
class ApiFileRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val fileUploader: FileUploader,
) : FileRepository {

    override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome =
        try {
            val response = backendApi.presignFile(request.toDto())
            PresignUploadOutcome.Success(response.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mapPresignFailure(e)
        }

    override suspend fun uploadBytes(
        uploadUrl: String,
        headers: Map<String, String>,
        bytes: ByteArray,
    ): UploadBytesOutcome =
        fileUploader.upload(
            uploadUrl = uploadUrl,
            headers = headers,
            bytes = bytes,
        )

    override suspend fun confirm(
        fileId: String,
        request: ConfirmUploadRequest,
    ): ConfirmUploadOutcome =
        try {
            val response = backendApi.confirmFile(fileId, request.toDto())
            ConfirmUploadOutcome.Success(response.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mapConfirmFailure(e)
        }

    private fun mapPresignFailure(e: Throwable): PresignUploadOutcome.Failure =
        when (val error = e.toApiError()) {
            is ApiError.Network ->
                PresignUploadOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized ->
                PresignUploadOutcome.Failure.Unauthorized(error.errorMessage)
            is ApiError.Server ->
                PresignUploadOutcome.Failure.Server(error.code, error.errorMessage)
            is ApiError.Unknown ->
                PresignUploadOutcome.Failure.Server(0, error.message ?: "Unknown error")
        }

    private fun mapConfirmFailure(e: Throwable): ConfirmUploadOutcome.Failure =
        when (val error = e.toApiError()) {
            is ApiError.Network ->
                ConfirmUploadOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized ->
                ConfirmUploadOutcome.Failure.Unauthorized(error.errorMessage)
            is ApiError.Server ->
                ConfirmUploadOutcome.Failure.Server(error.code, error.errorMessage)
            is ApiError.Unknown ->
                ConfirmUploadOutcome.Failure.Server(0, error.message ?: "Unknown error")
        }
}
