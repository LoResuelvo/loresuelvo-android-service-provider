package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationOutcome
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalDraft
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.proposal.ValidateServiceProposalUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.CreateServiceProposalUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
    data class Reviewing(
        val form: Form,
        val proposal: ValidatedServiceProposal,
        val failure: CreateServiceProposalOutcome.Failure? = null,
        val duplicateRiskAcknowledged: Boolean = false,
    ) : ProposalUiState
    data class Sending(val reviewing: Reviewing) : ProposalUiState
}

@HiltViewModel
class ProviderProposalViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val validateProposal: ValidateServiceProposalUseCase,
    private val timeSource: ProposalTimeSource,
    private val createProposal: CreateServiceProposalUseCase,
) : ViewModel() {
    private val conversationId = checkNotNull(savedStateHandle.get<Int>(Route.Conversation.argument))
    private val state = kotlinx.coroutines.flow.MutableStateFlow<ProposalUiState>(ProposalUiState.Closed)
    private var blockedFailure: CreateServiceProposalOutcome.Failure? = null
    val uiState: StateFlow<ProposalUiState> = state.asStateFlow()
    private val successPending = MutableStateFlow(false)
    val hasSuccess: StateFlow<Boolean> = successPending.asStateFlow()

    fun consumeSuccess(): Boolean {
        if (!successPending.value) return false
        successPending.value = false
        return true
    }

    fun open(detail: ConversationDetail): Boolean {
        if (state.value is ProposalUiState.Sending) return false
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
                state.value = ProposalUiState.Reviewing(
                    form.copy(errors = emptySet()), result.proposal,
                    failure = if (savedStateHandle.get<Boolean>("proposal_send_uncertain") == true)
                        CreateServiceProposalOutcome.Failure.Uncertain else blockedFailure,
                )
                true
            }
        }
    }

    fun acknowledgeDuplicateRisk() {
        val reviewing = state.value as? ProposalUiState.Reviewing ?: return
        if (reviewing.failure == CreateServiceProposalOutcome.Failure.Uncertain) {
            state.value = reviewing.copy(duplicateRiskAcknowledged = true)
        }
    }

    fun confirmSend() {
        val reviewing = state.value as? ProposalUiState.Reviewing ?: return
        if (reviewing.failure != null &&
            (reviewing.failure != CreateServiceProposalOutcome.Failure.Uncertain ||
                !reviewing.duplicateRiskAcknowledged)) return
        val form = reviewing.form
        val draft = ServiceProposalDraft(form.consumerId, form.amount, form.date, form.time,
            form.reason, form.duration, form.selectedOffsetMinutes)
        val validated = validateProposal(draft, timeSource.nowMillis(), TimeZone.getTimeZone(form.zoneId))
        if (validated is ProposalValidationOutcome.Invalid) {
            state.value = form.copy(errors = validated.errors)
            return
        }
        val proposal = (validated as ProposalValidationOutcome.Valid).proposal
        state.value = ProposalUiState.Sending(reviewing)
        savedStateHandle["proposal_send_uncertain"] = true
        viewModelScope.launch {
            val outcome = try { createProposal(proposal) } catch (e: CancellationException) { throw e }
            when (outcome) {
                is CreateServiceProposalOutcome.Created -> {
                    clearDraft()
                    blockedFailure = null
                    state.value = ProposalUiState.Closed
                    successPending.value = true
                }
                is CreateServiceProposalOutcome.Failure -> {
                    if (outcome != CreateServiceProposalOutcome.Failure.Uncertain) {
                        savedStateHandle["proposal_send_uncertain"] = false
                    }
                    if (outcome != CreateServiceProposalOutcome.Failure.Uncertain &&
                        outcome !is CreateServiceProposalOutcome.Failure.Invalid) {
                        blockedFailure = outcome
                    }
                    state.value = reviewing.copy(failure = outcome, duplicateRiskAcknowledged = false)
                }
            }
        }
    }

    fun close() {
        if (state.value is ProposalUiState.Sending) return
        state.value = ProposalUiState.Closed
    }

    fun cancelReview() {
        val reviewing = state.value as? ProposalUiState.Reviewing ?: return
        state.value = reviewing.form.copy(
            errors = (reviewing.failure as? CreateServiceProposalOutcome.Failure.Invalid)?.errors ?: emptySet(),
        )
    }

    private fun update(key: String, value: String, change: ProposalUiState.Form.() -> ProposalUiState.Form) {
        val form = state.value as? ProposalUiState.Form ?: return
        if (blockedFailure == CreateServiceProposalOutcome.Failure.Rejected) blockedFailure = null
        savedStateHandle[key] = value
        if (key == "proposal_date" || key == "proposal_time") savedStateHandle["proposal_offset_minutes"] = null
        state.value = form.change().copy(errors = emptySet())
    }

    private fun clearDraft() {
        listOf("proposal_amount", "proposal_date", "proposal_time", "proposal_reason",
            "proposal_duration", "proposal_custom_duration", "proposal_zone_id",
            "proposal_offset_minutes", "proposal_send_uncertain").forEach { savedStateHandle.remove<Any>(it) }
    }
}

open class ProposalTimeSource @Inject constructor() {
    open fun nowMillis(): Long = System.currentTimeMillis()
    open fun zone(): TimeZone = TimeZone.getDefault()
}
