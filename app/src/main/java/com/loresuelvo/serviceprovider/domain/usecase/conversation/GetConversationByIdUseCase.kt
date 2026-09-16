package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import javax.inject.Inject

/**
 * Loads the full snapshot of a single provider ↔ consumer
 * conversation. The use case is a thin pass-through to
 * [ConversationRepository.getConversationById] — it exists so the
 * ViewModel depends on a one-method port whose name matches the
 * Gherkin step (`When el prestador abre la conversación 42`) and
 * so a future cross-cutting concern (caching, deduplication,
 * instrumentation) has a single seam to land on.
 *
 * Pure orchestration. No Android, no coroutine scope of its own.
 */
class GetConversationByIdUseCase @Inject constructor(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(
        conversationId: Int,
    ): ConversationDetailOutcome {
        require(conversationId > 0) { "conversationId must be positive" }
        return repository.getConversationById(conversationId)
    }
}
