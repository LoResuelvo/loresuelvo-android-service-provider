package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetConversationsUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(): ConversationsOutcome =
        conversationRepository.getConversations()
}
