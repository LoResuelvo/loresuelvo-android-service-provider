package com.loresuelvo.serviceprovider.ui.turns

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderCompletionImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderCompletionReport
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderWorkOrderDetailUseCase
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
    private val proposals = GetServiceProposalsUseCase(object : ServiceProposalRepository {
        override suspend fun list() = ServiceProposalListOutcome.Success(emptyList())
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
            error("Not used")
    })

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
            GetProviderWorkOrderDetailUseCase(orders), proposals, session)
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
            GetProviderWorkOrderDetailUseCase(orders), proposals, session)
        dispatcher.scheduler.runCurrent()
        model.load()
        assertEquals(1, calls)
        assertTrue(model.uiState.value is ProviderTurnDetailUiState.Loading)
        pending.complete(WorkOrderDetailOutcome.Success(detail))
        advanceUntilIdle()
        assertTrue(model.uiState.value is ProviderTurnDetailUiState.Ready)
    }

    @Test fun saves_only_selected_file_id_and_uses_refreshed_url_after_requery() = runTest(dispatcher.scheduler) {
        val saved = SavedStateHandle(mapOf("turnId" to 42))
        var url = "https://storage.test/old"
        val orders = object : WorkOrderRepository {
            override suspend fun getWorkOrder(id: Int) = WorkOrderDetailOutcome.Success(detail.copy(
                status = WorkOrderStatus.Paid, completionReportId = 17,
                completionReport = WorkOrderCompletionReport(17, "Done", 1000,
                    listOf(WorkOrderCompletionImage("file-2", "two.jpg", url)))))
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(emptyList<WorkOrder>())
        }
        val model = ProviderTurnDetailViewModel(saved, GetProviderWorkOrderDetailUseCase(orders), proposals, session)
        advanceUntilIdle()
        model.selectFile("unknown")
        assertEquals(null, model.selectedFileId.value)
        model.selectFile("file-2")
        assertEquals("file-2", saved.get<String>("selectedFileId"))
        url = "https://storage.test/fresh"
        model.load()
        advanceUntilIdle()
        assertEquals("file-2", model.selectedFileId.value)
        val current = ((model.uiState.value as ProviderTurnDetailUiState.Ready).result.detail
            as WorkOrderDetailOutcome.Success).order
        assertEquals(url, current.completionReport?.images?.single()?.url)
        model.closeViewer()
        assertEquals(null, saved.get<String>("selectedFileId"))
    }

    @Test fun restores_selected_file_id_only_after_current_detail_arrives() = runTest(dispatcher.scheduler) {
        val saved = SavedStateHandle(mapOf("turnId" to 42, "selectedFileId" to "file-2"))
        val pending = CompletableDeferred<WorkOrderDetailOutcome>()
        val orders = object : WorkOrderRepository {
            override suspend fun getWorkOrder(id: Int) = pending.await()
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(emptyList<WorkOrder>())
        }
        val model = ProviderTurnDetailViewModel(saved, GetProviderWorkOrderDetailUseCase(orders), proposals, session)
        dispatcher.scheduler.runCurrent()
        assertEquals(ProviderTurnDetailUiState.Loading, model.uiState.value)
        pending.complete(WorkOrderDetailOutcome.Success(detail.copy(status = WorkOrderStatus.Paid,
            completionReportId = 17, completionReport = WorkOrderCompletionReport(17, "Done", 1000,
                listOf(WorkOrderCompletionImage("file-2", "two.jpg", "https://storage.test/fresh"))))))
        advanceUntilIdle()
        assertEquals("file-2", model.selectedFileId.value)
        assertTrue(model.uiState.value is ProviderTurnDetailUiState.Ready)
        session.clearSession()
        advanceUntilIdle()
        assertEquals(null, model.selectedFileId.value)
    }

    @Test fun photo_retry_keeps_other_evidence_available_and_uses_fresh_url() = runTest(dispatcher.scheduler) {
        val oldImages = (1..3).map { WorkOrderCompletionImage("file-$it", "$it.jpg", "https://storage.test/old-$it") }
        val old = detail.copy(status = WorkOrderStatus.Paid, completionReportId = 17,
            completionReport = WorkOrderCompletionReport(17, "Done", 1000, oldImages))
        val pending = CompletableDeferred<WorkOrderDetailOutcome>()
        var calls = 0
        val orders = object : WorkOrderRepository {
            override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
                calls++
                return if (calls == 1) WorkOrderDetailOutcome.Success(old) else pending.await()
            }
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(emptyList<WorkOrder>())
        }
        val model = ProviderTurnDetailViewModel(SavedStateHandle(mapOf("turnId" to 42)),
            GetProviderWorkOrderDetailUseCase(orders), proposals, session)
        advanceUntilIdle()
        model.selectFile("file-2")
        model.retryPhoto("file-2")
        dispatcher.scheduler.runCurrent()
        assertEquals(2, calls)
        assertEquals(old, ((model.uiState.value as ProviderTurnDetailUiState.Ready).result.detail
            as WorkOrderDetailOutcome.Success).order)

        val fresh = old.copy(completionReport = old.completionReport?.copy(images = oldImages.map {
            it.copy(url = it.url.replace("old", "fresh"))
        }))
        pending.complete(WorkOrderDetailOutcome.Success(fresh))
        advanceUntilIdle()
        val current = ((model.uiState.value as ProviderTurnDetailUiState.Ready).result.detail
            as WorkOrderDetailOutcome.Success).order
        assertEquals("file-2", model.selectedFileId.value)
        assertEquals(listOf("file-1", "file-2", "file-3"), current.completionReport?.images?.map { it.fileId })
        assertEquals("https://storage.test/fresh-2", current.completionReport?.images?.get(1)?.url)
        assertEquals("Done", current.completionReport?.description)
    }
}
