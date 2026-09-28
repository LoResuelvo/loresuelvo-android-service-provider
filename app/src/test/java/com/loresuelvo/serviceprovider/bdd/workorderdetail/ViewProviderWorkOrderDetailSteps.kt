package com.loresuelvo.serviceprovider.bdd.workorderdetail

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
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderWorkOrderDetailUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.ui.components.providerInitials
import com.loresuelvo.serviceprovider.ui.navigation.Route
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
    private val detail = WorkOrderDetail(42, 10, 3, 7, 123456, scheduledOn,
        "Repair the kitchen tap and preserve the original fittings", WorkOrderStatus.Scheduled, null)
    private var origin = ""
    private var detailCalls = 0
    private var listCalls = 0
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
        override suspend fun list() = ServiceProposalListOutcome.Success(emptyList())
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
            error("No proposal should be created")
    }
    private lateinit var model: ProviderTurnDetailViewModel

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

    private fun ready() = model.uiState.value as ProviderTurnDetailUiState.Ready

    @After fun tearDown() {
        session.clearSession()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }
}
