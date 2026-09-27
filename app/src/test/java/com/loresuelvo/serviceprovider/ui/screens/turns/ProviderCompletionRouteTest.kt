package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.lifecycle.SavedStateHandle

import com.loresuelvo.serviceprovider.testing.unavailableCompletionUploadUseCase

import android.net.Uri
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidencePreparer
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.activity.PostCompletionReportOutcome
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidenceReader
import com.loresuelvo.serviceprovider.domain.file.*
import com.loresuelvo.serviceprovider.domain.usecase.activity.UploadCompletionEvidenceUseCase
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.ValidateCompletionReportDraftUseCase
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionViewModel
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionIssue
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR")
class ProviderCompletionRouteTest {
    @get:Rule val compose = createComposeRule()
    private val order = WorkOrder(42, "Ana Pérez", "Reparar la canilla", 1_000,
        WorkOrderStatus.Scheduled, serviceProposalId = 10, consumerId = 3)
    private var result: WorkOrderDetailOutcome = WorkOrderDetailOutcome.Success(
        WorkOrderDetail(42, 10, 3, 7, 100, 1_000, order.description, WorkOrderStatus.Scheduled, null))
    private var postCalls = 0
    private val orders = object : WorkOrderRepository {
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = error("No list query")
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            assertEquals(42, id)
            return result
        }
        override suspend fun postCompletionReport(orderId: Int, description: String,
            confirmedFileIds: List<String>): PostCompletionReportOutcome {
            assertEquals(42, orderId)
            assertEquals("Done", description)
            assertEquals(listOf("file-first.jpg"), confirmedFileIds)
            postCalls++
            result = WorkOrderDetailOutcome.Success(WorkOrderDetail(42, 10, 3, 7, 100, 1_000,
                order.description, WorkOrderStatus.AwaitingPayment, 17))
            return PostCompletionReportOutcome.Success(17)
        }
    }
    private val accounts = object : CurrentAccountRepository {
        override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(
            CurrentAccount.Provider(7, "Juan", "Gómez", "juan@example.com", Category(1, "Plumbing"), null))
    }
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "provider@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val cleaned = mutableListOf<String>()
    private val evidencePort = object : CompletionEvidencePreparer {
        override suspend fun prepare(source: String): EvidenceImagePreparation = EvidenceImagePreparation.Ready(
            PreparedEvidenceImage(source.substringAfterLast('/'), "image/jpeg", 100, source))
        override suspend fun clean(image: PreparedEvidenceImage) { cleaned += image.localPath }
    }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun fresh_owned_order_shows_form_and_returns_to_summary() {
        var backs = 0
        showRoute { backs++ }
        compose.onNodeWithText("Ana Pérez").assertExists()
        compose.onNodeWithText("Reparar la canilla").assertExists()
        compose.onNodeWithText("Descripción de la entrega").assertExists()
        compose.onNodeWithTag("completion_submit").assertExists()
        compose.onNodeWithText("Volver").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun fresh_forbidden_response_never_shows_form() {
        result = WorkOrderDetailOutcome.Failure.Forbidden
        showRoute {}
        compose.onNodeWithText("No tenés permiso para informar la finalización de esta orden.").assertExists()
        compose.onNodeWithTag("completion_submit").assertDoesNotExist()
    }

    @Test fun missing_order_shows_safe_message_without_form() {
        result = WorkOrderDetailOutcome.Failure.NotFound
        showRoute {}
        compose.onNodeWithText("No encontramos esta orden.").assertExists()
        compose.onNodeWithTag("completion_submit").assertDoesNotExist()
    }

    @Test fun query_failure_retries_the_same_order() {
        result = WorkOrderDetailOutcome.Failure.Network(IOException("offline"))
        showRoute {}
        compose.onNodeWithText("No pudimos consultar esta orden.").assertExists()
        result = WorkOrderDetailOutcome.Success(
            WorkOrderDetail(42, 10, 3, 7, 100, 1_000, order.description, WorkOrderStatus.Scheduled, null))
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Descripción de la entrega").assertExists()
    }

    @Test fun route_draft_is_removed_when_session_ends() {
        val viewModel = showRoute {}
        compose.onNodeWithTag("completion_description").performTextInput("Private delivery note")
        compose.runOnIdle { assertEquals("Private delivery note", viewModel.description.value) }

        compose.runOnIdle { session.clearSession() }
        compose.onNodeWithTag("completion_description").assertDoesNotExist()
        compose.runOnIdle { assertEquals("", viewModel.description.value) }
    }

    @Test fun picker_callback_keeps_empty_result_and_enforces_app_limit() {
        var pickerCalls = 0
        val viewModel = showRoute(pickPhotos = { pickerCalls++ }) {}
        compose.onNodeWithText("Agregar fotos").performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(1, pickerCalls) }
        compose.runOnIdle {
            onCompletionImagesPicked(viewModel, emptyList())
            assertEquals(0, viewModel.evidence.value.size)
            onCompletionImagesPicked(viewModel, listOf("first.jpg", "second.jpg", "third.jpg", "fourth.jpg")
                .map { Uri.parse("photo://$it") })
        }
        compose.onNodeWithContentDescription("Vista previa de la foto 1").assertExists()
        compose.onNodeWithText("No podés agregar más de 3 fotos.").assertExists()
        compose.runOnIdle {
            assertEquals(3, viewModel.evidence.value.size)
            assertEquals(EvidenceSelectionIssue.MaximumReached, viewModel.evidenceIssue.value)
        }
    }

    @Test fun cancel_discards_private_draft_and_returns_to_summary() {
        var backs = 0
        val viewModel = showRoute { backs++ }
        compose.onNodeWithTag("completion_description").performTextInput("Private delivery note")
        compose.runOnIdle { onCompletionImagesPicked(viewModel, listOf(Uri.parse("photo://first.jpg"))) }
        compose.onNodeWithContentDescription("Vista previa de la foto 1").assertExists()

        compose.onNodeWithText("Cancelar").performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals("", viewModel.description.value)
            assertEquals(0, viewModel.evidence.value.size)
            assertEquals(listOf("photo://first.jpg"), cleaned)
        }
    }

    @Test fun back_discards_prepared_photo_before_returning() {
        var backs = 0
        val viewModel = showRoute { backs++ }
        compose.runOnIdle { onCompletionImagesPicked(viewModel, listOf(Uri.parse("photo://back.jpg"))) }
        compose.onNodeWithContentDescription("Vista previa de la foto 1").assertExists()

        compose.onNodeWithText("Volver").performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(0, viewModel.evidence.value.size)
            assertEquals(listOf("photo://back.jpg"), cleaned)
        }
    }

    @Test fun confirm_attempt_shows_localized_error_and_preserves_draft() {
        val viewModel = showRoute {}
        compose.onNodeWithTag("completion_description").performTextInput("Done")
        compose.runOnIdle { onCompletionImagesPicked(viewModel, listOf(Uri.parse("photo://first.jpg"))) }
        compose.onNodeWithText("No se pudo preparar la subida.").assertExists()
        compose.onNodeWithTag("completion_retry_1").assertExists()
        compose.onNodeWithText("Confirmar finalización").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Esperá a que se confirme la fotografía antes de enviar.").assertExists()
        compose.runOnIdle {
            assertEquals("Done", viewModel.description.value)
            assertEquals(1, viewModel.evidence.value.size)
        }
    }

    @Test fun confirmed_report_refreshes_from_server_and_disables_second_submission() {
        var refreshes = 0
        val filePort = object : FileRepository {
            override suspend fun presign(request: PresignUploadRequest) = PresignUploadOutcome.Success(
                PresignUploadResult("file-${request.originalName}", "private/${request.originalName}",
                    "https://storage.example/file", emptyMap()))
            override suspend fun uploadBytes(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray) =
                UploadBytesOutcome.Success
            override suspend fun confirm(fileId: String, request: ConfirmUploadRequest) =
                ConfirmUploadOutcome.Success(ConfirmedFile(fileId, mimeType = request.mimeType, originalName = "first.jpg"))
        }
        val uploader = UploadCompletionEvidenceUseCase(filePort, object : CompletionEvidenceReader {
            override suspend fun read(image: PreparedEvidenceImage) = ByteArray(image.sizeBytes.toInt())
        })
        val viewModel = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { 1_000 },
            session, evidencePort, ValidateCompletionReportDraftUseCase(), uploader, orders, SavedStateHandle())
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionRoute(42, ProviderTurnsUiState.Ready(listOf(order)), {}, {}, viewModel,
                onReportConfirmed = { refreshes++ })
        } }
        compose.onNodeWithTag("completion_description").performTextInput("Done")
        compose.runOnIdle { onCompletionImagesPicked(viewModel, listOf(Uri.parse("photo://first.jpg"))) }
        compose.onNodeWithText("Foto confirmada").assertExists()
        compose.runOnIdle { assertEquals(com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionDraftValidation.Valid(
            "Done", listOf("file-first.jpg")), viewModel.attemptSubmit()) }
        compose.onNodeWithTag("completion_submit").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { postCalls == 1 }
        compose.runOnIdle {
            assertEquals("submission=${viewModel.submission.value}, state=${viewModel.uiState.value}, validation=${viewModel.validationIssue.value}", 1, postCalls)
            assertEquals(com.loresuelvo.serviceprovider.ui.turns.CompletionSubmissionState.Confirmed(17, true),
                viewModel.submission.value)
            assertEquals(WorkOrderStatus.AwaitingPayment, viewModel.refreshedOrderStatus.value)
        }
        compose.onNodeWithText("Pendiente de pago").assertExists()
        compose.onNodeWithTag("completion_submit").assertDoesNotExist()
        compose.runOnIdle {
            viewModel.confirmCompletion()
            assertEquals(1, postCalls)
            assertEquals(1, refreshes)
        }
    }

    private fun showRoute(pickPhotos: (() -> Unit)? = null, onBack: () -> Unit): ProviderCompletionViewModel {
        val viewModel = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { 1_000 }, session,
            evidencePort, ValidateCompletionReportDraftUseCase(), unavailableCompletionUploadUseCase(),
            orders, SavedStateHandle())
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionRoute(42, ProviderTurnsUiState.Ready(listOf(order)), onBack, {}, viewModel, pickPhotos)
        } }
        return viewModel
    }
}
