package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalDraft
import com.loresuelvo.serviceprovider.domain.usecase.proposal.ValidateServiceProposalUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import java.util.TimeZone

sealed interface ProposalUiState {
    data object Closed : ProposalUiState
    data class Form(
        val conversationId: Int,
        val consumerId: Int,
        val consumerName: String,
        val amount: String = "",
        val date: String = "",
        val time: String = "",
        val reason: String = "",
        val duration: String = "",
        val customDuration: Boolean = false,
        val zoneId: String = "UTC",
        val selectedOffsetMinutes: Int? = null,
        val errors: Set<ProposalValidationError> = emptySet(),
    ) : ProposalUiState
}

@HiltViewModel
class ProviderProposalViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val validateProposal: ValidateServiceProposalUseCase,
    private val timeSource: ProposalTimeSource,
) : ViewModel() {
    private val conversationId = checkNotNull(savedStateHandle.get<Int>(Route.Conversation.argument))
    private val state = kotlinx.coroutines.flow.MutableStateFlow<ProposalUiState>(ProposalUiState.Closed)
    val uiState: StateFlow<ProposalUiState> = state.asStateFlow()

    fun open(detail: ConversationDetail): Boolean {
        if (detail.id != conversationId || detail.status != ConversationStatus.Active) return false
        state.value = ProposalUiState.Form(
            conversationId = detail.id,
            consumerId = detail.counterpart.id,
            consumerName = "${detail.counterpart.name} ${detail.counterpart.surname}",
            amount = savedStateHandle["proposal_amount"] ?: "",
            date = savedStateHandle["proposal_date"] ?: "",
            time = savedStateHandle["proposal_time"] ?: "",
            reason = savedStateHandle["proposal_reason"] ?: "",
            duration = savedStateHandle["proposal_duration"] ?: "",
            customDuration = savedStateHandle["proposal_custom_duration"] ?: false,
            zoneId = savedStateHandle.get<String>("proposal_zone_id") ?: timeSource.zone().id.also {
                savedStateHandle["proposal_zone_id"] = it
            },
            selectedOffsetMinutes = savedStateHandle["proposal_offset_minutes"],
        )
        return true
    }

    fun updateAmount(value: String) = update("proposal_amount", value) { copy(amount = value) }
    fun updateDate(value: String) = update("proposal_date", value) { copy(date = value, selectedOffsetMinutes = null) }
    fun updateTime(value: String) = update("proposal_time", value) { copy(time = value, selectedOffsetMinutes = null) }
    fun updateReason(value: String) = update("proposal_reason", value) { copy(reason = value) }
    fun selectDuration(minutes: Int?) {
        val form = state.value as? ProposalUiState.Form ?: return
        savedStateHandle["proposal_custom_duration"] = minutes == null
        savedStateHandle["proposal_duration"] = minutes?.toString() ?: ""
        state.value = form.copy(duration = minutes?.toString() ?: "", customDuration = minutes == null)
    }

    fun updateCustomDuration(value: String) = update("proposal_duration", value) { copy(duration = value) }

    fun selectOffset(minutes: Int) {
        val form = state.value as? ProposalUiState.Form ?: return
        savedStateHandle["proposal_offset_minutes"] = minutes
        state.value = form.copy(selectedOffsetMinutes = minutes)
    }

    fun continueToConfirmation(): Boolean {
        val form = state.value as? ProposalUiState.Form ?: return false
        val draft = ServiceProposalDraft(form.consumerId, form.amount, form.date, form.time,
            form.reason, form.duration, form.selectedOffsetMinutes)
        return when (val result = validateProposal(draft, timeSource.nowMillis(), TimeZone.getTimeZone(form.zoneId))) {
            is ProposalValidationOutcome.Invalid -> {
                state.value = form.copy(errors = result.errors)
                false
            }
            is ProposalValidationOutcome.Valid -> {
                state.value = form.copy(errors = emptySet())
                true
            }
        }
    }

    fun close() {
        state.value = ProposalUiState.Closed
    }

    private fun update(key: String, value: String, change: ProposalUiState.Form.() -> ProposalUiState.Form) {
        val form = state.value as? ProposalUiState.Form ?: return
        savedStateHandle[key] = value
        if (key == "proposal_date" || key == "proposal_time") savedStateHandle["proposal_offset_minutes"] = null
        state.value = form.change().copy(errors = emptySet())
    }
}

open class ProposalTimeSource @Inject constructor() {
    open fun nowMillis(): Long = System.currentTimeMillis()
    open fun zone(): TimeZone = TimeZone.getDefault()
}
