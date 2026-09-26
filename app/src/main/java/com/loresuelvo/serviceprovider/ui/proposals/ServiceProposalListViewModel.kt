package com.loresuelvo.serviceprovider.ui.proposals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

enum class ProposalTab(val status: ServiceProposalStatus) {
    Pending(ServiceProposalStatus.Pending), Accepted(ServiceProposalStatus.Accepted),
    Rejected(ServiceProposalStatus.Rejected),
}

data class ServiceProposalListUiState(
    val selectedTab: ProposalTab = ProposalTab.Pending,
    val proposals: List<ServiceProposalSummary> = emptyList(),
    val loading: Boolean = true,
    val failure: ServiceProposalListOutcome.Failure? = null,
) {
    val visibleProposals: List<ServiceProposalSummary>
        get() = proposals.filter { it.status == selectedTab.status }

    fun proposalInConversation(conversationId: Int): ServiceProposalSummary? =
        proposals.firstOrNull { it.conversationId == conversationId }
}

@HiltViewModel
class ServiceProposalListViewModel @Inject constructor(
    private val getServiceProposals: GetServiceProposalsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ServiceProposalListUiState())
    val uiState: StateFlow<ServiceProposalListUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var initialResumePending = true

    init { load() }

    fun select(tab: ProposalTab) { _uiState.update { it.copy(selectedTab = tab) } }

    fun onResume() {
        if (initialResumePending) {
            initialResumePending = false
            return
        }
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(loading = true, failure = null) }
        loadJob = viewModelScope.launch {
            val result = getServiceProposals()
            _uiState.update { state ->
                when (result) {
                    is ServiceProposalListOutcome.Success -> state.copy(
                        proposals = result.proposals,
                        loading = false,
                    )
                    is ServiceProposalListOutcome.Failure -> state.copy(
                        proposals = emptyList(),
                        loading = false,
                        failure = result,
                    )
                }
            }
        }
    }
}
