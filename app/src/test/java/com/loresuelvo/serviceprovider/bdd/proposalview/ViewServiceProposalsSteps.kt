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
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.domain.conversation.*
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationByIdUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMessageUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMediaMessageUseCase
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationViewModel
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.ChatListItem
import com.loresuelvo.serviceprovider.data.media.MediaReader
import com.loresuelvo.serviceprovider.data.media.AudioRecorder
import com.loresuelvo.serviceprovider.data.media.AudioPlayer
import androidx.lifecycle.SavedStateHandle
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
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
    private var openedConversationPath: String? = null
    private var conversationSummary: ServiceProposalSummary? = null
    private var conversationViewModel: ProviderConversationViewModel? = null
    private var returnedView = ""
    private var proposalListCalls = 0

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
            override suspend fun list(): ServiceProposalListOutcome {
                proposalListCalls++
                return ServiceProposalListOutcome.Success(proposals)
            }
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

    @Given("que el detalle abierto corresponde a la propuesta {int} para el consumidor {int} y la conversación {int}")
    fun detailBelongsToConversation(proposalId: Int, consumerId: Int, conversationId: Int) {
        consumerProposal("Ana Pérez", 1500050)
        openJobs()
        detailProposal = viewModel.uiState.value.visibleProposals.single()
        assertEquals(proposalId, detailProposal?.id)
        assertEquals(consumerId, detailProposal?.counterpart?.id)
        assertEquals(conversationId, detailProposal?.conversationId)
    }

    @When("elijo {string}")
    fun chooseConversation(action: String) {
        assertEquals("Ver conversación", action)
        openedConversationPath = Route.Conversation.buildPath(requireNotNull(detailProposal).conversationId)
    }

    @Then("se abre la conversación {int}")
    fun conversationOpens(conversationId: Int) {
        assertEquals(Route.Conversation.buildPath(conversationId), openedConversationPath)
    }

    @And("no se abre la conversación {int} ni la conversación {int}")
    fun otherConversationsDoNotOpen(first: Int, second: Int) {
        assertNotEquals(Route.Conversation.buildPath(first), openedConversationPath)
        assertNotEquals(Route.Conversation.buildPath(second), openedConversationPath)
    }

    @Given("que las propuestas son:")
    fun conversationProposalsAre(table: DataTable) {
        proposals = table.asMaps().map { row ->
            summaries(listOf(mapOf(
                "id" to row.getValue("id"),
                "estado" to row.getValue("estado"),
                "fecha de creación" to row.getValue("fecha de creación"),
                "fecha de visita" to "2026-10-05T00:30:00Z",
            ))).single().copy(
                conversationId = row.getValue("id conversación").toInt(),
                amountCents = row.getValue("id").toLong() * 100,
                description = "Visit reason for proposal ${row.getValue("id")}",
            )
        }
    }

    @When("abro la conversación 93")
    fun openConversation93() {
        openJobs()
        conversationSummary = viewModel.uiState.value.proposalInConversation(93)
    }

    @Then("su resumen muestra el monto, la fecha y hora local de visita, el motivo y el estado pendiente de la propuesta 22")
    fun latestProposalSummary() {
        val summary = requireNotNull(conversationSummary)
        assertEquals(22, summary.id)
        assertEquals(2200L, summary.amountCents)
        assertEquals(Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(), summary.scheduledOnEpochMillis)
        assertEquals("Visit reason for proposal 22", summary.description)
        assertEquals(ServiceProposalStatus.Pending, summary.status)
    }

    @And("puedo abrir el detalle de la propuesta 22")
    fun latestProposalDetail() {
        detailProposal = conversationSummary
        assertEquals(22, detailProposal?.id)
    }

    @And("las propuestas 20, 21 y 99 no aparecen en el resumen")
    fun onlyLatestProposalIsSummarized() {
        assertFalse(conversationSummary?.id in setOf(20, 21, 99))
        assertEquals(4, viewModel.uiState.value.proposals.size)
        assertEquals(94, proposals.single { it.id == 99 }.conversationId)
    }

    @Given("que la conversación 93 tiene mensajes y ninguna propuesta")
    fun conversationHasMessagesWithoutProposals() {
        proposals = emptyList()
        val repository = object : ConversationRepository {
            override suspend fun getConversations(): ConversationsOutcome = error("Not needed")
            override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome {
                assertEquals(93, conversationId)
                return ConversationDetailOutcome.Success(ConversationDetail(
                    93, ConversationStatus.Active,
                    ConversationCounterpart(7, "Ana", "Pérez", null),
                    listOf(ConversationMessage(1, ConversationSender.Consumer, "Hola", 1L)), 1L,
                ))
            }
            override suspend fun sendMessage(conversationId: Int, content: String): SendMessageOutcome =
                error("Typing does not send")
        }
        conversationViewModel = ProviderConversationViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 93)),
            GetConversationByIdUseCase(repository),
            SendMessageUseCase(repository),
            SendMediaMessageUseCase(repository),
            object : MediaReader { override suspend fun read(uri: Uri): MediaUpload = error("Not needed") },
            object : AudioRecorder {
                override fun start(): Result<Unit> = error("Not needed")
                override fun stop(): Result<Uri> = error("Not needed")
                override fun cancel() = Unit
            },
            object : AudioPlayer {
                override val isPlaying = MutableStateFlow(false)
                override val currentPositionMillis = MutableStateFlow(0L)
                override fun play(url: String, startPositionMillis: Long) = Unit
                override fun pause() = Unit
                override fun stop() = Unit
            },
        ).also { viewModelStore.put("conversation", it) }
    }

    @Then("veo sus mensajes y puedo escribir un mensaje")
    fun messagesAndComposerRemainAvailable() {
        val conversation = requireNotNull(conversationViewModel)
        val ready = conversation.uiState.value as ProviderConversationUiState.Ready
        assertEquals("Hola", (ready.items.single() as ChatListItem.ServerConfirmed).content)
        conversation.onPromptChange("Llegaré a las 9")
        assertEquals("Llegaré a las 9", (conversation.uiState.value as ProviderConversationUiState.Ready).promptInput)
    }

    @And("no se muestra un resumen de propuesta")
    fun noProposalSummary() {
        assertTrue(conversationViewModel?.uiState?.value is ProviderConversationUiState.Ready)
        assertEquals(null, conversationSummary)
    }

    @Given("que anteriormente vi la propuesta 12 como pendiente en {string}")
    fun previouslySawPendingProposal(view: String) {
        assertTrue(view == "Trabajos" || view == "conversación 93")
        returnedView = view
        consumerProposal("Ana Pérez", 1500050)
        openJobs()
        viewModel.onResume()
        assertEquals(1, proposalListCalls)
        assertEquals(ServiceProposalStatus.Pending, viewModel.uiState.value.proposalInConversation(93)?.status)
    }

    @And("salí de esa vista")
    fun leftView() { assertTrue(returnedView.isNotEmpty()) }

    @And("el servidor ahora informa que la propuesta 12 está aceptada")
    fun serverNowAcceptsProposal() {
        proposals = proposals.map { it.copy(status = ServiceProposalStatus.Accepted) }
    }

    @When("regreso a {string}")
    fun returnToView(view: String) {
        assertEquals(returnedView, view)
        viewModel.onResume()
        scope.advanceUntilIdle()
        assertEquals(2, proposalListCalls)
    }

    @Then("veo {string}")
    fun seeUpdatedProposal(result: String) {
        val state = viewModel.uiState.value
        when (result) {
            "la pestaña Pendientes sin la propuesta 12" -> {
                assertEquals(ProposalTab.Pending, state.selectedTab)
                assertFalse(state.visibleProposals.any { it.id == 12 })
            }
            "el resumen de la propuesta 12 con el estado Aceptada" -> {
                assertEquals(12, state.proposalInConversation(93)?.id)
                assertEquals(ServiceProposalStatus.Accepted, state.proposalInConversation(93)?.status)
            }
            else -> error("Unexpected result: $result")
        }
    }
}
