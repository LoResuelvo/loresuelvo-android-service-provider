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
import java.util.Locale
import java.util.TimeZone

class ViewServiceProposalsSteps {
    private val scope = TestScope(StandardTestDispatcher())
    private val viewModelStore = ViewModelStore()
    private var authenticated = false
    private var proposals = emptyList<ServiceProposalSummary>()
    private lateinit var today: Instant
    private lateinit var viewModel: ServiceProposalListViewModel
    private var previousLocale: Locale? = null
    private var previousZone: TimeZone? = null
    private var detailProposal: ServiceProposalSummary? = null
    private var detailView = ""
    private var historyPosition = -1

    @Before
    fun setUp() { Dispatchers.setMain(StandardTestDispatcher(scope.testScheduler)) }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
        previousLocale?.let(Locale::setDefault)
        previousZone?.let(TimeZone::setDefault)
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

    @Given("que una propuesta pendiente para la consumidora {string} tiene un monto de {long} centavos")
    fun consumerProposal(name: String, amount: Long) {
        assertEquals("Ana Pérez", name)
        proposals = summaries(listOf(mapOf(
            "id" to "12", "estado" to "pending", "fecha de creación" to "2026-09-21T12:00:00Z",
            "fecha de visita" to "2026-10-05T00:30:00Z",
        ))).map { it.copy(amountCents = amount) }
    }

    @And("su visita es el {string} y su motivo es {string}")
    fun visitAndReason(visit: String, reason: String) {
        proposals = proposals.map { it.copy(
            scheduledOnEpochMillis = Instant.parse(visit).toEpochMilli(), description = reason,
        ) }
    }

    @And("la foto de perfil de Ana está {string}")
    fun photoIs(availability: String) {
        val url = when (availability) {
            "disponible" -> "https://example.test/ana.jpg"
            "ausente" -> null
            "inaccesible por un error" -> "https://example.test/unreachable.jpg"
            else -> error("Unexpected photo availability: $availability")
        }
        proposals = proposals.map { it.copy(counterpart = it.counterpart.copy(profilePhotoUrl = url)) }
    }

