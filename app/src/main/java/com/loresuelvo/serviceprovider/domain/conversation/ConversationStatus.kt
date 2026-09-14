package com.loresuelvo.serviceprovider.domain.conversation

sealed interface ConversationStatus {
    data object Pending : ConversationStatus
    data object Active : ConversationStatus
    data object Rejected : ConversationStatus
    data class Unsupported(val raw: String) : ConversationStatus
}
