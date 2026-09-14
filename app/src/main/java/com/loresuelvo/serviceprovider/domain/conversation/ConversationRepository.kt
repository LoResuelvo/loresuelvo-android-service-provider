package com.loresuelvo.serviceprovider.domain.conversation

interface ConversationRepository {
    suspend fun getConversations(): ConversationsOutcome
}
