package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderProposalViewModelTest {
    @Test
    fun `active chat opens a form for its consumer without confusing the IDs`() {
        val viewModel = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
        )
        viewModel.open(detail(42, 7, ConversationStatus.Active))

        val form = viewModel.uiState.value as ProposalUiState.Form
        assertEquals(42, form.conversationId)
        assertEquals(7, form.consumerId)
        assertEquals("Ana Pérez", form.consumerName)
        assertEquals("", form.amount)
        assertEquals("", form.date)
        assertEquals("", form.time)
        assertEquals("", form.reason)
        assertEquals("", form.duration)
    }

    @Test
    fun `rejects a detail from another chat and nonactive states`() {
        val viewModel = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
        )
        assertFalse(viewModel.open(detail(43, 7, ConversationStatus.Active)))
        assertFalse(viewModel.open(detail(42, 7, ConversationStatus.Pending)))
        assertFalse(viewModel.open(detail(42, 7, ConversationStatus.Rejected)))
        assertFalse(viewModel.open(detail(42, 7, ConversationStatus.Unsupported("other"))))
        assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
    }

    @Test
    fun `editing persists across close and duration can switch between preset and custom`() {
        val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
        val viewModel = ProviderProposalViewModel(handle)
        val chat = detail(42, 7, ConversationStatus.Active)
        viewModel.open(chat)
        viewModel.updateAmount("100")
        viewModel.updateDate("2026-09-25")
        viewModel.updateTime("10:00")
        viewModel.updateReason("Inspect the sink")
        viewModel.selectDuration(45)
        assertEquals("45", (viewModel.uiState.value as ProposalUiState.Form).duration)
        viewModel.selectDuration(null)
        viewModel.updateCustomDuration("75")
        viewModel.close()
        assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
        viewModel.open(chat)
        val form = viewModel.uiState.value as ProposalUiState.Form
        assertEquals("100", form.amount)
        assertEquals("2026-09-25", form.date)
        assertEquals("10:00", form.time)
        assertEquals("Inspect the sink", form.reason)
        assertEquals("75", form.duration)
        assertTrue(form.customDuration)
        assertEquals("75", handle.get<String>("proposal_duration"))
    }

    private fun detail(conversationId: Int, consumerId: Int, status: ConversationStatus) =
        ConversationDetail(
            id = conversationId,
            status = status,
            counterpart = ConversationCounterpart(consumerId, "Ana", "Pérez", null),
            messages = emptyList(),
            updatedOnEpochMillis = 1L,
        )
}
