package com.loresuelvo.serviceprovider.ui.turns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.activity.linkedConversationId
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

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
    private val sessionStore: AuthSessionStore,
) : ViewModel() {
    private var initialResumePending = true
    private var activeSession = sessionStore.getSession()
    private var loadJob: Job? = null
    private var conversationJob: Job? = null
    private val _uiState = MutableStateFlow<ProviderTurnsUiState>(ProviderTurnsUiState.Loading)
    val uiState: StateFlow<ProviderTurnsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    loadJob?.cancel()
                    conversationJob?.cancel()
                    _uiState.value = ProviderTurnsUiState.Error
                }
            }
        }
        load()
    }

    fun onResume() {
        if (initialResumePending) {
            initialResumePending = false
            return
        }
        load(preserveContent = true)
    }

    fun load(preserveContent: Boolean = false) {
        if (loadJob?.isActive == true) return
        val requestSession = sessionStore.getSession()
        if (requestSession == null) {
            _uiState.value = ProviderTurnsUiState.Error
            sessionStore.clearSession()
            return
        }
        if (!preserveContent || _uiState.value !is ProviderTurnsUiState.Ready) {
            _uiState.value = ProviderTurnsUiState.Loading
        }
        loadJob = viewModelScope.launch {
            _uiState.value = when (val result = getProviderTurns()) {
                is ActivityLoadOutcome.Success -> {
                    val proposalResult = getServiceProposals()
                    if (sessionStore.getSession() != requestSession) return@launch
                    if (proposalResult == ServiceProposalListOutcome.Failure.SessionExpired) {
                        _uiState.value = ProviderTurnsUiState.Error
                        sessionStore.clearSession()
                        return@launch
                    }
                    val proposals = (proposalResult as? ServiceProposalListOutcome.Success)?.proposals.orEmpty()
                    ProviderTurnsUiState.Ready(result.items, result.items.mapNotNull { order ->
                        order.linkedConversationId(proposals)?.let { order.id to it }
                    }.toMap(), proposalResult as? ServiceProposalListOutcome.Failure)
                }
                is ActivityLoadOutcome.Failure -> {
                    if (sessionStore.getSession() != requestSession) return@launch
                    if (result == ActivityLoadOutcome.Failure.Unauthorized) sessionStore.clearSession()
                    ProviderTurnsUiState.Error
                }
            }
        }
    }

    fun retryConversation(orderId: Int) {
        val current = _uiState.value as? ProviderTurnsUiState.Ready ?: return
        if (current.resolvingConversation || current.conversationToOpen != null) return
        val requestSession = sessionStore.getSession() ?: return
        val order = current.orders.firstOrNull { it.id == orderId } ?: return
        _uiState.value = current.copy(resolvingConversation = true)
        conversationJob = viewModelScope.launch {
            when (val result = getServiceProposals()) {
                is ServiceProposalListOutcome.Success -> {
                    if (sessionStore.getSession() != requestSession) return@launch
                    val id = order.linkedConversationId(result.proposals)
                    _uiState.value = current.copy(conversationIds = if (id == null) current.conversationIds
                        else current.conversationIds + (orderId to id), proposalFailure = null,
                        resolvingConversation = false, conversationToOpen = id)
                }
                is ServiceProposalListOutcome.Failure -> {
                    if (sessionStore.getSession() != requestSession) return@launch
                    _uiState.value = current.copy(proposalFailure = result, resolvingConversation = false)
                }
            }
        }
    }

    fun conversationOpened() {
        val current = _uiState.value as? ProviderTurnsUiState.Ready ?: return
        _uiState.value = current.copy(conversationToOpen = null)
    }
}
