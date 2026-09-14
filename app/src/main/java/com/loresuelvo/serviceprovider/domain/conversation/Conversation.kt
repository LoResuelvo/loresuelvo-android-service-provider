package com.loresuelvo.serviceprovider.domain.conversation

data class Conversation(
    val id: Int,
    val status: ConversationStatus,
    val counterpart: ConversationCounterpart,
    val lastMessage: ConversationMessage?,
    val updatedOnEpochMillis: Long,
)
