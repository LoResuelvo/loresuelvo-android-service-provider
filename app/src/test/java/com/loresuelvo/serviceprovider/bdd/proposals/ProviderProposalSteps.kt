package com.loresuelvo.serviceprovider.bdd.proposals

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProposalUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderProposalViewModel
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ProviderProposalSteps {
    private val viewModel = ProviderProposalViewModel(
        SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
    )
    private lateinit var activeChat: ConversationDetail

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
}
