package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveConversationWorkOrderUseCaseTest {
    private val proposal = ServiceProposalSummary(10, 70, 123456, 1, "Work", 60,
        ServiceProposalStatus.Accepted, 1,
        ServiceProposalCounterpart(3, "consumer", "Ana", "Pérez", null, null),
        ServiceProposalBookingTerms("ARS", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    private val order = WorkOrder(42, "Ana Pérez", "Work", 1, WorkOrderStatus.Scheduled,
        serviceProposalId = 10, consumerId = 3)

    @Test fun returns_only_unique_order_matching_conversation_proposal_and_consumer() = runTest {
        val source = Sources(listOf(proposal), listOf(order.copy(id = 71, consumerId = 99), order))
        assertEquals(ConversationWorkOrderResolution.Linked(42), source.resolve(70))
        assertEquals(1, source.proposalCalls)
        assertEquals(1, source.orderCalls)
    }

    @Test fun does_not_use_proposal_or_conversation_id_as_order_id() = runTest {
        val source = Sources(listOf(proposal), emptyList())
        assertEquals(ConversationWorkOrderResolution.Missing, source.resolve(70))
    }

    @Test fun ambiguous_order_link_does_not_choose_arbitrarily() = runTest {
        val source = Sources(listOf(proposal), listOf(order, order.copy(id = 43)))
        assertEquals(ConversationWorkOrderResolution.Missing, source.resolve(70))
    }

    @Test fun missing_or_ambiguous_proposal_skips_order_query() = runTest {
        val source = Sources(listOf(proposal, proposal.copy(id = 11)), listOf(order))
        assertEquals(ConversationWorkOrderResolution.Missing, source.resolve(70))
        assertEquals(0, source.orderCalls)
    }

    @Test fun order_query_failure_is_retryable_and_not_a_link() = runTest {
        val source = Sources(listOf(proposal), listOf(order))
        source.orderFailure = ActivityLoadOutcome.Failure.Network(Exception("offline"))
        assertEquals(ConversationWorkOrderResolution.Unavailable, source.resolve(70))
    }

    private class Sources(proposals: List<ServiceProposalSummary>, orders: List<WorkOrder>) {
        var proposalCalls = 0
        var orderCalls = 0
        var orderFailure: ActivityLoadOutcome.Failure? = null
        private val proposalRepository = object : ServiceProposalRepository {
            override suspend fun list(): ServiceProposalListOutcome {
                proposalCalls++
                return ServiceProposalListOutcome.Success(proposals)
            }
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
                error("Must not create")
        }
        private val orderRepository = object : WorkOrderRepository {
            override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> {
                orderCalls++
                return orderFailure ?: ActivityLoadOutcome.Success(orders)
            }
        }
        private val useCase = ResolveConversationWorkOrderUseCase(
            GetServiceProposalsUseCase(proposalRepository), GetProviderTurnsUseCase(orderRepository))
        suspend fun resolve(id: Int) = useCase(id)
    }
}
