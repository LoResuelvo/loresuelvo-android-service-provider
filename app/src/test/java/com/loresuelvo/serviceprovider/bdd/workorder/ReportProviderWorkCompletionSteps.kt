package com.loresuelvo.serviceprovider.bdd.workorder

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
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    private val orders = object : WorkOrderRepository {
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = ActivityLoadOutcome.Success(listOf(selected))
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            assertEquals(selected.id, id)
            detailCalls++
            return WorkOrderDetailOutcome.Success(detail)
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
    private lateinit var initialDetail: WorkOrderDetail
    private var expectedPhotos = emptyList<String>()
    private var photoAction = ""
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
            })
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
            session, evidencePreparer)
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
            else -> error("Unapproved photo result: $result")
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

    @After fun tearDown() { Dispatchers.resetMain() }
}
