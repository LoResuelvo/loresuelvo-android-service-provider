package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.SendMessageRequestDto
import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

@Singleton
class ApiConversationRepository @Inject constructor(
    private val backendApi: BackendApi,
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
}
