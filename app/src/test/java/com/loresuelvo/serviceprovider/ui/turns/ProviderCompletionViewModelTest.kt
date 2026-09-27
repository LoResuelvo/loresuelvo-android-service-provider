package com.loresuelvo.serviceprovider.ui.turns

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.CompletionEligibility
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderCompletionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val selected = WorkOrder(42, "Ana Pérez", "Repair", 1_000, WorkOrderStatus.Scheduled,
        serviceProposalId = 10, consumerId = 3)
    private val detail = WorkOrderDetail(42, 10, 3, 7, 100, 1_000, "Repair", WorkOrderStatus.Scheduled, null)
    private val session = AuthSession(User("provider", "provider@example.com"), "token")
    private val sessionStore = FakeSession()
    private val orders = FakeOrders()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun opens_selected_order_with_checking_then_fresh_eligibility() = runTest(dispatcher.scheduler) {
        val pending = CompletableDeferred<WorkOrderDetailOutcome>()
        orders.next = { pending.await() }
        val viewModel = viewModel()

        viewModel.open(selected)
        runCurrent()
        assertEquals(ProviderCompletionUiState.Checking(selected), viewModel.uiState.value)

        pending.complete(WorkOrderDetailOutcome.Success(detail))
        advanceUntilIdle()
        assertEquals(ProviderCompletionUiState.Ready(selected, CompletionEligibility.Eligible), viewModel.uiState.value)
        assertEquals(listOf(42), orders.queriedIds)
    }

    @Test fun retry_rechecks_same_order_after_network_failure() = runTest(dispatcher.scheduler) {
        var attempt = 0
        orders.next = { if (++attempt == 1) WorkOrderDetailOutcome.Failure.Network(Exception("offline"))
            else WorkOrderDetailOutcome.Success(detail) }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        assertEquals(CompletionEligibility.Failure.Network::class,
            (viewModel.uiState.value as ProviderCompletionUiState.Ready).eligibility::class)

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(ProviderCompletionUiState.Ready(selected, CompletionEligibility.Eligible), viewModel.uiState.value)
        assertEquals(listOf(42, 42), orders.queriedIds)
    }

    @Test fun forbidden_and_missing_orders_keep_distinct_outcomes() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel()
        orders.next = { WorkOrderDetailOutcome.Failure.Forbidden }
        viewModel.open(selected)
        advanceUntilIdle()
        assertEquals(ProviderCompletionUiState.Ready(selected, CompletionEligibility.Forbidden), viewModel.uiState.value)

        orders.next = { WorkOrderDetailOutcome.Failure.NotFound }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(ProviderCompletionUiState.Ready(selected, CompletionEligibility.Failure.NotFound), viewModel.uiState.value)
    }

    @Test fun unauthorized_clears_session_and_private_order() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Private delivery note")
        orders.next = { WorkOrderDetailOutcome.Failure.Unauthorized }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(null, sessionStore.getSession())
        assertEquals(ProviderCompletionUiState.SessionExpired, viewModel.uiState.value)
        assertEquals("", viewModel.description.value)
    }

    @Test fun draft_survives_retry_but_clears_on_new_order_and_session_change() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Private delivery note")

        orders.next = { WorkOrderDetailOutcome.Failure.Network(Exception("offline")) }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals("Private delivery note", viewModel.description.value)

        viewModel.open(selected.copy(id = 43))
        assertEquals("", viewModel.description.value)
        sessionStore.clearSession()
        advanceUntilIdle()
        assertEquals("", viewModel.description.value)
    }

    @Test fun session_change_clears_an_existing_private_draft() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Private delivery note")

        sessionStore.clearSession()
        advanceUntilIdle()
        assertEquals("", viewModel.description.value)
        assertEquals(ProviderCompletionUiState.SessionExpired, viewModel.uiState.value)
    }

    @Test fun session_change_drops_in_flight_result() = runTest(dispatcher.scheduler) {
        val pending = CompletableDeferred<WorkOrderDetailOutcome>()
        orders.next = { pending.await() }
        val viewModel = viewModel()
        viewModel.open(selected)
        runCurrent()

        sessionStore.clearSession()
        pending.complete(WorkOrderDetailOutcome.Success(detail))
        advanceUntilIdle()
        assertEquals(ProviderCompletionUiState.SessionExpired, viewModel.uiState.value)
    }

    private fun viewModel() = ProviderCompletionViewModel(
        GetCompletionEligibilityUseCase(orders, object : CurrentAccountRepository {
            override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(
                CurrentAccount.Provider(7, "Juan", "Gómez", "juan@example.com", Category(1, "Plumbing"), null))
        }) { 1_000 }, sessionStore,
    )

    private inner class FakeSession : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(session)
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }

    private class FakeOrders : WorkOrderRepository {
        var next: suspend () -> WorkOrderDetailOutcome = { error("No detail response") }
        val queriedIds = mutableListOf<Int>()
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = error("List must not be queried")
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            queriedIds += id
            return next()
        }
    }
}
