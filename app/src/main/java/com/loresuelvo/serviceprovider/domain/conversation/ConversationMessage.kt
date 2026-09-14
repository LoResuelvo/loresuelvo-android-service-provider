package com.loresuelvo.serviceprovider.domain.conversation

data class ConversationMessage(
    val content: String,
    val kind: ConversationMessageKind,
    val createdOnEpochMillis: Long,
)

sealed interface ConversationMessageKind {
    data object Text : ConversationMessageKind
    data object Audio : ConversationMessageKind
    data object Video : ConversationMessageKind
}
