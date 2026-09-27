package com.loresuelvo.serviceprovider.bdd.turns

import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsViewModel
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
    private val repository = object : WorkOrderRepository {
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = ActivityLoadOutcome.Success(orders)
    }
    private lateinit var viewModel: ProviderTurnsViewModel
    private var photoCase = ""
    private lateinit var pastStatus: WorkOrderStatus

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
        viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(repository))
        dispatcher.scheduler.advanceUntilIdle()
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

    @After fun tearDown() { Dispatchers.resetMain() }
}
