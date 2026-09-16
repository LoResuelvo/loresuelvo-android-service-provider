package com.loresuelvo.serviceprovider.domain.conversation

/**
 * Single message in a provider ↔ consumer conversation.
 *
 *  - [id] is the backend-issued numeric id. The inbox summary
 *    surfaces it implicitly (only the counterpart preview shows),
 *    while the chat detail uses it as the `LazyColumn` key so each
 *    bubble stays stable across rotation and process death.
 *  - [sender] discriminates between the provider's bubble and the
 *    consumer's bubble. The chat surface renders each variant on a
 *    different side of the screen; the inbox summary uses it to
 *    prepend a "Vos:" or "Consumidor:" prefix when the API exposes
 *    it (currently the inbox shows only the raw [content]).
 *  - [content] is the message body in plain text. Empty when the
 *    message is media-only (placeholder for upcoming image / audio
 *    USes — text-only US-A always sends non-empty [content]).
 *  - [createdOnEpochMillis] is the backend's `created_on` parsed to
 *    epoch millis (UTC). `Long` (not `java.time.Instant`) because
 *    `minSdk = 24`.
 *
 * This type carries **no** delivery state. Pending / failed bubbles
 * that the UI shows during an optimistic send live in the
 * `ui.conversation.ChatListItem` wrapper, not here — domain
 * entities stay a faithful mirror of the server's truth.
 *
 * Pure domain.
 */
data class ConversationMessage(
    val id: Int,
    val sender: ConversationSender,
    val content: String,
    val createdOnEpochMillis: Long,
    val kind: ConversationMessageKind = ConversationMessageKind.Text,
)

sealed interface ConversationMessageKind {
    data object Text : ConversationMessageKind
    data object Audio : ConversationMessageKind
    data object Video : ConversationMessageKind
}
