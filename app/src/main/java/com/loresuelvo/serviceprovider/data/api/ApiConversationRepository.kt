package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

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
}