    @And("mi configuración regional es español de Argentina y mi zona horaria es {string}")
    fun argentineSettings(zone: String) {
        previousLocale = Locale.getDefault()
        previousZone = TimeZone.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-AR"))
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @When("abro Trabajos")
    fun openJobs() = openAll("Ver todas", "Trabajos")

    @Then("la tarjeta muestra {string}, ARS 15.000,50, el 4 de octubre de 2026 a las 21:30, el motivo y {string}")
    fun cardShowsProposal(name: String, status: String) {
        val proposal = viewModel.uiState.value.visibleProposals.single()
        assertEquals(name, "${proposal.counterpart.name} ${proposal.counterpart.surname}")
        assertEquals(1500050L, proposal.amountCents)
        assertEquals(Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(), proposal.scheduledOnEpochMillis)
        assertEquals("Reparar la canilla de la cocina", proposal.description)
        assertEquals("Pendiente", status)
        assertEquals(ServiceProposalStatus.Pending, proposal.status)
    }

    @And("la tarjeta muestra {string}")
    fun cardShowsAvatar(avatar: String) {
        val photo = viewModel.uiState.value.visibleProposals.single().counterpart.profilePhotoUrl
        if (avatar == "la foto de Ana") assertEquals("https://example.test/ana.jpg", photo)
        else {
            assertEquals("las iniciales AP", avatar)
            assertTrue(photo == null || photo.endsWith("unreachable.jpg"))
        }
    }

    @And("el nombre de la consumidora no tiene un rubro ni un espacio vacío reservado para él")
    fun noConsumerCategory() {
        assertEquals(null, viewModel.uiState.value.visibleProposals.single().counterpart.categoryName)
    }

    @Given("que la propuesta 12 tiene un motivo más largo que la vista previa de su tarjeta")
    fun proposalHasLongReason() {
        consumerProposal("Ana Pérez", 1500050)
        proposals = proposals.map { it.copy(
            description = "Reparar la canilla de la cocina y revisar todas las conexiones bajo la mesada",
        ) }
    }

    @And("su duración estimada es de {int} minutos")
    fun estimatedDuration(minutes: Int) {
        proposals = proposals.map { it.copy(estimatedDurationMinutes = minutes) }
    }

    @And("puedo ver la propuesta 12 en {string}")
    fun proposalIsVisibleIn(view: String) {
        assertTrue(view == "Trabajos" || view == "resumen del chat")
        detailView = view
        openJobs()
        val state = viewModel.uiState.value
        assertEquals(12, if (view == "Trabajos") state.visibleProposals.single().id
            else state.proposalInConversation(93)?.id)
    }

    @When("abro el detalle de la propuesta 12")
    fun openProposalDetail() {
        detailProposal = if (detailView == "Trabajos") {
            viewModel.uiState.value.visibleProposals.singleOrNull { it.id == 12 }
        } else {
            viewModel.uiState.value.proposalInConversation(93)
        }
        assertEquals(12, detailProposal?.id)
    }

    @Then("veo el consumidor, el motivo completo, el monto, la fecha y hora local de la visita y el estado")
    fun detailShowsAllTerms() {
        val detail = requireNotNull(detailProposal)
        assertEquals("Ana Pérez", "${detail.counterpart.name} ${detail.counterpart.surname}")
        assertEquals(proposals.single().description, detail.description)
        assertEquals(1500050L, detail.amountCents)
        assertEquals(proposals.single().scheduledOnEpochMillis, detail.scheduledOnEpochMillis)
        assertEquals(ServiceProposalStatus.Pending, detail.status)
    }

    @And("veo la duración {string}")
    fun detailShowsDuration(expected: String) {
        val minutes = requireNotNull(detailProposal).estimatedDurationMinutes
        assertEquals(mapOf(45 to "45 minutos", 60 to "1 hora", 90 to "1 hora 30 minutos")[minutes], expected)
    }

    @And("puedo elegir {string}")
    fun canViewConversation(action: String) {
        assertEquals("Ver conversación", action)
        assertEquals(93, requireNotNull(detailProposal).conversationId)
    }

    @And("no puedo aceptar, rechazar, pagar ni calificar la propuesta")
    fun detailIsReadOnly() {
        assertEquals(12, requireNotNull(detailProposal).id)
    }

    @Given("que abrí un detalle desde la pestaña {string} después de desplazarme hasta la propuesta 42")
    fun openScrolledAcceptedDetail(tab: String) {
        assertEquals("Aceptadas", tab)
        val accepted = summaries(listOf(mapOf(
            "id" to "1", "estado" to "accepted",
            "fecha de creación" to "2026-09-21T12:00:00Z",
            "fecha de visita" to "2026-10-05T12:00:00Z",
        ))).single()
        proposals = (1..60).map { accepted.copy(id = it) }
        openJobs()
        viewModel.select(ProposalTab.Accepted)
        historyPosition = viewModel.uiState.value.visibleProposals.indexOfFirst { it.id == 42 }
        assertTrue(historyPosition > 0)
        detailProposal = viewModel.uiState.value.visibleProposals[historyPosition]
    }

    @When("cierro el detalle con la acción Atrás")
    fun dismissHistoryDetail() {
        assertEquals(42, requireNotNull(detailProposal).id)
        detailProposal = null
    }

    @Then("la pestaña {string} sigue seleccionada")
    fun historyTabRemainsSelected(tab: String) {
        assertEquals("Aceptadas", tab)
        assertEquals(ProposalTab.Accepted, viewModel.uiState.value.selectedTab)
    }

    @And("la propuesta 42 permanece en la misma posición visible")
    fun historyPositionRemains() {
        assertEquals(historyPosition, viewModel.uiState.value.visibleProposals.indexOfFirst { it.id == 42 })
    }
}
