package com.loresuelvo.serviceprovider.domain.conversation

/**
 * Identifies who produced a [ConversationMessage] inside a provider
 * ↔ consumer chat. Modelled as a sealed interface (rather than an
 * enum) so future subtypes can carry their own state without
 * touching consumers — e.g. a `System` sender for "Provider
 * accepted the conversation" events surfaced by the backend.
 *
 * Pure domain.
 */
sealed interface ConversationSender {
    data object Provider : ConversationSender
    data object Consumer : ConversationSender
}
