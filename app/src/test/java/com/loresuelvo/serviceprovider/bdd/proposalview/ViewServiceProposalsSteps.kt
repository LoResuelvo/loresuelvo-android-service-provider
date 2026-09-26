package com.loresuelvo.serviceprovider.bdd.proposalview

import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.ui.proposals.ProposalTab
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListViewModel
import androidx.lifecycle.ViewModelStore
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.java.Before
import io.cucumber.java.After
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import java.time.Instant

class ViewServiceProposalsSteps {
    private val scope = TestScope(StandardTestDispatcher())
    private val viewModelStore = ViewModelStore()
    private var authenticated = false
    private var proposals = emptyList<ServiceProposalSummary>()
    private lateinit var today: Instant
    private lateinit var viewModel: ServiceProposalListViewModel

    @Before
    fun setUp() { Dispatchers.setMain(StandardTestDispatcher(scope.testScheduler)) }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Given("que inicié sesión como prestador")
    fun signedIn() { authenticated = true }

    @Given("que mis propuestas son:")
    fun proposalsAre(table: DataTable) {
        proposals = summaries(table.asMaps())
    }

    private fun summaries(rows: List<Map<String, String>>) = rows.map { row ->
            ServiceProposalSummary(
                id = row.getValue("id").toInt(),
                conversationId = 93,
                amountCents = 1500050,
                scheduledOnEpochMillis = Instant.parse(row.getValue("fecha de visita")).toEpochMilli(),
                description = "Inspect sink",
                estimatedDurationMinutes = 45,
                status = when (row.getValue("estado")) {
                    "pending" -> ServiceProposalStatus.Pending
                    "accepted" -> ServiceProposalStatus.Accepted
                    "rejected" -> ServiceProposalStatus.Rejected
                    else -> error("Unexpected fixture status")
                },
                createdOnEpochMillis = Instant.parse(row.getValue("fecha de creación")).toEpochMilli(),
                counterpart = ServiceProposalCounterpart(7, "consumer", "Ana", "Pérez", null, null),
                bookingTerms = ServiceProposalBookingTerms(
                    "ARS", 1500050, 1000, 1499050, 500, 100, 400, 1100, 1499450, 1500550,
                    Instant.parse("2026-09-30T12:00:00Z").toEpochMilli(),
                ),
            )
        }

    @And("hoy es 26 de septiembre de 2026")
    fun todayIsSeptember26() { today = Instant.parse("2026-09-26T00:00:00Z") }

    @When("elijo {string} en la sección {string} de Inicio")
    fun openAll(viewAll: String, section: String) {
        assertTrue(authenticated)
        assertEquals("Ver todas", viewAll)
        assertEquals("Trabajos", section)
        val repository = object : ServiceProposalRepository {
            override suspend fun list() = ServiceProposalListOutcome.Success(proposals)
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
                error("Creation is outside this scenario")
        }
        viewModel = ServiceProposalListViewModel(GetServiceProposalsUseCase(repository))
        viewModelStore.put("proposals", viewModel)
        scope.advanceUntilIdle()
    }

    @Then("la pestaña {string} está seleccionada")
    fun selectedTab(tab: String) {
        assertEquals("Pendientes", tab)
        assertEquals(ProposalTab.Pending, viewModel.uiState.value.selectedTab)
    }

    @And("veo las pestañas {string}, {string} y {string}")
    fun tabs(first: String, second: String, third: String) {
        assertEquals(listOf("Pendientes", "Aceptadas", "Rechazadas"), listOf(first, second, third))
        assertEquals(
            listOf(ServiceProposalStatus.Pending, ServiceProposalStatus.Accepted, ServiceProposalStatus.Rejected),
            ProposalTab.entries.map { it.status },
        )
    }

    @And("veo las propuestas 12, 11 y 10 en ese orden")
    fun pendingOrder() = assertEquals(
        listOf(12, 11, 10),
        viewModel.uiState.value.visibleProposals.map { it.id },
    )

