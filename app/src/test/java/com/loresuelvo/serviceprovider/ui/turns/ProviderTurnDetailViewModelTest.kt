package com.loresuelvo.serviceprovider.ui.turns

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderWorkOrderDetailUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderTurnDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "p@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val detail = WorkOrderDetail(42, 10, 3, 7, 123456, 1000, "Current reason",
        WorkOrderStatus.Scheduled, null)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun loads_current_order_then_clears_private_state_on_session_change() = runTest(dispatcher.scheduler) {
        val orders = object : WorkOrderRepository {
            override suspend fun getWorkOrder(id: Int) = WorkOrderDetailOutcome.Success(detail)
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(listOf(
                WorkOrder(42, "Ana Pérez", "Old reason", 1, WorkOrderStatus.Paid,
                    serviceProposalId = 10, consumerId = 3)))
        }
        val model = ProviderTurnDetailViewModel(SavedStateHandle(mapOf("turnId" to 42)),
            GetProviderWorkOrderDetailUseCase(orders), session)
        assertEquals(ProviderTurnDetailUiState.Loading, model.uiState.value)
        advanceUntilIdle()
        val ready = model.uiState.value as ProviderTurnDetailUiState.Ready
        assertEquals(detail, (ready.result.detail as WorkOrderDetailOutcome.Success).order)
        assertEquals("Ana Pérez", ready.result.consumer?.consumerName)

        session.clearSession()
        advanceUntilIdle()
        assertEquals(ProviderTurnDetailUiState.Error(WorkOrderDetailOutcome.Failure.Unauthorized), model.uiState.value)
    }

    @Test fun duplicate_load_does_not_request_current_detail_twice() = runTest(dispatcher.scheduler) {
        val pending = CompletableDeferred<WorkOrderDetailOutcome>()
        var calls = 0
        val orders = object : WorkOrderRepository {
            override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome { calls++; return pending.await() }
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(emptyList<WorkOrder>())
        }
        val model = ProviderTurnDetailViewModel(SavedStateHandle(mapOf("turnId" to 42)),
            GetProviderWorkOrderDetailUseCase(orders), session)
        dispatcher.scheduler.runCurrent()
        model.load()
        assertEquals(1, calls)
        assertTrue(model.uiState.value is ProviderTurnDetailUiState.Loading)
        pending.complete(WorkOrderDetailOutcome.Success(detail))
        advanceUntilIdle()
        assertTrue(model.uiState.value is ProviderTurnDetailUiState.Ready)
    }
}
