package com.loresuelvo.serviceprovider.data.api

import android.util.Log
import com.loresuelvo.serviceprovider.data.api.dto.SendMessageRequestDto
import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.FilePurpose
import com.loresuelvo.serviceprovider.domain.file.FileRepository
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.PresignUploadResult
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

@Singleton
class ApiConversationRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val fileRepository: FileRepository,
) : ConversationRepository {

    override suspend fun getConversations(): ConversationsOutcome = try {
        ConversationsOutcome.Success(backendApi.getConversations().map { it.toDomain() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        when (val error = e.toApiError()) {
            is ApiError.Network -> ConversationsOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized -> ConversationsOutcome.Failure.Unauthorized
            is ApiError.Server -> ConversationsOutcome.Failure.Server(error.code)
            is ApiError.Unknown -> ConversationsOutcome.Failure.Invalid(error.unknownCause)
        }
    }

    override suspend fun getConversationById(
        conversationId: Int,
    ): ConversationDetailOutcome = try {
        val dto = backendApi.getConversationById(conversationId)
        ConversationDetailOutcome.Success(dto.toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        when (e.code()) {
            404 -> ConversationDetailOutcome.Failure.NotFound(
                message = e.message().ifBlank { "Conversation $conversationId not found" },
            )
            else -> {
                val error = e.toApiError()
                when (error) {
                    is ApiError.Network ->
                        ConversationDetailOutcome.Failure.Network(error.networkCause)
                    is ApiError.Unauthorized ->
                        ConversationDetailOutcome.Failure.Unauthorized
                    is ApiError.Server ->
                        ConversationDetailOutcome.Failure.Server(error.code, error.errorMessage)
                    is ApiError.Unknown ->
                        ConversationDetailOutcome.Failure.Server(
                            code = e.code(),
                            message = error.message ?: "Unknown error",
                        )
                }
            }
        }
    } catch (e: Throwable) {
        val error = e.toApiError()
        when (error) {
            is ApiError.Network ->
                ConversationDetailOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized ->
                ConversationDetailOutcome.Failure.Unauthorized
            is ApiError.Server ->
                ConversationDetailOutcome.Failure.Server(error.code, error.errorMessage)
            is ApiError.Unknown ->
                ConversationDetailOutcome.Failure.Server(
                    code = 0,
                    message = error.message ?: "Unknown error",
                )
        }
    }

    override suspend fun sendMessage(
        conversationId: Int,
        content: String,
    ): SendMessageOutcome = try {
        val dto = backendApi.postMessage(
            conversationId = conversationId,
            request = SendMessageRequestDto(content = content),
        )
        SendMessageOutcome.Success(dto.toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        when (e.code()) {
            404 -> SendMessageOutcome.Failure.ConversationNotFound(
                message = e.message().ifBlank { "Conversation $conversationId not found" },
            )
            else -> {
                val error = e.toApiError()
                when (error) {
                    is ApiError.Network ->
                        SendMessageOutcome.Failure.Network(error.networkCause)
                    is ApiError.Unauthorized ->
                        SendMessageOutcome.Failure.Unauthorized
                    is ApiError.Server ->
                        SendMessageOutcome.Failure.Server(error.code, error.errorMessage)
                    is ApiError.Unknown ->
                        SendMessageOutcome.Failure.Server(
                            code = e.code(),
                            message = error.message ?: "Unknown error",
                        )
                }
            }
        }
    } catch (e: Throwable) {
        val error = e.toApiError()
        when (error) {
            is ApiError.Network ->
                SendMessageOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized ->
                SendMessageOutcome.Failure.Unauthorized
            is ApiError.Server ->
                SendMessageOutcome.Failure.Server(error.code, error.errorMessage)
            is ApiError.Unknown ->
                SendMessageOutcome.Failure.Server(
                    code = 0,
                    message = error.message ?: "Unknown error",
                )
        }
    }

    /**
     * US-B orchestrator. Walks the presign → upload → confirm
     * pipeline per attachment (US-B is image-only; US-C will
     * extend this branch with audio) and finally posts the JSON
     * message body carrying the joined file ids.
     *
     * Failure modes collapse into the same
     * [SendMessageOutcome.Failure] tree so the ViewModel renders
     * them with the existing transient error card / persistent
     * failed bubble — no new surface.
     */
    override suspend fun sendMediaMessage(
        conversationId: Int,
        media: List<MediaUpload>,
    ): SendMessageOutcome {
        if (media.isEmpty()) {
            return SendMessageOutcome.Failure.Server(
                code = 0,
                message = "Media payload is empty",
            )
        }
        return when (val first = media.first()) {
            is MediaUpload.Image -> sendImages(conversationId, media.map { it as MediaUpload.Image })
        }
    }

    private suspend fun sendImages(
        conversationId: Int,
        images: List<MediaUpload.Image>,
    ): SendMessageOutcome {
        val fileIds = mutableListOf<String>()
        for ((index, image) in images.withIndex()) {
            Log.d(
                TAG,
                "sendImages[$index/${images.size}]: " +
                    "mime=${image.mimeType} size=${image.bytes.size}B " +
                    "originalName=${image.originalName}",
            )
            val fileId = when (
                val r = runPresignUploadConfirm(
                    originalName = image.originalName,
                    mimeType = image.mimeType,
                    bytes = image.bytes,
                    purpose = FilePurpose.CONVERSATION_MESSAGE_IMAGE,
                )
            ) {
                is UploadFlow.Failure -> return r.failure
                is UploadFlow.Success -> r.fileId
            }
            fileIds += fileId
        }
        return postMessageWithImageFileIds(conversationId, fileIds)
    }

    private suspend fun postMessageWithImageFileIds(
        conversationId: Int,
        fileIds: List<String>,
    ): SendMessageOutcome = try {
        val dto = backendApi.postMessage(
            conversationId = conversationId,
            request = SendMessageRequestDto(
                content = "",
                imageFileIds = fileIds,
            ),
        )
        SendMessageOutcome.Success(dto.toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        when (e.code()) {
            404 -> SendMessageOutcome.Failure.ConversationNotFound(
                message = e.message().ifBlank { "Conversation $conversationId not found" },
            )
            else -> {
                val error = e.toApiError()
                when (error) {
                    is ApiError.Network ->
                        SendMessageOutcome.Failure.Network(error.networkCause)
                    is ApiError.Unauthorized ->
                        SendMessageOutcome.Failure.Unauthorized
                    is ApiError.Server ->
                        SendMessageOutcome.Failure.Server(error.code, error.errorMessage)
                    is ApiError.Unknown ->
                        SendMessageOutcome.Failure.Server(
                            code = e.code(),
                            message = error.message ?: "Unknown error",
                        )
                }
            }
        }
    } catch (e: Throwable) {
        val error = e.toApiError()
        when (error) {
            is ApiError.Network ->
                SendMessageOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized ->
                SendMessageOutcome.Failure.Unauthorized
            is ApiError.Server ->
                SendMessageOutcome.Failure.Server(error.code, error.errorMessage)
            is ApiError.Unknown ->
                SendMessageOutcome.Failure.Server(
                    code = 0,
                    message = error.message ?: "Unknown error",
                )
        }
    }

    /**
     * Outcome of the presign → upload → confirm pipeline. The
     * repository never throws: every HTTP / network failure is
     * mapped here so the caller (the message dispatcher) just
     * returns the carried [SendMessageOutcome.Failure] verbatim.
     */
    private sealed interface UploadFlow {
        data class Success(val fileId: String) : UploadFlow
        data class Failure(val failure: SendMessageOutcome.Failure) : UploadFlow
    }

    private suspend fun runPresignUploadConfirm(
        originalName: String,
        mimeType: String,
        bytes: ByteArray,
        purpose: FilePurpose,
    ): UploadFlow {
        val presign = fileRepository.presign(
            PresignUploadRequest(
                originalName = originalName,
                mimeType = mimeType,
                sizeBytes = bytes.size.toLong(),
                purpose = purpose,
            ),
        )
        val presignResult = when (presign) {
            is PresignUploadOutcome.Success -> presign.result
            is PresignUploadOutcome.Failure.Network ->
                return UploadFlow.Failure(SendMessageOutcome.Failure.Network(presign.cause))
            is PresignUploadOutcome.Failure.Server ->
                return UploadFlow.Failure(
                    SendMessageOutcome.Failure.Server(presign.code, presign.message),
                )
            is PresignUploadOutcome.Failure.Unauthorized ->
                return UploadFlow.Failure(SendMessageOutcome.Failure.Unauthorized)
        }
        val uploadResult = fileRepository.uploadBytes(
            uploadUrl = presignResult.uploadUrl,
            headers = presignResult.headers,
            bytes = bytes,
        )
        when (uploadResult) {
            UploadBytesOutcome.Success -> Unit
            is UploadBytesOutcome.Failure.Network ->
                return UploadFlow.Failure(
                    SendMessageOutcome.Failure.Network(uploadResult.cause),
                )
            is UploadBytesOutcome.Failure.Server ->
                return UploadFlow.Failure(
                    SendMessageOutcome.Failure.Server(uploadResult.code, uploadResult.message),
                )
            is UploadBytesOutcome.Failure.Unauthorized ->
                return UploadFlow.Failure(SendMessageOutcome.Failure.Unauthorized)
        }
        val confirm = fileRepository.confirm(
            fileId = presignResult.fileId,
            request = ConfirmUploadRequest(
                key = presignResult.key,
                mimeType = mimeType,
                sizeBytes = bytes.size.toLong(),
            ),
        )
        return when (confirm) {
            is ConfirmUploadOutcome.Success ->
                UploadFlow.Success(confirm.file.id)
            is ConfirmUploadOutcome.Failure.Network ->
                UploadFlow.Failure(SendMessageOutcome.Failure.Network(confirm.cause))
            is ConfirmUploadOutcome.Failure.Server ->
                UploadFlow.Failure(
                    SendMessageOutcome.Failure.Server(confirm.code, confirm.message),
                )
            is ConfirmUploadOutcome.Failure.Unauthorized ->
                UploadFlow.Failure(SendMessageOutcome.Failure.Unauthorized)
        }
    }

    private companion object {
        const val TAG = "ApiConversationRepo"
    }
}
