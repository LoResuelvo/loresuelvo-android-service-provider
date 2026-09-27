package com.loresuelvo.serviceprovider.bdd.workorder

import androidx.lifecycle.SavedStateHandle

import com.loresuelvo.serviceprovider.testing.unavailableCompletionUploadUseCase
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidenceReader
import com.loresuelvo.serviceprovider.domain.file.*
import com.loresuelvo.serviceprovider.domain.usecase.activity.UploadCompletionEvidenceUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionUploadStage
import com.loresuelvo.serviceprovider.ui.turns.EvidenceUploadStatus

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
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.ValidateCompletionReportDraftUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionDraftValidation
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetScheduledWorkUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestsUseCase
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeViewModel
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.turns.CompletionSubmissionState
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionViewModel
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionIssue
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionStatus
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsViewModel
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.cucumber.java.en.Then
import io.cucumber.java.en.And
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReportProviderWorkCompletionSteps {
    private val dispatcher = StandardTestDispatcher()
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "provider@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val selected = WorkOrder(42, "Ana Pérez", "Reparar la canilla y verificar la instalación",
        1_000, WorkOrderStatus.Scheduled, serviceProposalId = 10, consumerId = 3)
    private var detail = WorkOrderDetail(42, 10, 3, 7, 100, 1_000, selected.description,
        WorkOrderStatus.Scheduled, null)
    private var now = 1_000L
    private var detailCalls = 0
    private var listedOrder = selected
    private val submittedReports = mutableListOf<Triple<Int, String, List<String>>>()
    private var rejectedReport: PostCompletionReportOutcome.Rejected? = null
    private var conflictDetail: WorkOrderDetail? = null
    private var uncertainReport = false
    private val reconciliationResult = CompletableDeferred<String>()
    private val completionHandle = SavedStateHandle()
    private val orders = object : WorkOrderRepository {
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = ActivityLoadOutcome.Success(listOf(listedOrder))
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            assertEquals(selected.id, id)
            detailCalls++
            if (uncertainReport && submittedReports.isNotEmpty()) return when (reconciliationResult.await()) {
                "el reporte ya está registrado" -> WorkOrderDetailOutcome.Success(
                    detail.copy(status = WorkOrderStatus.AwaitingPayment, completionReportId = 17))
                "sigue habilitada y no tiene reporte" -> WorkOrderDetailOutcome.Success(detail)
                "no se pudo consultar el estado" -> WorkOrderDetailOutcome.Failure.Network(Exception("offline"))
                else -> error("Unapproved reconciliation result")
            }
            return WorkOrderDetailOutcome.Success(detail)
        }
        override suspend fun postCompletionReport(orderId: Int, description: String,
            confirmedFileIds: List<String>): PostCompletionReportOutcome {
            submittedReports += Triple(orderId, description, confirmedFileIds)
            if (uncertainReport) return PostCompletionReportOutcome.Uncertain.Network
            rejectedReport?.let { rejection ->
                conflictDetail?.let { detail = it }
                return rejection
            }
            detail = detail.copy(status = WorkOrderStatus.AwaitingPayment, completionReportId = 17)
            listedOrder = selected.copy(status = WorkOrderStatus.AwaitingPayment)
            return PostCompletionReportOutcome.Success(17)
        }
    }
    private val accounts = object : CurrentAccountRepository {
        override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(
            CurrentAccount.Provider(7, "Juan", "Gómez", "juan@example.com", Category(1, "Plomería"), null))
    }
    private val proposals = object : ServiceProposalRepository {
        override suspend fun list() = ServiceProposalListOutcome.Success(listOf(ServiceProposalSummary(
            10, 93, 100, 1_000, selected.description, 60, ServiceProposalStatus.Accepted, 1,
            ServiceProposalCounterpart(3, "consumer", "Ana", "Pérez", null, null),
            ServiceProposalBookingTerms("ARS", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        )))
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
            error("Completion must not create proposals")
    }
    private lateinit var turns: ProviderTurnsViewModel
    private lateinit var completion: ProviderCompletionViewModel
    private lateinit var home: ProviderHomeViewModel
    private lateinit var initialDetail: WorkOrderDetail
    private var expectedPhotos = emptyList<String>()
    private var photoAction = ""
    private val validateDraft = ValidateCompletionReportDraftUseCase()
    private var draftProblem = ""
    private var expectedDescription = ""
    private var attemptedValidation: CompletionDraftValidation? = null
    private var failedUploadStage = CompletionUploadStage.PRESIGN
    private var failurePending = false
    private var duplicateFileIds = false
    private val presignCalls = mutableListOf<String>()
    private val completionFiles = object : FileRepository {
        override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome {
            assertEquals(FilePurpose.WORK_ORDER_COMPLETION_IMAGE, request.purpose)
            presignCalls += request.originalName
            if (request.originalName == "second.jpg" && failurePending && failedUploadStage == CompletionUploadStage.PRESIGN) {
                failurePending = false
                return PresignUploadOutcome.Failure.Server(503, "private")
            }
            return PresignUploadOutcome.Success(PresignUploadResult(if (duplicateFileIds) "confirmed-file-1" else "file-${request.originalName}",
                "private/${request.originalName}", "https://storage.example/${request.originalName}", emptyMap()))
        }
        override suspend fun uploadBytes(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray): UploadBytesOutcome {
            if (uploadUrl.endsWith("second.jpg") && failurePending && failedUploadStage == CompletionUploadStage.TRANSFER) {
                failurePending = false
                return UploadBytesOutcome.Failure.Server(403, "private")
            }
            return UploadBytesOutcome.Success
        }
        override suspend fun confirm(fileId: String, request: ConfirmUploadRequest): ConfirmUploadOutcome {
            if (fileId == "file-second.jpg" && failurePending && failedUploadStage == CompletionUploadStage.CONFIRM) {
                failurePending = false
                return ConfirmUploadOutcome.Failure.Server(503, "private")
            }
            return ConfirmUploadOutcome.Success(ConfirmedFile(fileId,
                mimeType = request.mimeType, originalName = request.key.substringAfterLast('/')))
        }
    }
    private val completionUploader = UploadCompletionEvidenceUseCase(completionFiles, object : CompletionEvidenceReader {
        override suspend fun read(image: PreparedEvidenceImage): ByteArray? = ByteArray(image.sizeBytes.toInt())
    })
    private val evidencePreparer = object : CompletionEvidencePreparer {
        override suspend fun prepare(source: String): EvidenceImagePreparation = when (source) {
            "photo://empty.jpg" -> EvidenceImagePreparation.Invalid.EmptyFile
            "photo://unsupported.gif" -> EvidenceImagePreparation.Invalid.UnsupportedFormat
            "photo://oversize.jpg" -> EvidenceImagePreparation.Invalid.ExceedsMaxSize
            "photo://inaccessible.jpg" -> EvidenceImagePreparation.Invalid.Unreadable
            else -> EvidenceImagePreparation.Ready(PreparedEvidenceImage(
                source.substringAfterLast('/'), when {
                    source.endsWith(".png") -> "image/png"
                    source.endsWith(".webp") -> "image/webp"
                    else -> "image/jpeg"
                }, 100, source))
        }
        override suspend fun clean(image: PreparedEvidenceImage) = Unit
    }

    @Given("que inicié sesión como prestador")
    fun signedIn() { Dispatchers.setMain(dispatcher) }

    @Given("que consulto el resumen de un turno real de Ana Pérez desde Turnos")
    fun selectedTurnSummary() = runTest(dispatcher.scheduler) {
        turns = ProviderTurnsViewModel(GetProviderTurnsUseCase(orders), GetServiceProposalsUseCase(proposals), session)
        advanceUntilIdle()
        assertEquals(selected, (turns.uiState.value as ProviderTurnsUiState.Ready).orders.single())
    }

    @And("la consulta vigente de esa orden indica {string}")
    fun currentDetailSituation(situation: String) {
        detail = when (situation) {
            "soy el asignado, está scheduled y aún no llegó el turno" -> { now = 999; detail }
            "soy el asignado, está scheduled y llegó la hora del turno" -> detail
            "soy el asignado, está scheduled y el turno ya pasó" -> { now = 1_001; detail }
            "la orden ya está awaiting_payment con reporte" ->
                detail.copy(status = WorkOrderStatus.AwaitingPayment, completionReportId = 17)
            "la orden ya está paid con reporte" ->
                detail.copy(status = WorkOrderStatus.Paid, completionReportId = 17)
            "no soy el prestador asignado" -> detail.copy(providerId = 99)
            else -> error("Unapproved situation: $situation")
        }
        initialDetail = detail
    }

    @When("intento abrir Informar finalización")
    fun openCompletion() = runTest(dispatcher.scheduler) {
        completion = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { now }, session,
            object : CompletionEvidencePreparer {
                override suspend fun prepare(source: String): EvidenceImagePreparation = error("No photo selected in 01-PIF")
                override suspend fun clean(image: PreparedEvidenceImage) = Unit
            }, validateDraft, unavailableCompletionUploadUseCase(), orders, SavedStateHandle())
        completion.open((turns.uiState.value as ProviderTurnsUiState.Ready).orders.single())
        advanceUntilIdle()
    }

    @Then("veo {string} para esa misma orden")
    fun seeCurrentResult(result: String) {
        val state = completion.uiState.value as ProviderCompletionUiState.Ready
        assertEquals(selected.id, state.order.id)
        assertEquals(when (result) {
            "una explicación de que todavía no se puede" -> CompletionEligibility.TooEarly
            "el formulario con consumidor y trabajo" -> CompletionEligibility.Eligible
            "el estado vigente sin ofrecer otro reporte" -> CompletionEligibility.AlreadyReported
            "un aviso de falta de permisos" -> CompletionEligibility.Forbidden
            else -> error("Unapproved result: $result")
        }, state.eligibility)
        if (state.eligibility == CompletionEligibility.Eligible) {
            assertEquals("Ana Pérez", state.order.consumerName)
            assertEquals(initialDetail.description, state.order.description)
        }
    }

    @And("conservo el acceso al motivo completo y a su conversación desde el resumen")
    fun summaryKeepsReasonAndConversation() {
        val state = turns.uiState.value as ProviderTurnsUiState.Ready
        assertEquals(selected.description, state.orders.single().description)
        assertEquals(93, state.conversationIds[selected.id])
    }

    @And("no se registra ninguna finalización")
    fun noCompletionRecorded() {
        assertEquals(1, detailCalls)
        assertEquals(initialDetail, detail)
    }

    @Given("que escribí la descripción de entrega")
    fun wroteCompletionDescription() = runTest(dispatcher.scheduler) {
        completion = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { now },
            session, evidencePreparer, validateDraft, unavailableCompletionUploadUseCase(), orders, SavedStateHandle())
        completion.open(selected)
        advanceUntilIdle()
        assertEquals(CompletionEligibility.Eligible,
            (completion.uiState.value as ProviderCompletionUiState.Ready).eligibility)
        completion.onDescriptionChange("Trabajo terminado y revisado")
    }

    @And("el formulario contiene {string}")
    fun initialPhotos(selection: String) = runTest(dispatcher.scheduler) {
        expectedPhotos = when (selection) {
            "ninguna foto" -> emptyList()
            "una foto válida" -> listOf("first.jpg")
            "tres fotos válidas" -> listOf("first.jpg", "second.jpg", "third.jpg")
            else -> error("Unapproved initial selection: $selection")
        }
        completion.selectEvidence(expectedPhotos.map { "photo://$it" })
        advanceUntilIdle()
    }

    @When("realizo {string} sobre las fotografías")
    fun changePhotos(action: String) = runTest(dispatcher.scheduler) {
        photoAction = action
        when (action) {
            "seleccionar una foto JPEG válida" -> {
                completion.selectEvidence(listOf("photo://first.jpg"))
                expectedPhotos = listOf("first.jpg")
            }
            "seleccionar una foto PNG y una WebP válidas" -> {
                completion.selectEvidence(listOf("photo://second.png", "photo://third.webp"))
                expectedPhotos += listOf("second.png", "third.webp")
            }
            "quitar la segunda foto" -> {
                completion.removeEvidence(completion.evidence.value[1].id)
                expectedPhotos = listOf("first.jpg", "third.jpg")
            }
            "intentar agregar una cuarta foto" -> completion.selectEvidence(listOf("photo://fourth.jpg"))
            "seleccionar un archivo vacío" -> completion.selectEvidence(listOf("photo://empty.jpg"))
            "seleccionar un formato no admitido" -> completion.selectEvidence(listOf("photo://unsupported.gif"))
            "seleccionar una foto mayor a 5 MiB" -> completion.selectEvidence(listOf("photo://oversize.jpg"))
            "seleccionar un archivo inaccesible" -> completion.selectEvidence(listOf("photo://inaccessible.jpg"))
            else -> error("Unapproved photo action: $action")
        }
        advanceUntilIdle()
    }

    @Then("veo {string}")
    fun seePhotoResult(result: String) {
        val photos = completion.evidence.value
        val ready = photos.mapNotNull { (it.status as? EvidenceSelectionStatus.Ready)?.image }
        when (result) {
            "la vista previa de la foto" -> assertEquals(listOf("first.jpg"), ready.map { it.originalName })
            "las tres vistas previas en orden de selección" -> {
                assertEquals(listOf("first.jpg", "second.png", "third.webp"), ready.map { it.originalName })
                assertEquals(listOf("image/jpeg", "image/png", "image/webp"), ready.map { it.mimeType })
            }
            "sólo la primera y la tercera foto" -> assertEquals(listOf("first.jpg", "third.jpg"), ready.map { it.originalName })
            "un aviso de máximo tres fotos" -> {
                assertEquals(EvidenceSelectionIssue.MaximumReached, completion.evidenceIssue.value)
                assertEquals(3, ready.size)
            }
            "un aviso de archivo inválido" -> assertEquals(EvidenceImagePreparation.Invalid.EmptyFile,
                (photos.last().status as EvidenceSelectionStatus.Invalid).reason)
            "un aviso de formato no admitido" -> assertEquals(EvidenceImagePreparation.Invalid.UnsupportedFormat,
                (photos.last().status as EvidenceSelectionStatus.Invalid).reason)
            "un aviso de tamaño excedido" -> assertEquals(EvidenceImagePreparation.Invalid.ExceedsMaxSize,
                (photos.last().status as EvidenceSelectionStatus.Invalid).reason)
            "un aviso para seleccionar otra foto" -> assertEquals(EvidenceImagePreparation.Invalid.Unreadable,
                (photos.last().status as EvidenceSelectionStatus.Invalid).reason)
            else -> {
                if (uncertainReport) seeReconciliationRecovery(result) else seeRejectedRecovery(result)
                return
            }
        }
    }

    @And("conservo la descripción y el orden relativo de las fotografías restantes")
    fun keepsDescriptionAndPhotoOrder() {
        assertEquals("Trabajo terminado y revisado", completion.description.value)
        assertEquals(expectedPhotos, completion.evidence.value.mapNotNull {
            (it.status as? EvidenceSelectionStatus.Ready)?.image?.originalName
        })
        assertTrue(photoAction.isNotEmpty())
        assertEquals(1, detailCalls)
    }

    @Given("que abrí el formulario de una orden habilitada")
    fun openedEligibleForm() = runTest(dispatcher.scheduler) {
        completion = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { now },
            session, evidencePreparer, validateDraft, completionUploader, orders, SavedStateHandle())
        completion.open(selected)
        advanceUntilIdle()
        assertEquals(CompletionEligibility.Eligible,
            (completion.uiState.value as ProviderCompletionUiState.Ready).eligibility)
    }

    @And("el borrador tiene {string}")
    fun draftHasProblem(problem: String) = runTest(dispatcher.scheduler) {
        draftProblem = problem
        expectedDescription = when (problem) {
            "descripción vacía" -> ""
            "descripción formada sólo por espacios" -> " \n  "
            else -> "Trabajo terminado"
        }
        completion.onDescriptionChange(expectedDescription)
        val sources = when (problem) {
            "descripción vacía", "descripción formada sólo por espacios",
            "una fotografía todavía sin confirmar" -> listOf("photo://first.jpg")
            "ninguna fotografía" -> emptyList()
            "identificadores de archivo repetidos" -> listOf("photo://first.jpg", "photo://second.jpg")
            else -> error("Unapproved draft problem: $problem")
        }
        completion.selectEvidence(sources)
        expectedPhotos = sources.map { it.substringAfterLast('/') }
        advanceUntilIdle()
        if (problem == "identificadores de archivo repetidos") {
            duplicateFileIds = true
            completion.evidence.value.forEach { completion.uploadEvidence(it.id) }
            advanceUntilIdle()
        }
    }

    @When("intento confirmar la finalización")
    fun attemptCompletionReport() {
        attemptedValidation = completion.attemptSubmit()
    }

    @Then("el envío permanece bloqueado con una explicación del problema")
    fun submissionIsBlockedWithExplanation() {
        val expected = when (draftProblem) {
            "descripción vacía", "descripción formada sólo por espacios" ->
                CompletionDraftValidation.Invalid.DescriptionRequired
            "ninguna fotografía" -> CompletionDraftValidation.Invalid.PhotoRequired
            "una fotografía todavía sin confirmar" -> CompletionDraftValidation.Invalid.UnconfirmedPhoto
            "identificadores de archivo repetidos" -> CompletionDraftValidation.Invalid.DuplicatePhotoIds
            else -> error("Unapproved draft problem: $draftProblem")
        }
        assertEquals(expected, attemptedValidation)
        assertEquals(expected, completion.validationIssue.value)
    }

    @And("no se registra un reporte ni se pierde el resto del borrador")
    fun noReportAndDraftRetained() {
        assertEquals(1, detailCalls)
        assertEquals(null, detail.completionReportId)
        assertEquals(WorkOrderStatus.Scheduled, detail.status)
        assertEquals(expectedDescription, completion.description.value)
        assertEquals(expectedPhotos, completion.evidence.value.mapNotNull {
            (it.status as? EvidenceSelectionStatus.Ready)?.image?.originalName
        })
    }

    @Given("que tengo una foto confirmada y otra cuya carga falló en {string}")
    fun confirmedAndFailedPhoto(stage: String) = runTest(dispatcher.scheduler) {
        failedUploadStage = when (stage) {
            "preparación de la subida" -> CompletionUploadStage.PRESIGN
            "transferencia del archivo" -> CompletionUploadStage.TRANSFER
            "confirmación del archivo" -> CompletionUploadStage.CONFIRM
            else -> error("Unapproved upload stage: $stage")
        }
        failurePending = true
        completion = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { now },
            session, evidencePreparer, validateDraft, completionUploader, orders, SavedStateHandle())
        completion.open(selected)
        advanceUntilIdle()
        completion.onDescriptionChange("Trabajo terminado y revisado")
        completion.selectEvidence(listOf("photo://first.jpg", "photo://second.jpg"))
        advanceUntilIdle()
        completion.uploadEvidence(completion.evidence.value[0].id)
        completion.uploadEvidence(completion.evidence.value[1].id)
        advanceUntilIdle()
    }

    @And("veo el estado de cada foto y la opción de reintentar la fallida")
    fun statusesAndRetry() {
        assertEquals(EvidenceUploadStatus.Confirmed("file-first.jpg"), completion.evidence.value[0].uploadStatus)
        assertTrue(completion.evidence.value[1].uploadStatus is EvidenceUploadStatus.Failed)
        assertEquals(failedUploadStage,
            (completion.evidence.value[1].uploadStatus as EvidenceUploadStatus.Failed).failure.stage)
    }

    @And("el próximo intento de esa carga puede completarse")
    fun nextUploadCanComplete() { assertEquals(false, failurePending) }

    @When("reintento la fotografía fallida")
    fun retryFailedPhoto() = runTest(dispatcher.scheduler) {
        completion.retryEvidence(completion.evidence.value[1].id)
        advanceUntilIdle()
    }

    @Then("ambas fotografías quedan confirmadas en su orden original")
    fun bothConfirmedInOrder() {
        assertEquals(listOf("first.jpg", "second.jpg"), completion.evidence.value.map {
            (it.status as EvidenceSelectionStatus.Ready).image.originalName
        })
        assertEquals(listOf("file-first.jpg", "file-second.jpg"), completion.evidence.value.map {
            (it.uploadStatus as EvidenceUploadStatus.Confirmed).fileId
        })
    }

    @And("se conserva la descripción sin volver a subir la foto ya confirmada")
    fun descriptionAndFirstUploadPreserved() {
        assertEquals("Trabajo terminado y revisado", completion.description.value)
        assertEquals(1, presignCalls.count { it == "first.jpg" })
        assertEquals(2, presignCalls.count { it == "second.jpg" })
    }

    @And("no se registra la finalización hasta que la confirme")
    fun noReportBeforeConfirmation() {
        assertEquals(WorkOrderStatus.Scheduled, detail.status)
        assertEquals(null, detail.completionReportId)
        assertEquals(listOf("file-first.jpg", "file-second.jpg"),
            (completion.attemptSubmit() as CompletionDraftValidation.Valid).confirmedFileIds)
    }

    @Given("que soy el prestador asignado de una orden scheduled cuyo turno ya comenzó")
    fun eligibleReportOrder() = runTest(dispatcher.scheduler) {
        turns = ProviderTurnsViewModel(GetProviderTurnsUseCase(orders), GetServiceProposalsUseCase(proposals), session)
        home = ProviderHomeViewModel(GetPendingJobRequestsUseCase(object : JobRequestRepository {
            override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> = ActivityLoadOutcome.Success(emptyList())
            override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome = error("Unused")
        }), GetScheduledWorkUseCase(orders) { 0L })
        completion = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { now },
            session, evidencePreparer, validateDraft, completionUploader, orders, SavedStateHandle())
        completion.open(selected)
        advanceUntilIdle()
        assertEquals(CompletionEligibility.Eligible,
            (completion.uiState.value as ProviderCompletionUiState.Ready).eligibility)
        assertEquals(1, (home.uiState.value.scheduledWork as ActivitySectionState.Ready).items.size)
    }

    @And("escribí una descripción válida y tengo {int} fotografías confirmadas")
    fun validConfirmedDraft(count: Int) = runTest(dispatcher.scheduler) {
        completion.onDescriptionChange("  Trabajo terminado y revisado  ")
        expectedPhotos = listOf("first.jpg", "second.jpg", "third.jpg").take(count)
        completion.selectEvidence(expectedPhotos.map { "photo://$it" })
        advanceUntilIdle()
        completion.evidence.value.forEach { completion.uploadEvidence(it.id) }
        advanceUntilIdle()
        assertEquals(count, completion.evidence.value.size)
    }

    @And("la API registra el reporte y devuelve la orden actualizada como awaiting_payment")
    fun apiWillReportAwaitingPayment() { assertTrue(submittedReports.isEmpty()) }

    @When("confirmo la finalización con un doble toque")
    fun doubleTapReport() = runTest(dispatcher.scheduler) {
        completion.confirmCompletion()
        completion.confirmCompletion()
        advanceUntilIdle()
    }

    @Then("se envía un solo reporte con la descripción y las fotografías en su orden")
    fun oneOrderedReport() {
        assertEquals(listOf(Triple(42, "Trabajo terminado y revisado",
            expectedPhotos.map { "file-$it" })), submittedReports)
        assertEquals(expectedPhotos.map { "file-$it" }, completion.evidence.value.map {
            (it.uploadStatus as EvidenceUploadStatus.Confirmed).fileId
        })
    }

    @And("veo la confirmación de éxito y el turno como Pendiente de pago")
    fun seesServerPaymentStatus() {
        assertEquals(CompletionSubmissionState.Confirmed(17, true), completion.submission.value)
        assertTrue(17 != selected.id)
        assertEquals(WorkOrderStatus.AwaitingPayment, completion.refreshedOrderStatus.value)
    }

    @And("se actualizan Turnos y la actividad afectada de Inicio")
    fun refreshedTurnsAndHome() = runTest(dispatcher.scheduler) {
        turns.load(preserveContent = true)
        home.retryScheduledWork()
        advanceUntilIdle()
        assertEquals(WorkOrderStatus.AwaitingPayment,
            (turns.uiState.value as ProviderTurnsUiState.Ready).orders.single().status)
        assertTrue((home.uiState.value.scheduledWork as ActivitySectionState.Ready).items.isEmpty())
    }

    @And("no se ofrece otro reporte ni se marca la orden como Pagado localmente")
    fun noDuplicateOrLocalPaid() = runTest(dispatcher.scheduler) {
        completion.confirmCompletion()
        advanceUntilIdle()
        assertEquals(1, submittedReports.size)
        assertEquals(WorkOrderStatus.AwaitingPayment, detail.status)
        assertEquals(CompletionEligibility.AlreadyReported,
            (completion.uiState.value as ProviderCompletionUiState.Ready).eligibility)
    }

    @Given("que tengo un borrador válido para una orden que estaba habilitada")
    fun validDraftBeforeRejection() = runTest(dispatcher.scheduler) {
        completion = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { now },
            session, evidencePreparer, validateDraft, completionUploader, orders, SavedStateHandle())
        completion.open(selected)
        advanceUntilIdle()
        completion.onDescriptionChange("Trabajo terminado y revisado")
        completion.selectEvidence(listOf("photo://first.jpg"))
        advanceUntilIdle()
        completion.uploadEvidence(completion.evidence.value.single().id)
        advanceUntilIdle()
        assertEquals(listOf("file-first.jpg"),
            (completion.attemptSubmit() as CompletionDraftValidation.Valid).confirmedFileIds)
    }

    @And("la API rechaza el reporte con {string}")
    fun apiRejectsReport(response: String) {
        rejectedReport = when (response) {
            "400 por datos inválidos" -> PostCompletionReportOutcome.Rejected.InvalidData
            "401 por sesión inválida" -> PostCompletionReportOutcome.Rejected.Unauthorized
            "403 por falta de permisos" -> PostCompletionReportOutcome.Rejected.Forbidden
            "404 por orden inexistente" -> PostCompletionReportOutcome.Rejected.NotFound
            "409 por fecha o estado vigente" -> PostCompletionReportOutcome.Rejected.Conflict.also {
                conflictDetail = detail.copy(scheduledOn = 2_000)
            }
            "409 por reporte existente" -> PostCompletionReportOutcome.Rejected.Conflict.also {
                conflictDetail = detail.copy(status = WorkOrderStatus.AwaitingPayment, completionReportId = 17)
            }
            else -> error("Unapproved rejection: $response")
        }
    }

    @When("confirmo la finalización")
    fun confirmRejectedReport() = runTest(dispatcher.scheduler) {
        completion.confirmCompletion()
        advanceUntilIdle()
    }

    fun seeRejectedRecovery(recovery: String) {
        when (recovery) {
            "los datos conservados y un aviso para corregirlos" -> {
                assertEquals(CompletionSubmissionState.Rejected(PostCompletionReportOutcome.Rejected.InvalidData),
                    completion.submission.value)
                assertEquals("Trabajo terminado y revisado", completion.description.value)
                assertEquals(EvidenceUploadStatus.Confirmed("file-first.jpg"), completion.evidence.value.single().uploadStatus)
            }
            "el flujo de autenticación sin datos privados de la sesión previa" -> {
                assertEquals(null, session.getSession())
                assertEquals(ProviderCompletionUiState.SessionExpired, completion.uiState.value)
                assertEquals("", completion.description.value)
                assertTrue(completion.evidence.value.isEmpty())
            }
            "un aviso de falta de permisos sin permitir otro envío" ->
                assertEquals(CompletionSubmissionState.Rejected(PostCompletionReportOutcome.Rejected.Forbidden),
                    completion.submission.value)
            "un aviso de orden no disponible y la opción de volver a Turnos" ->
                assertEquals(CompletionSubmissionState.Rejected(PostCompletionReportOutcome.Rejected.NotFound),
                    completion.submission.value)
            "la orden consultada nuevamente y la explicación correspondiente" -> {
                assertEquals(CompletionEligibility.TooEarly,
                    (completion.uiState.value as ProviderCompletionUiState.Ready).eligibility)
                assertTrue(detailCalls >= 3)
            }
            "la orden consultada nuevamente sin ofrecer otro reporte" -> {
                assertEquals(CompletionEligibility.AlreadyReported,
                    (completion.uiState.value as ProviderCompletionUiState.Ready).eligibility)
                assertTrue(detailCalls >= 3)
            }
            else -> error("Unapproved recovery: $recovery")
        }
    }

    @And("no se informa un éxito ni se repite automáticamente el envío")
    fun rejectedReportIsNotRetried() = runTest(dispatcher.scheduler) {
        assertEquals(1, submittedReports.size)
        assertTrue(completion.submission.value !is CompletionSubmissionState.Confirmed)
        if (session.getSession() != null && rejectedReport != PostCompletionReportOutcome.Rejected.InvalidData)
            completion.confirmCompletion()
        advanceUntilIdle()
        assertEquals(1, submittedReports.size)
    }

    @Given("que envié un reporte y la conexión se interrumpió sin conocer el resultado")
    fun uncertainReportWasSent() = runTest(dispatcher.scheduler) {
        uncertainReport = true
        completion = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { now },
            session, evidencePreparer, validateDraft, completionUploader, orders, completionHandle)
        completion.open(selected)
        advanceUntilIdle()
        completion.onDescriptionChange("Trabajo terminado y revisado")
        completion.selectEvidence(listOf("photo://first.jpg"))
        advanceUntilIdle()
        completion.uploadEvidence(completion.evidence.value.single().id)
        advanceUntilIdle()
        assertEquals(CompletionDraftValidation.Valid("Trabajo terminado y revisado", listOf("file-first.jpg")),
            completion.attemptSubmit())
        completion.confirmCompletion()
        runCurrent()
        assertEquals(1, submittedReports.size)
        assertEquals(selected.id, completionHandle.get<Int>("completion_pending_order_id"))
    }

    @And("la consulta posterior de esa misma orden obtiene {string}")
    fun subsequentQueryReturns(result: String) { reconciliationResult.complete(result) }

    @When("se reconcilia el estado de la orden")
    fun reconcileUncertainReport() = runTest(dispatcher.scheduler) {
        advanceUntilIdle()
    }

    fun seeReconciliationRecovery(recovery: String) {
        when (recovery) {
            "la finalización registrada y el estado vigente sin otro envío" -> {
                assertEquals(CompletionSubmissionState.Confirmed(null, true), completion.submission.value)
                assertEquals(CompletionEligibility.AlreadyReported,
                    (completion.uiState.value as ProviderCompletionUiState.Ready).eligibility)
                assertEquals(WorkOrderStatus.AwaitingPayment, completion.refreshedOrderStatus.value)
                assertEquals(null, completionHandle.get<Int>("completion_pending_order_id"))
            }
            "el borrador conservado y la opción de confirmar un nuevo intento" -> {
                assertEquals(CompletionSubmissionState.Idle, completion.submission.value)
                assertEquals(CompletionEligibility.Eligible,
                    (completion.uiState.value as ProviderCompletionUiState.Ready).eligibility)
                assertEquals(null, completionHandle.get<Int>("completion_pending_order_id"))
                assertEquals(CompletionDraftValidation.Valid("Trabajo terminado y revisado", listOf("file-first.jpg")),
                    completion.attemptSubmit())
            }
            "el borrador conservado y la opción de reintentar sólo la consulta" -> {
                assertEquals(CompletionSubmissionState.QueryFailed, completion.submission.value)
                assertEquals(selected.id, completionHandle.get<Int>("completion_pending_order_id"))
                assertEquals(CompletionDraftValidation.Valid("Trabajo terminado y revisado", listOf("file-first.jpg")),
                    validateDraft(completion.description.value, completion.evidence.value.map {
                        (it.uploadStatus as EvidenceUploadStatus.Confirmed).fileId
                    }))
            }
            else -> error("Unapproved recovery: $recovery")
        }
        assertEquals(1, submittedReports.size)
    }

    @And("no se envía automáticamente otro reporte")
    fun uncertainReportIsNotAutomaticallyRetried() = runTest(dispatcher.scheduler) {
        if (completion.submission.value != CompletionSubmissionState.Idle) {
            completion.confirmCompletion()
            advanceUntilIdle()
        }
        assertEquals(1, submittedReports.size)
        if (completion.submission.value == CompletionSubmissionState.QueryFailed) {
            val queries = detailCalls
            completion.retryReconciliation()
            advanceUntilIdle()
            assertTrue(detailCalls > queries)
            assertEquals(1, submittedReports.size)
            assertEquals(selected.id, completionHandle.get<Int>("completion_pending_order_id"))
        }
    }

    @After fun tearDown() { Dispatchers.resetMain() }
}
