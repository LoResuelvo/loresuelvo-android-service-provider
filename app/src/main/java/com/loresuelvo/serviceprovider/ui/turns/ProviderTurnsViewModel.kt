package com.loresuelvo.serviceprovider.ui.turns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.activity.linkedConversationId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderTurnsUiState {
    data object Loading : ProviderTurnsUiState
    data class Ready(val orders: List<WorkOrder>, val conversationIds: Map<Int, Int> = emptyMap(),
        val proposalFailure: ServiceProposalListOutcome.Failure? = null,
        val resolvingConversation: Boolean = false,
        val conversationToOpen: Int? = null) : ProviderTurnsUiState
    data object Error : ProviderTurnsUiState
}

@HiltViewModel
class ProviderTurnsViewModel @Inject constructor(
    private val getProviderTurns: GetProviderTurnsUseCase,
    private val getServiceProposals: GetServiceProposalsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProviderTurnsUiState>(ProviderTurnsUiState.Loading)
    val uiState: StateFlow<ProviderTurnsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = ProviderTurnsUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = getProviderTurns()) {
                is ActivityLoadOutcome.Success -> {
                    val proposalResult = getServiceProposals()
                    val proposals = (proposalResult as? ServiceProposalListOutcome.Success)?.proposals.orEmpty()
                    ProviderTurnsUiState.Ready(result.items, result.items.mapNotNull { order ->
                        order.linkedConversationId(proposals)?.let { order.id to it }
                    }.toMap(), proposalResult as? ServiceProposalListOutcome.Failure)
                }
                is ActivityLoadOutcome.Failure -> ProviderTurnsUiState.Error
            }
        }
    }

    fun retryConversation(orderId: Int) {
        val current = _uiState.value as? ProviderTurnsUiState.Ready ?: return
        if (current.resolvingConversation || current.conversationToOpen != null) return
        val order = current.orders.firstOrNull { it.id == orderId } ?: return
        _uiState.value = current.copy(resolvingConversation = true)
        viewModelScope.launch {
            when (val result = getServiceProposals()) {
                is ServiceProposalListOutcome.Success -> {
                    val id = order.linkedConversationId(result.proposals)
                    _uiState.value = current.copy(conversationIds = if (id == null) current.conversationIds
                        else current.conversationIds + (orderId to id), proposalFailure = null,
                        resolvingConversation = false, conversationToOpen = id)
                }
                is ServiceProposalListOutcome.Failure -> _uiState.value = current.copy(proposalFailure = result,
                    resolvingConversation = false)
            }
        }
    }

    fun conversationOpened() {
        val current = _uiState.value as? ProviderTurnsUiState.Ready ?: return
        _uiState.value = current.copy(conversationToOpen = null)
    }
}
