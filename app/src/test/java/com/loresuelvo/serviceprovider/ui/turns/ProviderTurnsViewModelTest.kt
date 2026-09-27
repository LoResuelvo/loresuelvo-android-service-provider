package com.loresuelvo.serviceprovider.ui.turns

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
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
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderTurnsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionStore = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "provider@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }

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
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(repository), GetServiceProposalsUseCase(proposals), sessionStore)
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
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(workOrders), GetServiceProposalsUseCase(proposals), sessionStore)
        advanceUntilIdle()

        assertEquals(mapOf(40 to 93), (viewModel.uiState.value as ProviderTurnsUiState.Ready).conversationIds)
        assertEquals(1, listCalls)
    }

    @Test fun retry_after_proposal_failure_resolves_conversation_without_reloading_orders() = runTest(dispatcher.scheduler) {
        val order = WorkOrder(40, "Ana", "Work", 1, WorkOrderStatus.Scheduled,
            serviceProposalId = 12, consumerId = 7)
        var orderCalls = 0
        val workOrders = object : WorkOrderRepository {
            override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> {
                orderCalls++
                return ActivityLoadOutcome.Success(listOf(order))
            }
        }
        var proposalCalls = 0
        val proposals = object : ServiceProposalRepository {
            override suspend fun list(): ServiceProposalListOutcome = if (++proposalCalls == 1)
                ServiceProposalListOutcome.Failure.Unavailable
            else ServiceProposalListOutcome.Success(listOf(proposal(12, 7, 93)))
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome = error("Must not create")
        }
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(workOrders), GetServiceProposalsUseCase(proposals), sessionStore)
        advanceUntilIdle()
        assertEquals(ServiceProposalListOutcome.Failure.Unavailable,
            (viewModel.uiState.value as ProviderTurnsUiState.Ready).proposalFailure)

        viewModel.retryConversation(40)
        viewModel.retryConversation(40)
        advanceUntilIdle()
        val ready = viewModel.uiState.value as ProviderTurnsUiState.Ready
        assertEquals(null, ready.proposalFailure)
        assertEquals(93, ready.conversationIds[40])
        assertEquals(93, ready.conversationToOpen)
        viewModel.conversationOpened()
        assertEquals(null, (viewModel.uiState.value as ProviderTurnsUiState.Ready).conversationToOpen)
        assertEquals(1, orderCalls)
        assertEquals(2, proposalCalls)
    }

    @Test fun returning_from_conversation_refreshes_order_without_clearing_visible_list() = runTest(dispatcher.scheduler) {
        var status: WorkOrderStatus = WorkOrderStatus.Scheduled
        var calls = 0
        val workOrders = object : WorkOrderRepository {
            override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> {
                calls++
                return ActivityLoadOutcome.Success(listOf(WorkOrder(40, "Ana", "Work", 1, status)))
            }
        }
        val proposals = object : ServiceProposalRepository {
            override suspend fun list() = ServiceProposalListOutcome.Success(emptyList())
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome = error("Not used")
        }
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(workOrders), GetServiceProposalsUseCase(proposals), sessionStore)
        advanceUntilIdle()
        viewModel.onResume()
        advanceUntilIdle()
        assertEquals(1, calls)

        status = WorkOrderStatus.AwaitingPayment
        viewModel.onResume()
        assertTrue(viewModel.uiState.value is ProviderTurnsUiState.Ready)
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(WorkOrderStatus.AwaitingPayment,
            (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.single().status)
    }

    @Test fun order_401_clears_session_and_visible_orders() = runTest(dispatcher.scheduler) {
        var unauthorized = false
        val repository = object : WorkOrderRepository {
            override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = if (unauthorized)
                ActivityLoadOutcome.Failure.Unauthorized
            else ActivityLoadOutcome.Success(listOf(WorkOrder(40, "Ana", "Work", 1, WorkOrderStatus.Scheduled)))
        }
        val proposals = object : ServiceProposalRepository {
            override suspend fun list() = ServiceProposalListOutcome.Success(listOf(proposal(12, 7, 93)))
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome = error("Not used")
        }
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(repository), GetServiceProposalsUseCase(proposals), sessionStore)
        advanceUntilIdle()
        unauthorized = true
        viewModel.load()
        advanceUntilIdle()
        assertEquals(null, sessionStore.getSession())
        assertEquals(ProviderTurnsUiState.Error, viewModel.uiState.value)
    }

    @Test fun initial_proposal_401_clears_session_and_order_links() = runTest(dispatcher.scheduler) {
        val repository = object : WorkOrderRepository {
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(listOf(
                WorkOrder(40, "Ana", "Work", 1, WorkOrderStatus.Scheduled, serviceProposalId = 12, consumerId = 7)))
        }
        val proposals = object : ServiceProposalRepository {
            override suspend fun list() = ServiceProposalListOutcome.Failure.SessionExpired
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome = error("Not used")
        }
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(repository), GetServiceProposalsUseCase(proposals), sessionStore)
        advanceUntilIdle()
        assertEquals(null, sessionStore.getSession())
        assertEquals(ProviderTurnsUiState.Error, viewModel.uiState.value)
    }

    @Test fun switching_sessions_purges_previous_orders_and_links() = runTest(dispatcher.scheduler) {
        val repository = object : WorkOrderRepository {
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(listOf(
                WorkOrder(40, "Ana", "Work", 1, WorkOrderStatus.Scheduled, serviceProposalId = 12, consumerId = 7)))
        }
        val proposals = object : ServiceProposalRepository {
            override suspend fun list() = ServiceProposalListOutcome.Success(listOf(proposal(12, 7, 93)))
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome = error("Not used")
        }
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(repository), GetServiceProposalsUseCase(proposals), sessionStore)
        advanceUntilIdle()
        assertEquals(93, (viewModel.uiState.value as ProviderTurnsUiState.Ready).conversationIds[40])
        sessionStore.saveSession(AuthSession(User("other", "other@example.com"), "other-token"))
        advanceUntilIdle()
        assertEquals(ProviderTurnsUiState.Error, viewModel.uiState.value)
    }

    private fun proposal(id: Int, consumerId: Int, conversationId: Int) = ServiceProposalSummary(
        id = id, conversationId = conversationId, amountCents = 100, scheduledOnEpochMillis = 1,
        description = "Work", estimatedDurationMinutes = 60, status = ServiceProposalStatus.Accepted,
        createdOnEpochMillis = 1,
        counterpart = ServiceProposalCounterpart(consumerId, "consumer", "Ana", "Pérez", null, null),
        bookingTerms = ServiceProposalBookingTerms("ARS", 100, 1, 99, 1, 1, 0, 2, 99, 101, 1),
    )
}
