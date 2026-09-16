package com.loresuelvo.serviceprovider.domain.conversation

/**
 * Full snapshot of a single conversation, as returned by
 * `GET /conversations/{id}`. Mirrors the shape of the
 * inbox-summary [Conversation] but adds the complete ordered
 * `messages` history so the chat surface can render the entire
 * thread without a second round-trip per bubble.
 *
 * The inbox-summary type and this detail type are deliberately
 * modelled as separate entities rather than a single class with an
 * optional `messages` field — the wire contracts differ enough
 * (list emits `last_message`, detail emits `messages[]`) and
 * conflating them would force every list call to carry an empty
 * list (semantically noisy) or every detail call to discard the
 * full history (data loss).
 *
 *  - [id] matches the backend-issued conversation id (the same
 *    value `POST /job-requests/{id}/accept` returned).
 *  - [status] / [counterpart] / [updatedOnEpochMillis] are
 *    field-for-field identical to [Conversation].
 *  - [messages] is the full ordered thread (oldest first).
 *
 * Pure domain.
 */
data class ConversationDetail(
    val id: Int,
    val status: ConversationStatus,
    val counterpart: ConversationCounterpart,
    val messages: List<ConversationMessage>,
    val updatedOnEpochMillis: Long,
)
