package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import javax.inject.Inject

/**
 * Sends a provider-typed text message to an existing conversation.
 * Single seam the ViewModel uses to fire `POST
 * /conversations/{id}/messages` — future attachment flows (image,
 * audio) land on the same port via parallel use cases rather than
 * overloading this one.
 *
 *  - Trims the [content] and rejects blank payloads with a typed
 *    outcome so the chat surface can keep the send button disabled
 *    without having to mirror the rule.
 *  - Forwards a non-blank payload verbatim to the backend. The
 *    server's `created_on` and `id` are surfaced back through
 *    [SendMessageOutcome.Success.message] so the ViewModel can
 *    replace the optimistic bubble.
 *
 * Pure orchestration. No Android, no coroutine scope of its own.
 */
class SendMessageUseCase @Inject constructor(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(
        conversationId: Int,
        content: String,
    ): SendMessageOutcome {
        require(conversationId > 0) { "conversationId must be positive" }
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "Message content must not be blank" }
        return repository.sendMessage(conversationId, trimmed)
    }
}
