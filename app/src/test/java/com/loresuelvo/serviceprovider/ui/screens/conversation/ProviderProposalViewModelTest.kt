package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.usecase.proposal.ValidateServiceProposalUseCase
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderProposalViewModelTest {
    private val validator = ValidateServiceProposalUseCase()
    private val clock = object : ProposalTimeSource() {
        override fun nowMillis() = 1_780_000_000_000L
        override fun zone() = java.util.TimeZone.getTimeZone("UTC")
    }
    @Test
    fun `active chat opens a form for its consumer without confusing the IDs`() {
        val viewModel = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
            validator, clock,
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
            validator, clock,
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
        val viewModel = ProviderProposalViewModel(handle, validator, clock)
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

    @Test fun `invalid amount stays in form until corrected`() {
        val viewModel = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)), validator, clock)
        viewModel.open(detail(42, 7, ConversationStatus.Active))
        viewModel.updateAmount("0")
        viewModel.updateDate("2026-10-01")
        viewModel.updateTime("10:00")
        viewModel.updateReason("Inspect sink")
        viewModel.selectDuration(45)

        assertFalse(viewModel.continueToConfirmation())
        assertEquals(setOf(ProposalValidationError.Amount),
            (viewModel.uiState.value as ProposalUiState.Form).errors)
        viewModel.updateAmount("100,50")
        assertTrue(viewModel.continueToConfirmation())
        assertEquals(emptySet<ProposalValidationError>(),
            (viewModel.uiState.value as ProposalUiState.Reviewing).form.errors)
    }

    @Test fun `captured draft zone survives source zone change and reopening`() {
        var currentZone = java.util.TimeZone.getTimeZone("America/New_York")
        val source = object : ProposalTimeSource() {
            override fun nowMillis() = clock.nowMillis()
            override fun zone() = currentZone
        }
        val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
        val viewModel = ProviderProposalViewModel(handle, validator, source)
        val chat = detail(42, 7, ConversationStatus.Active)
        viewModel.open(chat)
        currentZone = java.util.TimeZone.getTimeZone("UTC")
        viewModel.close()
        viewModel.open(chat)
        assertEquals("America/New_York", (viewModel.uiState.value as ProposalUiState.Form).zoneId)
        assertEquals("America/New_York", handle.get<String>("proposal_zone_id"))
    }

    @Test fun `valid draft opens review with normalized details before any send`() {
        val viewModel = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)), validator, clock)
        viewModel.open(detail(42, 7, ConversationStatus.Active))
        viewModel.updateAmount("100,50")
        viewModel.updateDate("2026-10-01")
        viewModel.updateTime("10:00")
        viewModel.updateReason("  Inspect sink  ")
        viewModel.selectDuration(45)

        assertTrue(viewModel.continueToConfirmation())
        val review = viewModel.uiState.value as ProposalUiState.Reviewing
        assertEquals("Ana Pérez", review.form.consumerName)
        assertEquals(7, review.proposal.consumerId)
        assertEquals("100.5", review.proposal.amountPesos)
        assertEquals("Inspect sink", review.proposal.reason)
        assertEquals(45, review.proposal.durationMinutes)
        assertEquals("UTC", review.form.zoneId)
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
