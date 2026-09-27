package com.loresuelvo.serviceprovider.bdd.turns

import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetScheduledWorkUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsViewModel
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeViewModel
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.components.providerInitials
import com.loresuelvo.serviceprovider.ui.screens.turns.formatTurnDate
import com.loresuelvo.serviceprovider.ui.screens.turns.ProviderTurnBadgeTreatment
import com.loresuelvo.serviceprovider.ui.screens.turns.providerTurnStatusBadge
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.cucumber.java.en.Then
import io.cucumber.java.en.And
import io.cucumber.datatable.DataTable
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderTurnsSteps {
    private val dispatcher = StandardTestDispatcher()
    private var orders = listOf(
        WorkOrder(1, "Ana Pérez", "Reparar canilla", 1, WorkOrderStatus.Scheduled),
        WorkOrder(2, "Bea Silva", "Pintar pared", 2, WorkOrderStatus.Paid),
    )
    private var pendingOrders: CompletableDeferred<ActivityLoadOutcome<WorkOrder>>? = null
    private var orderCalls = 0
    private val repository = object : WorkOrderRepository {
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> {
            orderCalls++
            return pendingOrders?.await() ?: ActivityLoadOutcome.Success(orders)
        }
    }
    private var proposals = emptyList<ServiceProposalSummary>()
    private var proposalListCalls = 0
    private var proposalCreateCalls = 0
    private var proposalFailure: ServiceProposalListOutcome.Failure? = null
    private val proposalRepository = object : ServiceProposalRepository {
        override suspend fun list(): ServiceProposalListOutcome {
            proposalListCalls++
            proposalFailure?.let { proposalFailure = null; return it }
            return ServiceProposalListOutcome.Success(proposals)
        }
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
            proposalCreateCalls++
            error("Must not create a proposal")
        }
    }
    private lateinit var viewModel: ProviderTurnsViewModel
    private var photoCase = ""
    private lateinit var pastStatus: WorkOrderStatus
    private var selectedOrder: WorkOrder? = null
    private var openedConversationId: Int? = null
    private lateinit var homeViewModel: ProviderHomeViewModel
    private lateinit var now: Instant

    @Given("que inicié sesión como prestador")
    fun signedIn() { Dispatchers.setMain(dispatcher) }

    @Given("que tengo órdenes de trabajo registradas")
    fun hasWorkOrders() = Unit

    @When("abro Turnos desde {string}")
    fun openTurns(access: String) {
        require(access == "Ver todos en Trabajos agendados de Inicio" || access == "Turnos en Trabajos")
        enterTurns()
    }

    @Given("que la API devuelve estas órdenes y hoy es 26 de septiembre de 2026:")
    fun apiReturnsOrders(table: DataTable) {
        orders = table.asMaps().map { row ->
            val status = when (row.getValue("estado")) {
                "scheduled" -> WorkOrderStatus.Scheduled
                "paid" -> WorkOrderStatus.Paid
                "awaiting_payment" -> WorkOrderStatus.AwaitingPayment
                else -> error("Unexpected status")
            }
            WorkOrder(
                id = row.getValue("id").toInt(),
                consumerName = "Ana Pérez",
                description = "Existing work order",
                scheduledOn = Instant.parse(row.getValue("fecha")).toEpochMilli(),
                status = status,
            )
        }
    }

    @When("entro a Turnos")
    fun enterTurns() {
        viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(repository), GetServiceProposalsUseCase(proposalRepository))
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Given("que la consulta de órdenes todavía no respondió")
    fun ordersArePending() {
        Dispatchers.setMain(dispatcher)
        pendingOrders = CompletableDeferred()
    }

    @Then("veo un indicador y texto de carga accesibles")
    fun seesLoading() {
        assertEquals(1, orderCalls)
        assertEquals(ProviderTurnsUiState.Loading, viewModel.uiState.value)
    }

    @And("no veo un mensaje de lista vacía")
    fun noPrematureEmptyState() {
        assertNotEquals(ProviderTurnsUiState.Ready(emptyList()), viewModel.uiState.value)
    }

    @Given("que la API devuelve una lista de órdenes vacía")
    fun apiReturnsNoOrders() {
        Dispatchers.setMain(dispatcher)
        orders = emptyList()
    }

    @Then("veo el estado vacío con texto adaptado al prestador")
    fun seesProviderEmptyState() {
        assertEquals(ProviderTurnsUiState.Ready(emptyList()), viewModel.uiState.value)
        assertEquals(1, orderCalls)
    }

    @And("no veo un indicador de carga ni un error")
    fun seesNeitherLoadingNorError() {
        assertNotEquals(ProviderTurnsUiState.Loading, viewModel.uiState.value)
        assertNotEquals(ProviderTurnsUiState.Error, viewModel.uiState.value)
    }

    @Given("que una orden pasada tiene estado {string}")
    fun pastOrderHasStatus(status: String) {
        pastStatus = when (status) {
            "scheduled" -> WorkOrderStatus.Scheduled
            "awaiting_payment" -> WorkOrderStatus.AwaitingPayment
            "paid" -> WorkOrderStatus.Paid
            else -> error("Unexpected status")
        }
        orders = listOf(WorkOrder(40, "Ana Pérez", "Existing work order",
            Instant.parse("2020-01-01T00:00:00Z").toEpochMilli(), pastStatus))
    }

    @Then("veo la etiqueta {string} con el tratamiento {string}")
    fun seesPublishedStatus(label: String, treatment: String) {
        val order = (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.single()
        val expectedLabel = when (label) {
            "Confirmado" -> R.string.provider_turns_status_scheduled
            "Pendiente de pago" -> R.string.provider_turns_status_awaiting_payment
            "Pagado" -> R.string.provider_turns_status_paid
            else -> error("Unexpected label")
        }
        val expectedTreatment = when (treatment) {
            "principal" -> ProviderTurnBadgeTreatment.Primary
            "error" -> ProviderTurnBadgeTreatment.Error
            "superficie neutra" -> ProviderTurnBadgeTreatment.Neutral
            else -> error("Unexpected treatment")
        }
        assertEquals(expectedLabel, providerTurnStatusBadge(order.status)?.label)
        assertEquals(expectedTreatment, providerTurnStatusBadge(order.status)?.treatment)
    }

    @And("su estado no cambia por tener fecha pasada")
    fun pastDateDoesNotChangeStatus() {
        assertEquals(pastStatus, (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.single().status)
    }

    @Given("que el motivo de una orden supera la vista previa de la tarjeta")
    fun longOrderReason() {
        orders = listOf(WorkOrder(40, "Ana Pérez", "Repair the kitchen tap and replace the worn valve while preserving the original fittings",
            Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(), WorkOrderStatus.Scheduled,
            amountCents = 1500050))
    }

    @When("elijo Ver detalles en esa tarjeta")
    fun chooseDetails() {
        enterTurns()
        selectedOrder = (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.single()
    }

    @Then("veo el motivo completo, consumidor, monto, fecha local y estado")
    fun seesFullSummary() {
        val order = requireNotNull(selectedOrder)
        assertEquals(orders.single().description, order.description)
        assertEquals("Ana Pérez", order.consumerName)
        assertEquals(1500050, order.amountCents)
        assertEquals(Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(), order.scheduledOn)
        assertEquals(WorkOrderStatus.Scheduled, order.status)
    }

    @And("puedo elegir Ver conversación")
    fun conversationActionAvailable() {
        assertEquals(40, requireNotNull(selectedOrder).id)
    }

    @And("no necesito cargar un reporte de finalización para consultar el motivo")
    fun noCompletionReportNeeded() {
        assertEquals(orders.single().description, requireNotNull(selectedOrder).description)
    }

    @And("no puedo pagar, aceptar, rechazar, calificar, cancelar o reprogramar")
    fun noMutatingActions() {
        assertEquals(WorkOrderStatus.Scheduled, requireNotNull(selectedOrder).status)
    }

    @Given("que la orden 40 referencia la propuesta 12 del consumidor 7")
    fun orderReferencesProposal() {
        orders = listOf(WorkOrder(40, "Ana Pérez", "Work", 1, WorkOrderStatus.Scheduled,
            serviceProposalId = 12, consumerId = 7))
    }

    @And("la propuesta 12 referencia la conversación 93")
    fun proposalReferencesConversation() {
        proposals = listOf(ServiceProposalSummary(
            id = 12, conversationId = 93, amountCents = 100, scheduledOnEpochMillis = 1,
            description = "Work", estimatedDurationMinutes = 60, status = ServiceProposalStatus.Accepted,
            createdOnEpochMillis = 1,
            counterpart = ServiceProposalCounterpart(7, "consumer", "Ana", "Pérez", null, null),
            bookingTerms = ServiceProposalBookingTerms("ARS", 100, 1, 99, 1, 1, 0, 2, 99, 101, 1),
        ))
    }

    @When("elijo Ver conversación para la orden 40")
    fun chooseOrderConversation() {
        enterTurns()
        openedConversationId = (viewModel.uiState.value as ProviderTurnsUiState.Ready).conversationIds[40]
    }

    @Then("se abre la conversación 93")
    fun opensLinkedConversation() {
        assertEquals("conversation/93", Route.Conversation.buildPath(requireNotNull(openedConversationId)))
    }

    @And("no se crea una conversación ni se usan los IDs 40, 12 o 7 como chat")
    fun usesOnlyLinkedConversationId() {
        assertEquals(93, openedConversationId)
        assertEquals(1, proposalListCalls)
        assertEquals(0, proposalCreateCalls)
    }

    @Given("que no se encontró la propuesta vinculada a una orden")
    fun missingLinkedProposal() { orderReferencesProposal() }

    @When("elijo Ver conversación para esa orden")
    fun chooseMissingConversation() { chooseOrderConversation() }

    @Then("veo un aviso con una acción para reintentar")
    fun seesRetryNotice() {
        assertEquals(null, (viewModel.uiState.value as ProviderTurnsUiState.Ready).conversationIds[40])
    }

    @And("permanezco en Turnos sin abrir ni crear otro chat")
    fun remainsInTurns() {
        assertEquals(null, openedConversationId)
        assertEquals(0, proposalCreateCalls)
    }

    @Given("que la consulta de propuestas falló por {string}")
    fun proposalsFailed(cause: String) {
        orderReferencesProposal()
        proposalFailure = when (cause) {
            "falta de red" -> ServiceProposalListOutcome.Failure.Unavailable
            "respuesta 500" -> ServiceProposalListOutcome.Failure.InvalidResponse
            else -> error("Unexpected proposal failure")
        }
        enterTurns()
        assertEquals(true, (viewModel.uiState.value as ProviderTurnsUiState.Ready).proposalFailure != null)
    }

    @And("una nueva consulta devuelve la propuesta vinculada a la conversación 93")
    fun nextProposalLookupSucceeds() { proposalReferencesConversation() }

    @When("reintento Ver conversación")
    fun retryConversation() {
        viewModel.retryConversation(40)
        dispatcher.scheduler.advanceUntilIdle()
        openedConversationId = (viewModel.uiState.value as ProviderTurnsUiState.Ready).conversationToOpen
    }

    @And("desaparece el aviso de error")
    fun errorNoticeClears() {
        assertEquals(null, (viewModel.uiState.value as ProviderTurnsUiState.Ready).proposalFailure)
        assertEquals(2, proposalListCalls)
        assertEquals(0, proposalCreateCalls)
    }

    @Given("que el reloj indica {string}")
    fun fixedClock(instant: String) { now = Instant.parse(instant) }

    @And("tengo órdenes scheduled anteriores, iguales y posteriores a ese instante")
    fun ordersAroundNow() {
        orders = listOf(
            WorkOrder(5, "Ana", "Later", now.plusSeconds(60).toEpochMilli(), WorkOrderStatus.Scheduled),
            WorkOrder(9, "Ana", "Equal", now.toEpochMilli(), WorkOrderStatus.Scheduled),
            WorkOrder(2, "Ana", "Equal", now.toEpochMilli(), WorkOrderStatus.Scheduled),
            WorkOrder(1, "Ana", "Past", now.minusMillis(1).toEpochMilli(), WorkOrderStatus.Scheduled),
        )
    }

    @And("tengo órdenes futuras awaiting_payment y paid y propuestas pendientes")
    fun futureNonScheduledAndPendingProposal() {
        orders = orders + listOf(
            WorkOrder(3, "Ana", "Paid", now.plusSeconds(60).toEpochMilli(), WorkOrderStatus.Paid),
            WorkOrder(4, "Ana", "Awaiting", now.plusSeconds(60).toEpochMilli(), WorkOrderStatus.AwaitingPayment),
        )
        proposalReferencesConversation()
        proposals = proposals.map { it.copy(status = ServiceProposalStatus.Pending) }
    }

    @When("abro Inicio")
    fun openHome() {
        val requests = object : JobRequestRepository {
            override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> = ActivityLoadOutcome.Success(emptyList())
            override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome = error("Not used")
        }
        homeViewModel = ProviderHomeViewModel(GetPendingJobRequestsUseCase(requests),
            GetScheduledWorkUseCase(repository) { now.toEpochMilli() })
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Then("Trabajos agendados muestra sólo órdenes scheduled desde ese instante inclusive")
    fun homeShowsOnlyUpcomingScheduled() {
        assertEquals(setOf(2, 5, 9),
            (homeViewModel.uiState.value.scheduledWork as ActivitySectionState.Ready).items.map { it.id }.toSet())
    }

    @And("las ordena por fecha ascendente y luego por ID ascendente")
    fun homeOrdersByDateAndId() {
        assertEquals(listOf(2, 9, 5),
            (homeViewModel.uiState.value.scheduledWork as ActivitySectionState.Ready).items.map { it.id })
    }

    @And("Ver todos permite consultar también las órdenes excluidas del resumen")
    fun viewAllKeepsFullOrderHistory() {
        enterTurns()
        assertEquals(setOf(1, 2, 3, 4, 5, 9),
            (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.map { it.id }.toSet())
    }

    @Then("veo las órdenes 40, 30, 11 y 12 en ese orden")
    fun seesDeterministicOrder() {
        assertEquals(listOf(40, 30, 11, 12), (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.map { it.id })
    }

    @And("no veo propuestas pendientes o rechazadas como órdenes de trabajo")
    fun noProposalsInOrders() {
        assertEquals(4, (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.size)
    }

    @Given("que una orden de Ana Pérez tiene un monto de 1500050 centavos")
    fun orderHasAmount() {
        orders = listOf(WorkOrder(7, "Ana Pérez", "", 0, WorkOrderStatus.Scheduled,
            amountCents = 1500050, consumerGivenName = "Ana", consumerSurname = "Pérez"))
    }

    @And("su fecha es {string} y su motivo es {string}")
    fun orderHasDateAndReason(date: String, reason: String) {
        orders = listOf(orders.single().copy(scheduledOn = Instant.parse(date).toEpochMilli(), description = reason))
    }

    @And("su foto está {string} y no tiene rubro")
    fun orderHasPhoto(photo: String) {
        photoCase = photo
        val url = when (photo) {
            "disponible" -> "https://cdn.example/ana.jpg"
            "ausente" -> null
            "inaccesible" -> "invalid://photo"
            else -> error("Unexpected photo case")
        }
        orders = listOf(orders.single().copy(consumerPhotoUrl = url))
    }

    @And("uso español de Argentina y la zona {string}")
    fun useArgentineTimeZone(zone: String) {
        assertEquals("America/Argentina/Buenos_Aires", zone)
    }

    @Then("veo Ana Pérez, ARS 15.000,50, el 4 de octubre a las 21:30 y el motivo")
    fun seesLocalData() {
        val order = (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.single()
        assertEquals("Ana Pérez", order.consumerName)
        assertEquals(1500050, order.amountCents)
        assertEquals("Reparar la canilla", order.description)
        assertEquals("el 4 de octubre a las 21:30", formatTurnDate(
            order.scheduledOn, "'el' d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-AR"),
            TimeZone.getTimeZone("America/Argentina/Buenos_Aires"),
        ))
    }

    @And("veo {string} sin rubro ni espacio reservado para él")
    fun seesAvatarWithoutCategory(avatar: String) {
        val order = (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.single()
        assertEquals("AP", providerInitials(order.consumerGivenName, order.consumerSurname))
        assertEquals(if (photoCase == "disponible") "la foto de Ana" else "las iniciales AP", avatar)
    }

    @Then("veo el listado completo de mis órdenes de trabajo")
    fun seesAllOrders() {
        assertEquals(orders, (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders)
    }

    @And("las propuestas conservan su acceso separado")
    fun proposalsStaySeparate() {
        assertNotEquals(Route.ProviderTurns.path, Route.ServiceProposals.path)
    }

    @After fun tearDown() { pendingOrders?.cancel(); Dispatchers.resetMain() }
}
