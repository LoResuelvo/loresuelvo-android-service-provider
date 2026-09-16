package com.loresuelvo.serviceprovider.ui.screens.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender

/**
 * Single row the chat surface renders. Modelled as a sealed
 * hierarchy so the screen's `LazyColumn` can dispatch on the
 * delivery state and the VM has a single source of truth for
 * "what should be on the list right now".
 *
 *  - [ServerConfirmed] wraps a domain [ConversationMessage] that
 *    came back from the backend with a stable id. These bubbles
 *    never change identity across rotation.
 *  - [LocalPending] is an optimistic bubble the VM appended
 *    immediately on send / retry, before the server has
 *    acknowledged. The key is a local UUID so the VM can find
 *    and replace it once the round-trip completes.
 *  - [LocalFailed] is the same bubble after a failed round-trip.
 *    It carries [pendingPrompt] so the VM's retry handler can
 *    resubmit it without the user re-typing.
 *
 * Delivery state lives in the UI layer on purpose — the domain
 * [ConversationMessage] is a faithful mirror of the server's
 * truth and stays free of UI concerns.
 */
sealed interface ChatListItem {

    val key: String

    val sender: ConversationSender

    val content: String

    val createdOnEpochMillis: Long

    data class ServerConfirmed(
        val message: ConversationMessage,
    ) : ChatListItem {
        override val key: String get() = message.id.toString()
        override val sender: ConversationSender get() = message.sender
        override val content: String get() = message.content
        override val createdOnEpochMillis: Long get() = message.createdOnEpochMillis
    }

    data class LocalPending(
        override val key: String,
        override val sender: ConversationSender,
        override val content: String,
        override val createdOnEpochMillis: Long,
    ) : ChatListItem

    data class LocalFailed(
        override val key: String,
        override val sender: ConversationSender,
        override val content: String,
        override val createdOnEpochMillis: Long,
        val pendingPrompt: String,
    ) : ChatListItem
}
