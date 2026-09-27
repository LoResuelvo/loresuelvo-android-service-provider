package com.loresuelvo.serviceprovider.ui.turns

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderTurnsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun loads_all_work_orders_without_home_status_filter() = runTest(dispatcher.scheduler) {
        val orders = listOf(
            WorkOrder(1, "Ana", "First", 1, WorkOrderStatus.Scheduled),
            WorkOrder(2, "Bea", "Second", 2, WorkOrderStatus.Paid),
        )
        val repository = object : WorkOrderRepository {
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(orders)
        }

        val proposals = object : ServiceProposalRepository {
            override suspend fun list() = ServiceProposalListOutcome.Success(emptyList())
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
                error("Not used by turns")
        }
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(repository), GetServiceProposalsUseCase(proposals))
        assertEquals(ProviderTurnsUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(orders, (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders)
        assertTrue((viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.any { it.status is WorkOrderStatus.Paid })
    }

    @Test fun resolves_only_matching_proposal_and_consumer_from_one_snapshot() = runTest(dispatcher.scheduler) {
        val order = WorkOrder(40, "Ana", "Work", 1, WorkOrderStatus.Scheduled,
            serviceProposalId = 12, consumerId = 7)
        val workOrders = object : WorkOrderRepository {
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(listOf(order))
        }
        var listCalls = 0
        val proposals = object : ServiceProposalRepository {
            override suspend fun list(): ServiceProposalListOutcome {
                listCalls++
                return ServiceProposalListOutcome.Success(listOf(
                    proposal(40, 7, 40), proposal(12, 40, 12), proposal(12, 7, 93)))
            }
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
                error("Must not create a proposal")
        }
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(workOrders), GetServiceProposalsUseCase(proposals))
        advanceUntilIdle()

        assertEquals(mapOf(40 to 93), (viewModel.uiState.value as ProviderTurnsUiState.Ready).conversationIds)
        assertEquals(1, listCalls)
    }

    private fun proposal(id: Int, consumerId: Int, conversationId: Int) = ServiceProposalSummary(
        id = id, conversationId = conversationId, amountCents = 100, scheduledOnEpochMillis = 1,
        description = "Work", estimatedDurationMinutes = 60, status = ServiceProposalStatus.Accepted,
        createdOnEpochMillis = 1,
        counterpart = ServiceProposalCounterpart(consumerId, "consumer", "Ana", "Pérez", null, null),
        bookingTerms = ServiceProposalBookingTerms("ARS", 100, 1, 99, 1, 1, 0, 2, 99, 101, 1),
    )
}
