package com.loresuelvo.serviceprovider.bdd.turns

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsViewModel
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.cucumber.java.en.Then
import io.cucumber.java.en.And
import io.cucumber.datatable.DataTable
import java.time.Instant
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

    @Then("veo las órdenes 40, 30, 11 y 12 en ese orden")
    fun seesDeterministicOrder() {
        assertEquals(listOf(40, 30, 11, 12), (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.map { it.id })
    }

    @And("no veo propuestas pendientes o rechazadas como órdenes de trabajo")
    fun noProposalsInOrders() {
        assertEquals(4, (viewModel.uiState.value as ProviderTurnsUiState.Ready).orders.size)
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
