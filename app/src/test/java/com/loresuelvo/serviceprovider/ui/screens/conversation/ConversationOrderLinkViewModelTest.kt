package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.ResolveConversationWorkOrderUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationOrderLinkViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "p@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val proposal = ServiceProposalSummary(10, 70, 1, 1, "Work", 60,
        ServiceProposalStatus.Accepted, 1,
        ServiceProposalCounterpart(3, "consumer", "Ana", "Pérez", null, null),
        ServiceProposalBookingTerms("ARS", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
    private val order = WorkOrder(42, "Ana Pérez", "Work", 1, WorkOrderStatus.Scheduled,
        serviceProposalId = 10, consumerId = 3)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun resolves_real_order_id_and_drops_link_on_session_change() = runTest(dispatcher.scheduler) {
        val model = model { ActivityLoadOutcome.Success(listOf(order)) }
        advanceUntilIdle()
        assertEquals(ConversationOrderLinkUiState.Linked(42), model.uiState.value)

        session.clearSession()
        advanceUntilIdle()
        assertEquals(ConversationOrderLinkUiState.Missing, model.uiState.value)
    }

    @Test fun retry_recovers_failed_order_lookup_without_leaving_chat() = runTest(dispatcher.scheduler) {
        var calls = 0
        val model = model {
            if (++calls == 1) ActivityLoadOutcome.Failure.Network(Exception("offline"))
            else ActivityLoadOutcome.Success(listOf(order))
        }
        advanceUntilIdle()
        assertEquals(ConversationOrderLinkUiState.Error, model.uiState.value)
        model.load()
        advanceUntilIdle()
        assertEquals(ConversationOrderLinkUiState.Linked(42), model.uiState.value)
    }

    @Test fun late_result_from_old_session_cannot_restore_link() = runTest(dispatcher.scheduler) {
        val pending = CompletableDeferred<ActivityLoadOutcome<WorkOrder>>()
        val model = model { pending.await() }
        dispatcher.scheduler.runCurrent()
        session.clearSession()
        pending.complete(ActivityLoadOutcome.Success(listOf(order)))
        advanceUntilIdle()
        assertEquals(ConversationOrderLinkUiState.Missing, model.uiState.value)
    }

    private fun model(next: suspend () -> ActivityLoadOutcome<WorkOrder>): ConversationOrderLinkViewModel {
        val proposals = object : ServiceProposalRepository {
            override suspend fun list() = ServiceProposalListOutcome.Success(listOf(proposal))
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome = error("Not used")
        }
        val orders = object : WorkOrderRepository {
            override suspend fun getWorkOrders() = next()
        }
        return ConversationOrderLinkViewModel(SavedStateHandle(mapOf("conversationId" to 70)),
            ResolveConversationWorkOrderUseCase(GetServiceProposalsUseCase(proposals), GetProviderTurnsUseCase(orders)),
            session)
    }
}
