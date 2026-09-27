package com.loresuelvo.serviceprovider.ui.turns

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.CompletionEligibility
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidencePreparer
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    private val evidence = FakeEvidence()

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
        viewModel.selectEvidence(listOf("private-photo"))
        advanceUntilIdle()
        orders.next = { WorkOrderDetailOutcome.Failure.Unauthorized }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(null, sessionStore.getSession())
        assertEquals(ProviderCompletionUiState.SessionExpired, viewModel.uiState.value)
        assertEquals("", viewModel.description.value)
        assertEquals(emptyList<CompletionEvidenceSelection>(), viewModel.evidence.value)
        assertEquals(listOf("private-photo"), evidence.cleaned)
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

    @Test fun selections_keep_order_identity_description_and_three_photo_limit() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Private delivery note")

        viewModel.selectEvidence(listOf("one", "two", "three", "four", "two"))
        advanceUntilIdle()
        assertEquals(listOf("one", "two", "three"), viewModel.evidence.value.map { it.source })
        assertEquals(EvidenceSelectionIssue.MaximumReached, viewModel.evidenceIssue.value)
        val firstId = viewModel.evidence.value[0].id
        val secondId = viewModel.evidence.value[1].id
        val thirdId = viewModel.evidence.value[2].id

        viewModel.removeEvidence(secondId)
        advanceUntilIdle()
        assertEquals(listOf(firstId, thirdId), viewModel.evidence.value.map { it.id })
        assertEquals(listOf("two"), evidence.cleaned)
        assertEquals("Private delivery note", viewModel.description.value)

        viewModel.selectEvidence(listOf("one", "four"))
        advanceUntilIdle()
        assertEquals(listOf("one", "three", "four"), viewModel.evidence.value.map { it.source })
        assertEquals(listOf("one", "two", "three", "four"), evidence.prepared)
    }

    @Test fun invalid_result_is_typed_and_does_not_clear_description() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        evidence.next = { EvidenceImagePreparation.Invalid.EmptyFile }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Private delivery note")

        viewModel.selectEvidence(listOf("empty"))
        advanceUntilIdle()
        assertEquals(EvidenceSelectionStatus.Invalid(EvidenceImagePreparation.Invalid.EmptyFile),
            viewModel.evidence.value.single().status)
        assertEquals("Private delivery note", viewModel.description.value)
    }

    @Test fun session_change_cleans_ready_files_and_ignores_late_result() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val late = CompletableDeferred<EvidenceImagePreparation>()
        evidence.next = { source -> if (source == "late") withContext(NonCancellable) { late.await() }
            else evidence.ready(source) }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.selectEvidence(listOf("ready", "late"))
        runCurrent()

        sessionStore.clearSession()
        late.complete(evidence.ready("late"))
        advanceUntilIdle()
        assertEquals(emptyList<CompletionEvidenceSelection>(), viewModel.evidence.value)
        assertEquals(listOf("ready", "late"), evidence.cleaned)
    }

    @Test fun retry_keeps_evidence_but_new_order_cleans_it() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(listOf("one"), viewModel.evidence.value.map { it.source })

        viewModel.open(selected.copy(id = 43))
        advanceUntilIdle()
        assertEquals(emptyList<CompletionEvidenceSelection>(), viewModel.evidence.value)
        assertEquals(listOf("one"), evidence.cleaned)
    }

    @Test fun removed_pending_selection_discards_and_cleans_late_result() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val late = CompletableDeferred<EvidenceImagePreparation>()
        evidence.next = { withContext(NonCancellable) { late.await() } }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.selectEvidence(listOf("late"))
        runCurrent()

        viewModel.removeEvidence(viewModel.evidence.value.single().id)
        late.complete(evidence.ready("late"))
        advanceUntilIdle()
        assertEquals(emptyList<CompletionEvidenceSelection>(), viewModel.evidence.value)
        assertEquals(listOf("late"), evidence.cleaned)
    }

    private fun viewModel() = ProviderCompletionViewModel(
        GetCompletionEligibilityUseCase(orders, object : CurrentAccountRepository {
            override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(
                CurrentAccount.Provider(7, "Juan", "Gómez", "juan@example.com", Category(1, "Plumbing"), null))
        }) { 1_000 }, sessionStore, evidence,
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

    private class FakeEvidence : CompletionEvidencePreparer {
        val prepared = mutableListOf<String>()
        val cleaned = mutableListOf<String>()
        var next: suspend (String) -> EvidenceImagePreparation = { ready(it) }
        fun ready(source: String) = EvidenceImagePreparation.Ready(
            PreparedEvidenceImage(source, "image/jpeg", 12, source),
        )
        override suspend fun prepare(source: String): EvidenceImagePreparation {
            prepared += source
            return next(source)
        }
        override suspend fun clean(image: PreparedEvidenceImage) { cleaned += image.localPath }
    }
}
