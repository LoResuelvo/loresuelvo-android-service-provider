package com.loresuelvo.serviceprovider.domain.conversation

/** Server identity is scoped by the caller's conversation; server time and ID give stable ordering. */
fun mergeConversationMessages(
    existing: List<ConversationMessage>,
    incoming: List<ConversationMessage>,
): List<ConversationMessage> = (existing + incoming).associateBy(ConversationMessage::id).values
    .sortedWith(compareBy(ConversationMessage::createdOnEpochMillis, ConversationMessage::id))
