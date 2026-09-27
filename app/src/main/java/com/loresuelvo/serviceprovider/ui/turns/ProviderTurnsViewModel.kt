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
    data class Ready(val orders: List<WorkOrder>, val conversationIds: Map<Int, Int> = emptyMap()) : ProviderTurnsUiState
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
                    val proposals = (getServiceProposals() as? ServiceProposalListOutcome.Success)?.proposals.orEmpty()
                    ProviderTurnsUiState.Ready(result.items, result.items.mapNotNull { order ->
                        order.linkedConversationId(proposals)?.let { order.id to it }
                    }.toMap())
                }
                is ActivityLoadOutcome.Failure -> ProviderTurnsUiState.Error
            }
        }
    }
}
