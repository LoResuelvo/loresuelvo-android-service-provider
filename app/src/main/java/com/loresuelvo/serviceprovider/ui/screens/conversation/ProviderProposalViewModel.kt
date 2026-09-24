package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

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
    ) : ProposalUiState
}

@HiltViewModel
class ProviderProposalViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
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
        )
        return true
    }

    fun updateAmount(value: String) = update("proposal_amount", value) { copy(amount = value) }
    fun updateDate(value: String) = update("proposal_date", value) { copy(date = value) }
    fun updateTime(value: String) = update("proposal_time", value) { copy(time = value) }
    fun updateReason(value: String) = update("proposal_reason", value) { copy(reason = value) }
    fun selectDuration(minutes: Int?) {
        val form = state.value as? ProposalUiState.Form ?: return
        savedStateHandle["proposal_custom_duration"] = minutes == null
        savedStateHandle["proposal_duration"] = minutes?.toString() ?: ""
        state.value = form.copy(duration = minutes?.toString() ?: "", customDuration = minutes == null)
    }

    fun updateCustomDuration(value: String) = update("proposal_duration", value) { copy(duration = value) }

    fun close() {
        state.value = ProposalUiState.Closed
    }

    private fun update(key: String, value: String, change: ProposalUiState.Form.() -> ProposalUiState.Form) {
        val form = state.value as? ProposalUiState.Form ?: return
        savedStateHandle[key] = value
        state.value = form.change()
    }
}
