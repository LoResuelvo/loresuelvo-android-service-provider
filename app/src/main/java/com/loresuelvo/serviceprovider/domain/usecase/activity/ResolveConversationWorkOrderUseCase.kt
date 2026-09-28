package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import javax.inject.Inject

sealed interface ConversationWorkOrderResolution {
    data class Linked(val orderId: Int) : ConversationWorkOrderResolution
    data object Missing : ConversationWorkOrderResolution
    data object Unavailable : ConversationWorkOrderResolution
    data object SessionExpired : ConversationWorkOrderResolution
}

class ResolveConversationWorkOrderUseCase @Inject constructor(
    private val getProposals: GetServiceProposalsUseCase,
    private val getTurns: GetProviderTurnsUseCase,
) {
    suspend operator fun invoke(conversationId: Int): ConversationWorkOrderResolution {
        if (conversationId <= 0) return ConversationWorkOrderResolution.Missing
        val proposals = when (val result = getProposals()) {
            is ServiceProposalListOutcome.Success -> result.proposals
            ServiceProposalListOutcome.Failure.SessionExpired -> return ConversationWorkOrderResolution.SessionExpired
            is ServiceProposalListOutcome.Failure -> return ConversationWorkOrderResolution.Unavailable
        }
        val proposal = proposals.singleOrNull { it.conversationId == conversationId &&
            it.id > 0 && it.counterpart.id > 0 } ?: return ConversationWorkOrderResolution.Missing
        val orders = when (val result = getTurns()) {
            is ActivityLoadOutcome.Success -> result.items
            ActivityLoadOutcome.Failure.Unauthorized -> return ConversationWorkOrderResolution.SessionExpired
            is ActivityLoadOutcome.Failure -> return ConversationWorkOrderResolution.Unavailable
        }
        val order = orders.singleOrNull { it.id > 0 && it.serviceProposalId == proposal.id &&
            it.consumerId == proposal.counterpart.id } ?: return ConversationWorkOrderResolution.Missing
        return ConversationWorkOrderResolution.Linked(order.id)
    }
}
