package com.loresuelvo.serviceprovider.acceptance.turns

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidencePreparer
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidenceReader
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.domain.activity.PostCompletionReportOutcome
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.file.*
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetScheduledWorkUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.UploadCompletionEvidenceUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.ValidateCompletionReportDraftUseCase
import com.loresuelvo.serviceprovider.ui.screens.turns.ProviderCompletionRoute
import com.loresuelvo.serviceprovider.ui.screens.turns.ProviderTurnsScreen
import com.loresuelvo.serviceprovider.ui.screens.turns.onCompletionImagesPicked
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeViewModel
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionStatus
import com.loresuelvo.serviceprovider.ui.turns.EvidenceUploadStatus
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionDraftValidation
import com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionViewModel
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProviderCompletionAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val order = WorkOrder(42, "Ana Pérez", "Repair the kitchen tap", 0,
        WorkOrderStatus.Scheduled, serviceProposalId = 10, consumerId = 3)
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(
            AuthSession(User("provider", "provider@example.com"), "device-fixture"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val accounts = object : CurrentAccountRepository {
        override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(
            CurrentAccount.Provider(7, "Juan", "Gómez", "provider@example.com", Category(1, "Plumbing"), null))
    }
    private val orders = object : WorkOrderRepository {
        var status = WorkOrderStatus.Scheduled as WorkOrderStatus
        var reportId: Int? = null
        var postCalls = 0
        var postedFileIds = emptyList<String>()
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> =
            ActivityLoadOutcome.Success(listOf(order.copy(status = status)))
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome = WorkOrderDetailOutcome.Success(
            WorkOrderDetail(id, 10, 3, 7, 100, 0, order.description, status, reportId))
        override suspend fun postCompletionReport(orderId: Int, description: String,
            confirmedFileIds: List<String>): PostCompletionReportOutcome {
            postCalls++
            postedFileIds = confirmedFileIds
            status = WorkOrderStatus.AwaitingPayment
            reportId = 17
            return PostCompletionReportOutcome.Success(17)
        }
    }
    private val photos = object : CompletionEvidencePreparer {
        val files = mutableMapOf<String, File>()
        override suspend fun prepare(source: String): EvidenceImagePreparation {
            val file = File.createTempFile("completion_", ".jpg", compose.activity.cacheDir).apply {
                writeBytes(ByteArray(10))
            }
            files[source] = file
            return EvidenceImagePreparation.Ready(PreparedEvidenceImage(
                source.substringAfterLast('/'), "image/jpeg", 10, file.absolutePath))
        }
        override suspend fun isAvailable(image: PreparedEvidenceImage) =
            File(image.localPath).isFile && File(image.localPath).length() == image.sizeBytes
        override suspend fun clean(image: PreparedEvidenceImage) { File(image.localPath).delete() }
    }
    private val files = object : FileRepository {
        var presigns = 0
        override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome {
            presigns++
            return PresignUploadOutcome.Success(PresignUploadResult(
                "file-${request.originalName}", request.originalName, "https://storage.example/put", emptyMap()))
        }
        override suspend fun uploadBytes(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray) =
            UploadBytesOutcome.Success
        override suspend fun confirm(fileId: String, request: ConfirmUploadRequest) =
            ConfirmUploadOutcome.Success(ConfirmedFile(fileId, mimeType = request.mimeType,
                originalName = request.key))
    }
    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = ProviderCompletionViewModel(
        GetCompletionEligibilityUseCase(orders, accounts), session, photos,
        ValidateCompletionReportDraftUseCase(), UploadCompletionEvidenceUseCase(files,
            object : CompletionEvidenceReader {
                override suspend fun read(image: PreparedEvidenceImage) = File(image.localPath).readBytes()
            }), orders, handle)

    @Test fun summary_form_cancel_picker_and_success_return_keep_selected_order() {
        val model = viewModel()
        val home = ProviderHomeViewModel(GetPendingJobRequestsUseCase(object : JobRequestRepository {
            override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> =
                ActivityLoadOutcome.Success(emptyList())
            override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome = error("Unused")
        }), GetScheduledWorkUseCase(orders) { 0L })
        var form by mutableStateOf(false)
        var turns by mutableStateOf(ProviderTurnsUiState.Ready(listOf(order)))
        var pickerResult = emptyList<Uri>()
        compose.setContent {
            if (form) ProviderCompletionRoute(42, turns, onBack = { form = false },
                onRetryTurns = {}, viewModel = model,
                pickPhotos = { onCompletionImagesPicked(model, pickerResult) },
                onReportConfirmed = {
                    turns = ProviderTurnsUiState.Ready(listOf(order.copy(status = orders.status)))
                    home.retryScheduledWork()
                })
            else ProviderTurnsScreen(turns, {}, {}, initialSelectedId = 42,
                onCompletion = { form = true })
        }
        compose.waitUntil(5_000) { (home.uiState.value.scheduledWork as? ActivitySectionState.Ready)?.items?.size == 1 }
        val activity = compose.activity
        compose.onNodeWithTag("provider_turn_completion").performScrollTo().performClick()
        compose.onNodeWithTag("completion_description").performTextInput("Done")
        compose.onNodeWithText(activity.getString(R.string.provider_completion_add_photos)).performClick()
        assertEquals(0, files.presigns)
        assertEquals(0, orders.postCalls)
        compose.onNodeWithText(activity.getString(R.string.provider_completion_cancel)).performClick()
        compose.onNodeWithTag("provider_turn_completion").assertIsDisplayed()
        compose.onNodeWithTag("provider_turn_completion").performClick()
        compose.onNodeWithTag("completion_description").performTextInput("Done")
        pickerResult = listOf(Uri.parse("photo://one.jpg"), Uri.parse("photo://two.jpg"))
        compose.onNodeWithText(activity.getString(R.string.provider_completion_add_photos)).performClick()
        compose.waitUntil(5_000) { model.evidence.value.size == 2 &&
            model.evidence.value.all { it.uploadStatus is EvidenceUploadStatus.Confirmed } }
        assertEquals(listOf("one.jpg", "two.jpg"), model.evidence.value.map {
            (it.status as EvidenceSelectionStatus.Ready).image.originalName })
        compose.runOnIdle { model.removeEvidence(model.evidence.value[1].id) }
        compose.onNodeWithTag("completion_submit").performScrollTo().performClick()
        compose.waitUntil(5_000) { orders.postCalls == 1 &&
            (home.uiState.value.scheduledWork as? ActivitySectionState.Ready)?.items?.isEmpty() == true }
        assertEquals(listOf("file-one.jpg"), orders.postedFileIds)
        compose.onNodeWithText(activity.getString(R.string.provider_completion_report_success)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.provider_turns_back)).performClick()
        compose.onNodeWithTag("provider_turn_completion").assertIsDisplayed()
        assertEquals(WorkOrderStatus.AwaitingPayment, (turns.orders.single()).status)
        assertEquals(2, files.presigns)
    }

    @Test fun saved_state_bundle_restores_available_photo_and_flags_lost_photo() {
        val handle = SavedStateHandle()
        val original = viewModel(handle)
        original.open(order)
        compose.waitUntil(5_000) { original.uiState.value is com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionUiState.Ready }
        original.onDescriptionChange("Done")
        original.selectEvidence(listOf("photo://one.jpg", "photo://two.jpg"))
        compose.waitUntil(5_000) { original.evidence.value.size == 2 &&
            original.evidence.value.all { it.status is EvidenceSelectionStatus.Ready } }
        original.uploadEvidence(original.evidence.value[0].id)
        compose.waitUntil(5_000) { original.evidence.value[0].uploadStatus is EvidenceUploadStatus.Confirmed }
        val savedBundle = handle.savedStateProvider().saveState()
        photos.files.getValue("photo://two.jpg").delete()
        val recreated = viewModel(SavedStateHandle.createHandle(savedBundle, null))
        recreated.open(order)
        compose.waitUntil(5_000) { recreated.evidence.value.size == 2 }
        assertEquals("Done", recreated.description.value)
        assertEquals(EvidenceUploadStatus.Confirmed("file-one.jpg"), recreated.evidence.value[0].uploadStatus)
        assertEquals(EvidenceSelectionStatus.Invalid(EvidenceImagePreparation.Invalid.Unreadable),
            recreated.evidence.value[1].status)
        assertEquals(CompletionDraftValidation.Invalid.UnconfirmedPhoto, recreated.attemptSubmit())
        assertEquals(0, orders.postCalls)
        assertEquals(1, files.presigns)
        session.clearSession()
        compose.waitUntil(5_000) { recreated.description.value.isEmpty() }
        assertTrue(recreated.evidence.value.isEmpty())
    }

    @Test fun large_font_form_keeps_validation_and_actions_scrollable() {
        val model = viewModel()
        var backs = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                ProviderCompletionRoute(42, ProviderTurnsUiState.Ready(listOf(order)),
                    onBack = { backs++ }, onRetryTurns = {}, viewModel = model, pickPhotos = {})
            }
        }
        compose.onNodeWithTag("completion_description").performTextInput("Done")
        compose.onNodeWithTag("completion_submit").performScrollTo().performClick()
        compose.onNodeWithTag("completion_validation_issue").performScrollTo().assertIsDisplayed()
        assertEquals(0, orders.postCalls)
    }
}
