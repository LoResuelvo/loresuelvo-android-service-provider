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
import com.loresuelvo.serviceprovider.domain.activity.PostCompletionReportOutcome
import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.ValidateCompletionReportDraftUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionDraftValidation
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionEvidenceUpload
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionUploadFailure
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionUploadStage
import com.loresuelvo.serviceprovider.domain.usecase.activity.UploadCompletionEvidenceUseCase
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidenceReader
import com.loresuelvo.serviceprovider.domain.file.*
import com.loresuelvo.serviceprovider.testing.unavailableCompletionUploadUseCase
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
import org.junit.Assert.assertTrue

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

    @Test fun intentional_exit_discards_description_and_prepared_images() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Private delivery note")
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()

        viewModel.discardDraft()
        advanceUntilIdle()
        assertEquals(ProviderCompletionUiState.Closed, viewModel.uiState.value)
        assertEquals("", viewModel.description.value)
        assertEquals(emptyList<CompletionEvidenceSelection>(), viewModel.evidence.value)
        assertEquals(listOf("one"), evidence.cleaned)
    }

    @Test fun submit_attempt_blocks_incomplete_draft_without_losing_private_state() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        assertEquals(CompletionDraftValidation.Invalid.DescriptionRequired, viewModel.attemptSubmit())
        viewModel.onDescriptionChange("Done")
        assertEquals(CompletionDraftValidation.Invalid.PhotoRequired, viewModel.attemptSubmit())
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()
        assertEquals(CompletionDraftValidation.Invalid.UnconfirmedPhoto, viewModel.attemptSubmit())
        assertEquals("Done", viewModel.description.value)
        assertEquals(listOf("one"), viewModel.evidence.value.map { it.source })
        assertEquals(CompletionDraftValidation.Invalid.UnconfirmedPhoto, viewModel.validationIssue.value)
    }

    @Test fun submit_attempt_is_ignored_when_order_is_not_eligible() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Failure.Forbidden }
        val viewModel = viewModel()
        viewModel.open(selected)
        advanceUntilIdle()
        assertEquals(null, viewModel.attemptSubmit())
        assertEquals(null, viewModel.validationIssue.value)
    }

    @Test fun failed_second_upload_retries_in_place_without_reuploading_confirmed_first() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val files = UploadFiles()
        val viewModel = viewModel(files.useCase())
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Done")
        viewModel.selectEvidence(listOf("one", "two"))
        advanceUntilIdle()
        val ids = viewModel.evidence.value.map { it.id }

        viewModel.uploadEvidence(ids[0])
        advanceUntilIdle()
        files.failNextPresign = true
        viewModel.uploadEvidence(ids[1])
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), viewModel.evidence.value.map { it.source })
        assertEquals("Done", viewModel.description.value)
        assertEquals(EvidenceUploadStatus.Confirmed("file-one"), viewModel.evidence.value[0].uploadStatus)
        assertEquals(EvidenceUploadStatus.Failed(
            CompletionEvidenceUpload.Failure(CompletionUploadStage.PRESIGN, CompletionUploadFailure.SERVER, 503)),
            viewModel.evidence.value[1].uploadStatus)
        assertEquals(CompletionDraftValidation.Invalid.UnconfirmedPhoto, viewModel.attemptSubmit())

        viewModel.retryEvidence(ids[0])
        viewModel.retryEvidence(ids[1])
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), viewModel.evidence.value.map { it.source })
        assertEquals(listOf("file-one", "file-two"),
            (viewModel.attemptSubmit() as CompletionDraftValidation.Valid).confirmedFileIds)
        assertEquals(listOf("one", "two", "two"), files.presignedNames)
    }

    @Test fun duplicate_confirmed_ids_are_rejected_before_report_post() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val files = UploadFiles().apply { sameId = true }
        val viewModel = viewModel(files.useCase())
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Done")
        viewModel.selectEvidence(listOf("one", "two"))
        advanceUntilIdle()
        viewModel.evidence.value.map { it.id }.forEach { viewModel.uploadEvidence(it) }
        advanceUntilIdle()
        assertEquals(CompletionDraftValidation.Invalid.DuplicatePhotoIds, viewModel.attemptSubmit())
        assertEquals(CompletionDraftValidation.Invalid.DuplicatePhotoIds, viewModel.validationIssue.value)
    }

    @Test fun presign_unauthorized_expires_session_but_storage_403_remains_retryable() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val files = UploadFiles().apply { failNextTransfer = true }
        val viewModel = viewModel(files.useCase())
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()
        val id = viewModel.evidence.value.single().id
        viewModel.uploadEvidence(id)
        advanceUntilIdle()
        assertTrue(viewModel.evidence.value.single().uploadStatus is EvidenceUploadStatus.Failed)
        assertEquals(session, sessionStore.getSession())
        viewModel.retryEvidence(id)
        advanceUntilIdle()
        assertTrue(viewModel.evidence.value.single().uploadStatus is EvidenceUploadStatus.Confirmed)

        viewModel.selectEvidence(listOf("two"))
        advanceUntilIdle()
        files.unauthorizedNextPresign = true
        viewModel.uploadEvidence(viewModel.evidence.value.last().id)
        advanceUntilIdle()
        assertEquals(ProviderCompletionUiState.SessionExpired, viewModel.uiState.value)
        assertEquals(null, sessionStore.getSession())
        assertEquals(emptyList<CompletionEvidenceSelection>(), viewModel.evidence.value)
        assertEquals(listOf("one", "two"), evidence.cleaned)
    }

    @Test fun removed_upload_ignores_late_confirmation_and_cleans_private_file() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val files = UploadFiles().apply { lateConfirm = CompletableDeferred() }
        val viewModel = viewModel(files.useCase())
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()
        val id = viewModel.evidence.value.single().id
        viewModel.uploadEvidence(id)
        runCurrent()
        assertEquals(EvidenceUploadStatus.Uploading, viewModel.evidence.value.single().uploadStatus)

        viewModel.removeEvidence(id)
        files.lateConfirm!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(emptyList<CompletionEvidenceSelection>(), viewModel.evidence.value)
        assertEquals(listOf("one"), evidence.cleaned)
    }

    @Test fun double_confirm_sends_one_snapshot_and_locks_success_when_refresh_fails() = runTest(dispatcher.scheduler) {
        orders.next = { if (orders.queriedIds.size >= 3) WorkOrderDetailOutcome.Failure.Network(Exception("offline"))
            else WorkOrderDetailOutcome.Success(detail) }
        val pending = CompletableDeferred<PostCompletionReportOutcome>()
        orders.postNext = { pending.await() }
        val handle = SavedStateHandle()
        val viewModel = viewModel(UploadFiles().useCase(), handle)
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("  Done  ")
        viewModel.selectEvidence(listOf("one", "two"))
        advanceUntilIdle()
        viewModel.evidence.value.map { it.id }.forEach { viewModel.uploadEvidence(it) }
        advanceUntilIdle()

        viewModel.confirmCompletion()
        viewModel.confirmCompletion()
        viewModel.onDescriptionChange("changed")
        viewModel.removeEvidence(viewModel.evidence.value.first().id)
        runCurrent()
        assertEquals(1, orders.postCalls)
        assertEquals("Done", orders.postedDescription)
        assertEquals(listOf("file-one", "file-two"), orders.postedFileIds)
        assertEquals("  Done  ", viewModel.description.value)
        assertEquals(2, viewModel.evidence.value.size)

        pending.complete(PostCompletionReportOutcome.Success(17))
        advanceUntilIdle()
        assertEquals(CompletionSubmissionState.Confirmed(17, false), viewModel.submission.value)
        assertEquals(1, orders.postCalls)
        assertEquals(null, viewModel.attemptSubmit())
        assertEquals(42, handle.get<Int>("completion_pending_order_id"))

        orders.next = { WorkOrderDetailOutcome.Success(detail.copy(
            status = WorkOrderStatus.AwaitingPayment, completionReportId = 17)) }
        val recreated = viewModel(UploadFiles().useCase(), handle)
        recreated.open(selected)
        advanceUntilIdle()
        assertEquals(CompletionSubmissionState.Confirmed(17, true), recreated.submission.value)
        assertEquals(null, handle.get<Int>("completion_pending_order_id"))
        assertEquals(1, orders.postCalls)
    }

    @Test fun uncertain_result_allows_only_get_retry_before_another_explicit_post() = runTest(dispatcher.scheduler) {
        orders.next = { when (orders.queriedIds.size) {
            3 -> WorkOrderDetailOutcome.Failure.Network(Exception("offline"))
            else -> WorkOrderDetailOutcome.Success(detail)
        } }
        orders.postNext = { if (orders.postCalls == 1) PostCompletionReportOutcome.Uncertain.Network
            else PostCompletionReportOutcome.Success(18) }
        val viewModel = viewModel(UploadFiles().useCase())
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Done")
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()
        viewModel.uploadEvidence(viewModel.evidence.value.single().id)
        advanceUntilIdle()

        viewModel.confirmCompletion()
        advanceUntilIdle()
        assertEquals(CompletionSubmissionState.QueryFailed, viewModel.submission.value)
        viewModel.confirmCompletion()
        assertEquals(1, orders.postCalls)

        viewModel.retryReconciliation()
        advanceUntilIdle()
        assertEquals(CompletionSubmissionState.Idle, viewModel.submission.value)
        assertEquals(1, orders.postCalls)
        viewModel.confirmCompletion()
        advanceUntilIdle()
        assertEquals(2, orders.postCalls)
    }

    @Test fun uncertain_post_stays_locked_when_awaiting_payment_has_no_verified_report() = runTest(dispatcher.scheduler) {
        val awaitingWithoutReport = detail.copy(status = WorkOrderStatus.AwaitingPayment)
        orders.next = { when (orders.queriedIds.size) {
            3, 5, 6 -> WorkOrderDetailOutcome.Success(awaitingWithoutReport)
            4 -> WorkOrderDetailOutcome.Failure.Network(Exception("offline"))
            else -> WorkOrderDetailOutcome.Success(detail)
        } }
        orders.postNext = { PostCompletionReportOutcome.Uncertain.Network }
        val handle = SavedStateHandle()
        val viewModel = viewModel(UploadFiles().useCase(), handle)
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Done")
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()
        viewModel.uploadEvidence(viewModel.evidence.value.single().id)
        advanceUntilIdle()

        viewModel.confirmCompletion()
        advanceUntilIdle()
        assertEquals(CompletionSubmissionState.QueryFailed, viewModel.submission.value)
        assertEquals(42, handle.get<Int>("completion_pending_order_id"))
        assertEquals(null, viewModel.refreshedOrderStatus.value)
        viewModel.confirmCompletion()
        assertEquals(1, orders.postCalls)

        viewModel.retryReconciliation()
        advanceUntilIdle()
        assertEquals(CompletionSubmissionState.QueryFailed, viewModel.submission.value)
        assertEquals(42, handle.get<Int>("completion_pending_order_id"))
        viewModel.confirmCompletion()
        assertEquals(1, orders.postCalls)

        orders.next = { WorkOrderDetailOutcome.Success(awaitingWithoutReport.copy(completionReportId = 17)) }
        viewModel.retryReconciliation()
        advanceUntilIdle()
        assertEquals(CompletionSubmissionState.Confirmed(null, true), viewModel.submission.value)
        assertEquals(WorkOrderStatus.AwaitingPayment, viewModel.refreshedOrderStatus.value)
        assertEquals(null, handle.get<Int>("completion_pending_order_id"))
        assertEquals(1, orders.postCalls)
    }

    @Test fun recreated_viewmodel_with_pending_marker_reconciles_before_post() = runTest(dispatcher.scheduler) {
        val handle = SavedStateHandle(mapOf(
            "completion_pending_order_id" to 42,
            "completion_pending_owner_id" to "provider",
        ))
        orders.next = { WorkOrderDetailOutcome.Failure.Network(Exception("offline")) }
        val viewModel = viewModel(UploadFiles().useCase(), handle)
        viewModel.open(selected.copy(id = 43))
        assertEquals(CompletionSubmissionState.Blocked(CompletionEligibility.ChangedOrder), viewModel.submission.value)
        assertEquals(0, orders.queriedIds.size)
        viewModel.open(selected)
        advanceUntilIdle()
        assertEquals(CompletionSubmissionState.QueryFailed, viewModel.submission.value)
        assertEquals(0, orders.postCalls)
        viewModel.confirmCompletion()
        assertEquals(0, orders.postCalls)
    }

    @Test fun leaving_after_post_starts_keeps_marker_for_reconciliation() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val pending = CompletableDeferred<PostCompletionReportOutcome>()
        orders.postNext = { pending.await() }
        val handle = SavedStateHandle()
        val viewModel = viewModel(UploadFiles().useCase(), handle)
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Done")
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()
        viewModel.uploadEvidence(viewModel.evidence.value.single().id)
        advanceUntilIdle()
        viewModel.confirmCompletion()
        runCurrent()
        assertEquals(1, orders.postCalls)

        viewModel.discardDraft()
        advanceUntilIdle()
        assertEquals(42, handle.get<Int>("completion_pending_order_id"))
        val recreated = viewModel(UploadFiles().useCase(), handle)
        recreated.open(selected)
        advanceUntilIdle()
        assertEquals(1, orders.postCalls)
        assertEquals(CompletionSubmissionState.Idle, recreated.submission.value)
    }

    @Test fun fresh_preflight_rejects_changed_order_before_post() = runTest(dispatcher.scheduler) {
        orders.next = { if (orders.queriedIds.size == 1) WorkOrderDetailOutcome.Success(detail)
            else WorkOrderDetailOutcome.Success(detail.copy(status = WorkOrderStatus.AwaitingPayment,
                completionReportId = 17)) }
        val viewModel = viewModel(UploadFiles().useCase())
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Done")
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()
        viewModel.uploadEvidence(viewModel.evidence.value.single().id)
        advanceUntilIdle()
        viewModel.confirmCompletion()
        advanceUntilIdle()
        assertEquals(0, orders.postCalls)
        assertEquals(ProviderCompletionUiState.Ready(selected, CompletionEligibility.AlreadyReported), viewModel.uiState.value)
    }

    @Test fun session_change_discards_late_post_result_and_private_state() = runTest(dispatcher.scheduler) {
        orders.next = { WorkOrderDetailOutcome.Success(detail) }
        val late = CompletableDeferred<PostCompletionReportOutcome>()
        orders.postNext = { withContext(NonCancellable) { late.await() } }
        val handle = SavedStateHandle()
        val viewModel = viewModel(UploadFiles().useCase(), handle)
        viewModel.open(selected)
        advanceUntilIdle()
        viewModel.onDescriptionChange("Private note")
        viewModel.selectEvidence(listOf("one"))
        advanceUntilIdle()
        viewModel.uploadEvidence(viewModel.evidence.value.single().id)
        advanceUntilIdle()
        viewModel.confirmCompletion()
        runCurrent()
        assertEquals(1, orders.postCalls)

        sessionStore.clearSession()
        late.complete(PostCompletionReportOutcome.Success(17))
        advanceUntilIdle()
        assertEquals(ProviderCompletionUiState.SessionExpired, viewModel.uiState.value)
        assertEquals("", viewModel.description.value)
        assertEquals(emptyList<CompletionEvidenceSelection>(), viewModel.evidence.value)
        assertEquals(42, handle.get<Int>("completion_pending_order_id"))
        assertEquals(listOf("one"), evidence.cleaned)
    }

    private fun viewModel(uploader: UploadCompletionEvidenceUseCase = unavailableCompletionUploadUseCase(),
        handle: SavedStateHandle = SavedStateHandle()) = ProviderCompletionViewModel(
        GetCompletionEligibilityUseCase(orders, object : CurrentAccountRepository {
            override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(
                CurrentAccount.Provider(7, "Juan", "Gómez", "juan@example.com", Category(1, "Plumbing"), null))
        }) { 1_000 }, sessionStore, evidence, ValidateCompletionReportDraftUseCase(), uploader,
        orders, handle,
    )

    private class UploadFiles : FileRepository {
        val presignedNames = mutableListOf<String>()
        var failNextPresign = false
        var failNextTransfer = false
        var unauthorizedNextPresign = false
        var sameId = false
        var lateConfirm: CompletableDeferred<Unit>? = null
        fun useCase() = UploadCompletionEvidenceUseCase(this, object : CompletionEvidenceReader {
            override suspend fun read(image: PreparedEvidenceImage) = ByteArray(image.sizeBytes.toInt())
        })
        override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome {
            presignedNames += request.originalName
            if (unauthorizedNextPresign) {
                unauthorizedNextPresign = false
                return PresignUploadOutcome.Failure.Unauthorized("private")
            }
            if (failNextPresign) {
                failNextPresign = false
                return PresignUploadOutcome.Failure.Server(503, "private")
            }
            val id = if (sameId) "shared" else "file-${request.originalName}"
            return PresignUploadOutcome.Success(PresignUploadResult(id, request.originalName, "https://storage.example/put", emptyMap()))
        }
        override suspend fun uploadBytes(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray): UploadBytesOutcome {
            if (failNextTransfer) {
                failNextTransfer = false
                return UploadBytesOutcome.Failure.Server(403, "private")
            }
            return UploadBytesOutcome.Success
        }
        override suspend fun confirm(fileId: String, request: ConfirmUploadRequest): ConfirmUploadOutcome {
            lateConfirm?.let { withContext(NonCancellable) { it.await() } }
            return ConfirmUploadOutcome.Success(ConfirmedFile(fileId, mimeType = request.mimeType, originalName = request.key))
        }
    }

    private inner class FakeSession : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(session)
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }

    private class FakeOrders : WorkOrderRepository {
        var next: suspend () -> WorkOrderDetailOutcome = { error("No detail response") }
        val queriedIds = mutableListOf<Int>()
        var postCalls = 0
        var postedDescription: String? = null
        var postedFileIds: List<String>? = null
        var postNext: suspend () -> PostCompletionReportOutcome = { error("No post response") }
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = error("List must not be queried")
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            queriedIds += id
            return next()
        }
        override suspend fun postCompletionReport(orderId: Int, description: String,
            confirmedFileIds: List<String>): PostCompletionReportOutcome {
            assertEquals(42, orderId)
            postCalls++
            postedDescription = description
            postedFileIds = confirmedFileIds
            return postNext()
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