    @And("no veo las propuestas 13 ni 14")
    fun otherStatusesHidden() {
        val visible = viewModel.uiState.value.visibleProposals
        val ids = visible.map { it.id }
        assertFalse(13 in ids || 14 in ids)
        assertTrue(visible.single { it.id == 10 }.scheduledOnEpochMillis < today.toEpochMilli())
    }

    @Given("que Trabajos contiene propuestas pendientes, aceptadas y rechazadas")
    fun proposalsOfEveryStatus() {
        val headers = listOf("id", "estado", "fecha de creación", "fecha de visita")
        proposals = summaries(listOf(
            listOf("10", "pending", "2026-09-20T12:00:00Z", "2026-10-05T12:00:00Z"),
            listOf("11", "pending", "2026-09-21T12:00:00Z", "2026-10-05T12:00:00Z"),
            listOf("12", "pending", "2026-09-21T12:00:00Z", "2026-10-05T12:00:00Z"),
            listOf("20", "accepted", "2026-09-20T12:00:00Z", "2026-10-05T12:00:00Z"),
            listOf("21", "accepted", "2026-09-21T12:00:00Z", "2026-10-05T12:00:00Z"),
            listOf("22", "accepted", "2026-09-21T12:00:00Z", "2026-10-05T12:00:00Z"),
            listOf("30", "rejected", "2026-09-20T12:00:00Z", "2026-10-05T12:00:00Z"),
            listOf("31", "rejected", "2026-09-21T12:00:00Z", "2026-10-05T12:00:00Z"),
            listOf("32", "rejected", "2026-09-21T12:00:00Z", "2026-10-05T12:00:00Z"),
        ).map { headers.zip(it).toMap() })
    }

    @And("las propuestas de cada estado tienen fechas de creación distintas y fechas de creación iguales")
    fun creationDatesVaryAndTie() {
        ServiceProposalStatus.entries.forEach { status ->
            val dates = proposals.filter { it.status == status }.map { it.createdOnEpochMillis }
            assertEquals(3, dates.size)
            assertEquals(2, dates.distinct().size)
        }
    }

    @When("selecciono {string}")
    fun selectTab(label: String) {
        openAll("Ver todas", "Trabajos")
        viewModel.select(when (label) {
            "Pendientes" -> ProposalTab.Pending
            "Aceptadas" -> ProposalTab.Accepted
            "Rechazadas" -> ProposalTab.Rejected
            else -> error("Unexpected tab: $label")
        })
    }

    @Then("veo solamente las propuestas con estado {string}")
    fun onlyStatus(wireStatus: String) {
        val expected = when (wireStatus) {
            "pending" -> ServiceProposalStatus.Pending
            "accepted" -> ServiceProposalStatus.Accepted
            "rejected" -> ServiceProposalStatus.Rejected
            else -> error("Unexpected status: $wireStatus")
        }
        val visible = viewModel.uiState.value.visibleProposals
        assertEquals(3, visible.size)
        assertTrue(visible.all { it.status == expected })
        assertNotEquals(proposals.size, visible.size)
    }

    @And("aparecen primero las de creación más reciente y, en caso de empate, las de mayor ID")
    fun newestThenHighestId() {
        val ids = viewModel.uiState.value.visibleProposals.map { it.id }
        val base = when (viewModel.uiState.value.selectedTab) {
            ProposalTab.Pending -> 10
            ProposalTab.Accepted -> 20
            ProposalTab.Rejected -> 30
        }
        assertEquals(listOf(base + 2, base + 1, base), ids)
    }

    @And("cada tarjeta muestra el estado {string}")
    fun eachCardShowsStatus(label: String) {
        val expected = when (label) {
            "Pendiente" -> ServiceProposalStatus.Pending
            "Aceptada" -> ServiceProposalStatus.Accepted
            "Rechazada" -> ServiceProposalStatus.Rejected
            else -> error("Unexpected label: $label")
        }
        assertTrue(viewModel.uiState.value.visibleProposals.all { it.status == expected })
    }
}
