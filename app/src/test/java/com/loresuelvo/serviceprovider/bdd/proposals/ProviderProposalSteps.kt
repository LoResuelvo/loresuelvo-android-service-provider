package com.loresuelvo.serviceprovider.bdd.proposals

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProposalUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderProposalViewModel
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProposalTimeSource
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.usecase.proposal.ValidateServiceProposalUseCase
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.canCreateProposal
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ProviderProposalSteps {
    private val viewModel = ProviderProposalViewModel(
        SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
        ValidateServiceProposalUseCase(),
        object : ProposalTimeSource() {
            override fun nowMillis() = 1_780_000_000_000L
            override fun zone() = java.util.TimeZone.getTimeZone("UTC")
        },
    )
    private lateinit var activeChat: ConversationDetail
    private lateinit var nonActiveStates: List<ProviderConversationUiState>
    private lateinit var proposalActionsAvailable: List<Boolean>

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
        // The only reachable production state is the editing form; submission is not available here.
        assertTrue(viewModel.uiState.value is ProposalUiState.Form)
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
        assertTrue(viewModel.uiState.value is ProposalUiState.Reviewing)
    }
}
