package com.loresuelvo.serviceprovider.bdd.proposals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProposalUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderProposalViewModel
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProposalTimeSource
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.proposal.CreateServiceProposalUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.ValidateServiceProposalUseCase
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.canCreateProposal
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.java.Before
import io.cucumber.java.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.cancel

class ProviderProposalSteps {
    private val savedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
    private val testScope = TestScope(StandardTestDispatcher())
    private val viewModelStore = ViewModelStore()
    private val created = mutableListOf<ValidatedServiceProposal>()
    private var createResult: CreateServiceProposalOutcome = CreateServiceProposalOutcome.Created(9)
    private val creation = CreateServiceProposalUseCase(object : ServiceProposalRepository {
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
            created += proposal
            return createResult
        }
    })
    private val viewModel = ProviderProposalViewModel(
        savedStateHandle,
        ValidateServiceProposalUseCase(),
        object : ProposalTimeSource() {
            override fun nowMillis() = 1_780_000_000_000L
            override fun zone() = java.util.TimeZone.getTimeZone("UTC")
        },
        creation,
    )
    private lateinit var activeChat: ConversationDetail
    private lateinit var nonActiveStates: List<ProviderConversationUiState>
    private lateinit var proposalActionsAvailable: List<Boolean>
    private lateinit var draftBeforeReview: ProposalUiState.Form

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(testScope.testScheduler))
        viewModelStore.put("proposal", viewModel)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        testScope.cancel()
        Dispatchers.resetMain()
    }

    @Given("que estoy en un chat activo con un consumidor")
    fun activeConsumerChat() {
        activeChat = ConversationDetail(
            id = 42,
            status = ConversationStatus.Active,
            counterpart = ConversationCounterpart(7, "Ana", "Pérez", null),
            messages = emptyList(),
            updatedOnEpochMillis = 1L,
        )
    }

    @When("elijo Crear propuesta de servicio entre las acciones del chat")
    fun chooseCreateProposal() {
        assertTrue(viewModel.open(activeChat))
    }

    @Then("el formulario de propuesta identifica a ese consumidor")
    fun formIdentifiesConsumer() {
        val form = viewModel.uiState.value as ProposalUiState.Form
        assertEquals(42, form.conversationId)
        assertEquals(activeChat.counterpart.id, form.consumerId)
        assertEquals("Ana Pérez", form.consumerName)
    }

    @And("ofrece monto, fecha, hora, motivo de la visita y duración estimada")
    fun formOffersFields() {
        val form = viewModel.uiState.value as ProposalUiState.Form
        assertEquals("", form.amount)
        assertEquals("", form.date)
        assertEquals("", form.time)
        assertEquals("", form.reason)
        assertEquals("", form.duration)
        assertEquals(false, form.customDuration)
    }

    @Given("que el chat con el consumidor no está activo")
    fun nonActiveConsumerChat() {
        activeConsumerChat()
        nonActiveStates = listOf(
            ConversationStatus.Pending,
            ConversationStatus.Rejected,
            ConversationStatus.Unsupported("other"),
        ).map { status ->
            ProviderConversationUiState.Ready(
                detail = activeChat.copy(status = status), items = emptyList(),
                promptInput = "", sending = false,
            )
        } + listOf(
            ProviderConversationUiState.Loading,
            ProviderConversationUiState.Error(
                ConversationDetailOutcome.Failure.Network(RuntimeException("offline")),
            ),
        )
    }

    @When("abro las acciones del chat")
    fun openChatActions() {
        proposalActionsAvailable = nonActiveStates.map { it.canCreateProposal() }
    }

    @Then("Crear propuesta de servicio no está disponible")
    fun proposalActionIsUnavailable() {
        proposalActionsAvailable.forEach { assertFalse(it) }
    }

    @Given("que mi propuesta contiene un campo obligatorio inválido")
    fun invalidRequiredField() {
        activeConsumerChat()
        assertTrue(viewModel.open(activeChat))
        viewModel.updateAmount("0")
        viewModel.updateDate("2026-06-01")
        viewModel.updateTime("10:00")
        viewModel.updateReason("Inspect the sink")
        viewModel.selectDuration(45)
    }

    @When("intento continuar a la confirmación")
    fun attemptConfirmation() {
        assertFalse(viewModel.continueToConfirmation())
    }

    @Then("ese campo explica qué debo corregir")
    fun fieldExplainsCorrection() {
        assertTrue(ProposalValidationError.Amount in (viewModel.uiState.value as ProposalUiState.Form).errors)
    }

    @And("no se envía ninguna propuesta")
    fun noProposalIsSent() {
        assertEquals(0, created.size)
    }

    @Given("que mi propuesta contiene datos válidos de la visita")
    fun validVisitDraft() {
        activeConsumerChat()
        assertTrue(viewModel.open(activeChat))
        viewModel.updateAmount("100,50")
        viewModel.updateDate("2026-10-01")
        viewModel.updateTime("10:00")
        viewModel.updateReason("  Inspect sink  ")
        viewModel.selectDuration(45)
    }

    @When("elijo Enviar propuesta")
    fun chooseSendProposal() {
        assertTrue(viewModel.continueToConfirmation())
    }

    @Then("se me pide confirmar la propuesta")
    fun confirmationRequested() {
        val review = viewModel.uiState.value as ProposalUiState.Reviewing
        assertEquals(activeChat.counterpart.id, review.proposal.consumerId)
        assertEquals("Ana Pérez", review.form.consumerName)
        assertEquals("100.5", review.proposal.amountPesos)
        assertEquals("Inspect sink", review.proposal.reason)
        assertEquals(45, review.proposal.durationMinutes)
    }

    @And("todavía no se ha enviado ninguna propuesta")
    fun notSentBeforeConfirmation() {
        assertEquals(0, created.size)
    }

    @Given("que estoy revisando la confirmación de una propuesta")
    fun reviewingConfirmation() {
        validVisitDraft()
        viewModel.selectDuration(null)
        viewModel.updateCustomDuration("75")
        viewModel.selectOffset(0)
        assertTrue(viewModel.continueToConfirmation())
        draftBeforeReview = (viewModel.uiState.value as ProposalUiState.Reviewing).form
    }

    @When("cancelo la confirmación")
    fun cancelConfirmation() {
        viewModel.cancelReview()
    }

    @Then("puedo seguir editando la misma propuesta")
    fun sameDraftRemainsEditable() {
        assertEquals(draftBeforeReview, viewModel.uiState.value)
    }

    @Given("que estoy editando una propuesta sin enviar")
    fun editingUnsentProposal() {
        activeConsumerChat()
        assertTrue(viewModel.open(activeChat))
        viewModel.updateAmount("100,50")
        viewModel.updateReason("Inspect sink")
    }

    @When("cierro el formulario")
    fun closeProposalForm() {
        viewModel.close()
    }

    @Then("vuelvo al mismo chat sin enviar una propuesta")
    fun sameChatRemainsWithoutProposal() {
        assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
        assertEquals(0, created.size)
    }

    @Given("que una propuesta válida espera mi confirmación")
    fun validProposalAwaitsConfirmation() {
        validVisitDraft()
        assertTrue(viewModel.continueToConfirmation())
    }

    @And("el servicio confirmará su creación")
    fun serviceConfirmsCreation() {
        createResult = CreateServiceProposalOutcome.Created(9)
    }

    @When("confirmo el envío")
    fun confirmProposalSend() {
        viewModel.confirmSend()
        testScope.testScheduler.advanceUntilIdle()
    }

    @Then("vuelvo al chat con la confirmación Propuesta enviada")
    fun returnToChatWithConfirmation() {
        assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
        assertTrue(viewModel.hasSuccess.value)
        assertEquals(1, created.size)
    }

    @And("el formulario se limpia después de crear una propuesta pendiente")
    fun formIsClearedAfterPendingCreation() {
        listOf("proposal_amount", "proposal_date", "proposal_time", "proposal_reason",
            "proposal_duration", "proposal_send_uncertain").forEach {
            assertFalse(savedStateHandle.contains(it))
        }
    }
}
