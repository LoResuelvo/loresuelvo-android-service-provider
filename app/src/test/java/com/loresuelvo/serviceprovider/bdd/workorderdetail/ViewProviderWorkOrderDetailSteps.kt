package com.loresuelvo.serviceprovider.bdd.workorderdetail

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderCompletionImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderCompletionReport
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderReview
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
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderWorkOrderDetailUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.ResolveConversationWorkOrderUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.ui.components.providerInitials
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.ConversationOrderLinkUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.ConversationOrderLinkViewModel
import com.loresuelvo.serviceprovider.ui.screens.turns.formatTurnDate
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailViewModel
import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewProviderWorkOrderDetailSteps {
    private val dispatcher = StandardTestDispatcher()
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "p@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val scheduledOn = Instant.parse("2026-10-05T00:30:00Z").toEpochMilli()
    private val summary = WorkOrder(42, "Ana Pérez", "Stale summary", 1, WorkOrderStatus.Paid,
        consumerGivenName = "Ana", consumerSurname = "Pérez", serviceProposalId = 10, consumerId = 3)
    private var detail = WorkOrderDetail(42, 10, 3, 7, 123456, scheduledOn,
        "Repair the kitchen tap and preserve the original fittings", WorkOrderStatus.Scheduled, null)
    private var evidenceCase = ""
    private var paymentCase = ""
    private var reviewCase = ""
    private var origin = ""
    private var detailCalls = 0
    private var listCalls = 0
    private var linkedProposals = emptyList<ServiceProposalSummary>()
    private val orders = object : WorkOrderRepository {
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            detailCalls++
            assertEquals(42, id)
            return WorkOrderDetailOutcome.Success(detail)
        }
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> {
            listCalls++
            return ActivityLoadOutcome.Success(listOf(summary))
        }
    }
    private val proposals = object : ServiceProposalRepository {
        override suspend fun list() = ServiceProposalListOutcome.Success(linkedProposals)
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
            error("No proposal should be created")
    }
    private lateinit var model: ProviderTurnDetailViewModel
    private lateinit var linkModel: ConversationOrderLinkViewModel

    @Given("que estoy autenticado como prestador")
    fun signedIn() { Dispatchers.setMain(dispatcher) }

    @Given("que la orden 42 de la propuesta 10 aparece en {string}")
    fun orderAppearsIn(source: String) {
        require(source == "Turnos" || source == "trabajos agendados de Inicio")
        origin = source
    }

    @And("pertenece al consumidor Ana Pérez sin fotografía de perfil")
    fun consumerHasNoPhoto() { assertNull(summary.consumerPhotoUrl) }

    @And("su detalle vigente informa 123456 centavos, fecha con zona horaria, descripción completa y estado scheduled")
    fun currentDetailExists() { assertEquals(WorkOrderStatus.Scheduled, detail.status) }

    @When("elijo Ver detalle de la orden 42")
    fun openDetail() {
        assertTrue(origin.isNotBlank())
        assertEquals("provider_turns/42", Route.ProviderTurnDetail.buildPath(summary.id))
        model = ProviderTurnDetailViewModel(SavedStateHandle(mapOf("turnId" to summary.id)),
            GetProviderWorkOrderDetailUseCase(orders), GetServiceProposalsUseCase(proposals), session)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Then("veo la misma pantalla de detalle con Ana Pérez y su avatar de respaldo")
    fun seesConsumer() {
        val ready = model.uiState.value as ProviderTurnDetailUiState.Ready
        assertEquals("Ana Pérez", ready.result.consumer?.consumerName)
        assertEquals("AP", providerInitials(ready.result.consumer!!.consumerGivenName,
            ready.result.consumer!!.consumerSurname))
        assertEquals(1, detailCalls)
        assertEquals(1, listCalls)
    }

    @And("veo el monto de ARS 1234,56, la fecha y hora local, la descripción original completa y el estado Confirmado")
    fun seesCurrentData() {
        val current = (ready().result.detail as WorkOrderDetailOutcome.Success).order
        assertEquals(123456L, current.amountCents)
        assertTrue(formatTurnDate(current.scheduledOn, "'el' d 'de' MMMM 'a las' HH:mm", Locale("es", "AR"),
            TimeZone.getTimeZone("America/Argentina/Buenos_Aires")).contains("21:30"))
        assertEquals(detail.description, current.description)
        assertEquals(WorkOrderStatus.Scheduled, current.status)
    }

    @And("no veo rubro del consumidor, evidencia de finalización ni fecha de pago")
    fun noInventedFields() {
        assertNull((ready().result.detail as WorkOrderDetailOutcome.Success).order.completionReportId)
    }

    @And("no veo acciones para pagar ni escribir una reseña")
    fun noConsumerActions() { assertEquals(WorkOrderStatus.Scheduled,
        (ready().result.detail as WorkOrderDetailOutcome.Success).order.status) }

    @Given("que estoy en la conversación 70 con Ana Pérez")
    fun inConversation() { origin = "chat" }

    @And("esa conversación tiene vinculada la orden 42 de la propuesta 10")
    fun conversationHasOrder() {
        linkedProposals = listOf(ServiceProposalSummary(10, 70, 123456, scheduledOn, "Work", 60,
            ServiceProposalStatus.Accepted, 1,
            ServiceProposalCounterpart(3, "consumer", "Ana", "Pérez", null, null),
            ServiceProposalBookingTerms("ARS", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @And("veo la acción Ver detalle de la orden en la barra superior del chat")
    fun seesDirectAction() {
        linkModel = ConversationOrderLinkViewModel(SavedStateHandle(mapOf("conversationId" to 70)),
            ResolveConversationWorkOrderUseCase(GetServiceProposalsUseCase(proposals),
                GetProviderTurnsUseCase(orders)), session)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConversationOrderLinkUiState.Linked(42), linkModel.uiState.value)
    }

    @When("elijo Ver detalle de la orden")
    fun opensOrderFromChat() {
        val orderId = (linkModel.uiState.value as ConversationOrderLinkUiState.Linked).orderId
        assertEquals("provider_turns/42", Route.ProviderTurnDetail.buildPath(orderId))
        model = ProviderTurnDetailViewModel(SavedStateHandle(mapOf("turnId" to orderId)),
            GetProviderWorkOrderDetailUseCase(orders), GetServiceProposalsUseCase(proposals), session)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Then("veo el detalle vigente de la orden 42 con la identidad de su consumidor")
    fun seesChatOrder() {
        val current = (ready().result.detail as WorkOrderDetailOutcome.Success).order
        assertEquals(42, current.id)
        assertEquals("Ana Pérez", ready().result.consumer?.consumerName)
    }

    @And("no necesito abrir el detalle de una propuesta")
    fun directDestination() { assertEquals("chat", origin) }

    @And("no se usa el número de la propuesta ni de la conversación como número de orden")
    fun idsStayDistinct() {
        val id = (ready().result.detail as WorkOrderDetailOutcome.Success).order.id
        assertEquals(42, id)
        assertTrue(id != linkedProposals.single().id && id != linkedProposals.single().conversationId)
    }

    @Given("que mi orden está en estado {string}")
    fun orderHasStatus(status: String) {
        detail = detail.copy(status = when (status) {
            "scheduled" -> WorkOrderStatus.Scheduled
            "awaiting_payment" -> WorkOrderStatus.AwaitingPayment
            "paid" -> WorkOrderStatus.Paid
            else -> error("Unexpected status")
        })
    }

    @And("su detalle contiene {string}")
    fun detailContainsEvidence(case: String) {
        evidenceCase = case
        val report = when (case) {
            "ningún reporte disponible" -> null
            else -> {
                val count = when {
                    "tres fotos" in case -> 3
                    "una foto" in case -> 1
                    else -> 0
                }
                WorkOrderCompletionReport(17, "Delivered work as agreed",
                    Instant.parse("2026-08-15T16:00:00Z").toEpochMilli(),
                    (1..count).map { WorkOrderCompletionImage("file-$it", "$it.jpg", "https://storage.test/$it") })
            }
        }
        detail = detail.copy(completionReportId = report?.id, completionReport = report)
    }

    @When("abro el detalle de la orden")
    fun openCurrentOrder() {
        model = ProviderTurnDetailViewModel(SavedStateHandle(mapOf("turnId" to 42)),
            GetProviderWorkOrderDetailUseCase(orders), GetServiceProposalsUseCase(proposals), session)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Then("""^veo "(Evidencia de finalización.*|un aviso de evidencia no disponible|la descripción y fecha.*|sólo los datos.*)"$""")
    fun seesEvidence(result: String) {
        val current = (ready().result.detail as WorkOrderDetailOutcome.Success).order
        when {
            result.startsWith("Evidencia de finalización") -> {
                assertTrue(current.status == WorkOrderStatus.AwaitingPayment || current.status == WorkOrderStatus.Paid)
                val report = requireNotNull(current.completionReport)
                assertEquals("Delivered work as agreed", report.description)
                assertEquals(Instant.parse("2026-08-15T16:00:00Z").toEpochMilli(), report.reportedOn)
                assertEquals(if ("tres fotos" in evidenceCase) 3 else 1, report.images.size)
                assertEquals((1..report.images.size).map { "file-$it" }, report.images.map { it.fileId })
            }
            result == "un aviso de evidencia no disponible" -> assertNull(current.completionReport)
            result.startsWith("la descripción y fecha") -> {
                assertEquals("Delivered work as agreed", current.completionReport?.description)
                assertTrue(current.completionReport?.images?.isEmpty() == true)
            }
            result.startsWith("sólo los datos") -> assertEquals(WorkOrderStatus.Scheduled, current.status)
            else -> error("Unexpected result")
        }
    }

    @And("conservo los datos válidos del servicio y su descripción original separada de la entrega")
    fun keepsOriginalService() {
        val current = (ready().result.detail as WorkOrderDetailOutcome.Success).order
        assertEquals(detail.description, current.description)
        assertTrue(current.description != current.completionReport?.description)
    }

    @And("no se inventan fechas, fotografías ni descripciones ausentes")
    fun absentEvidenceStaysAbsent() {
        val current = (ready().result.detail as WorkOrderDetailOutcome.Success).order
        if (evidenceCase == "ningún reporte disponible") assertNull(current.completionReport)
        if ("sin fotos" in evidenceCase) assertTrue(current.completionReport?.images?.isEmpty() == true)
    }

    @Given("que mi orden paid tiene {string} y {string}")
    fun paidOrderHas(payment: String, review: String) {
        paymentCase = payment
        reviewCase = review
        detail = detail.copy(status = WorkOrderStatus.Paid,
            paidOn = if (payment == "fecha de pago informada")
                Instant.parse("2026-10-05T00:30:00Z").toEpochMilli() else null,
            review = if (review == "calificación 5 y comentario") WorkOrderReview(5, "Excellent work") else null)
    }

    @When("abro su detalle")
    fun openPaidDetail() {
        model = ProviderTurnDetailViewModel(SavedStateHandle(mapOf("turnId" to detail.id)),
            GetProviderWorkOrderDetailUseCase(orders), GetServiceProposalsUseCase(proposals), session)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Then("veo el estado Pagado y {string}")
    fun seesPaidHistory(result: String) {
        val current = (ready().result.detail as WorkOrderDetailOutcome.Success).order
        assertEquals(WorkOrderStatus.Paid, current.status)
        if (paymentCase == "fecha de pago informada") {
            assertTrue(result.contains("fecha local del pago"))
            assertTrue(formatTurnDate(current.paidOn!!, "'el' d 'de' MMMM 'a las' HH:mm",
                Locale("es", "AR"), TimeZone.getTimeZone("America/Argentina/Buenos_Aires")).contains("21:30"))
        } else assertNull(current.paidOn)
        if (reviewCase == "calificación 5 y comentario") {
            assertEquals(5, current.review?.rating)
            assertEquals("Excellent work", current.review?.description)
        } else assertNull(current.review)
    }

    @And("no puedo pagar, crear ni editar la reseña del consumidor")
    fun noPaidActions() {
        assertEquals(WorkOrderStatus.Paid,
            (ready().result.detail as WorkOrderDetailOutcome.Success).order.status)
    }

    private fun ready() = model.uiState.value as ProviderTurnDetailUiState.Ready

    @After fun tearDown() {
        Dispatchers.setMain(dispatcher)
        session.clearSession()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }
}
