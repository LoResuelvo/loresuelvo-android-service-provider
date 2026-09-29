package com.loresuelvo.serviceprovider.ui.turns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.activity.linkedConversationId
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderWorkOrderDetailUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.ProviderWorkOrderDetailResult
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderTurnDetailUiState {
    data object Loading : ProviderTurnDetailUiState
    data class Ready(val result: ProviderWorkOrderDetailResult, val conversationId: Int?) : ProviderTurnDetailUiState
    data class Error(val failure: WorkOrderDetailOutcome.Failure) : ProviderTurnDetailUiState
}

@HiltViewModel
class ProviderTurnDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getDetail: GetProviderWorkOrderDetailUseCase,
    private val getProposals: GetServiceProposalsUseCase,
    private val sessions: AuthSessionStore,
) : ViewModel() {
    val orderId: Int = savedStateHandle["turnId"] ?: 0
    val selectedFileId = savedStateHandle.getStateFlow<String?>("selectedFileId", null)
    private var initialResumePending = true
    private var loadJob: Job? = null
    private var activeSession = sessions.getSession()
    private val _uiState = MutableStateFlow<ProviderTurnDetailUiState>(ProviderTurnDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    loadJob?.cancel()
                    savedStateHandle["selectedFileId"] = null
                    _uiState.value = ProviderTurnDetailUiState.Error(WorkOrderDetailOutcome.Failure.Unauthorized)
                }
            }
        }
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        val requestSession = sessions.getSession()
        if (requestSession == null || orderId <= 0) {
            _uiState.value = ProviderTurnDetailUiState.Error(WorkOrderDetailOutcome.Failure.Unauthorized)
            return
        }
        _uiState.value = ProviderTurnDetailUiState.Loading
        loadJob = viewModelScope.launch {
            val result = getDetail(orderId)
            if (sessions.getSession() != requestSession) return@launch
            _uiState.value = when (val detail = result.detail) {
                is WorkOrderDetailOutcome.Success -> {
                    keepSelectedFileIfPresent(detail)
                    val proposals = getProposals()
                    if (sessions.getSession() != requestSession) return@launch
                    if (proposals == ServiceProposalListOutcome.Failure.SessionExpired) {
                        sessions.clearSession()
                        ProviderTurnDetailUiState.Error(WorkOrderDetailOutcome.Failure.Unauthorized)
                    } else ProviderTurnDetailUiState.Ready(result,
                        result.consumer?.linkedConversationId(
                            (proposals as? ServiceProposalListOutcome.Success)?.proposals.orEmpty()))
                }
                is WorkOrderDetailOutcome.Failure -> {
                    if (detail == WorkOrderDetailOutcome.Failure.Unauthorized) sessions.clearSession()
                    ProviderTurnDetailUiState.Error(detail)
                }
            }
        }
    }

    fun onResume() {
        if (initialResumePending) initialResumePending = false else load()
    }

    fun selectFile(fileId: String) {
        val current = (_uiState.value as? ProviderTurnDetailUiState.Ready)?.result?.detail
            as? WorkOrderDetailOutcome.Success ?: return
        if (current.order.status != WorkOrderStatus.AwaitingPayment &&
            current.order.status != WorkOrderStatus.Paid) return
        if (current.order.completionReport?.images?.count { it.fileId == fileId } == 1) {
            savedStateHandle["selectedFileId"] = fileId
        }
    }

    fun closeViewer() { savedStateHandle["selectedFileId"] = null }

    fun retryPhoto(fileId: String) {
        val current = _uiState.value as? ProviderTurnDetailUiState.Ready ?: return
        val order = (current.result.detail as? WorkOrderDetailOutcome.Success)?.order ?: return
        if (order.completionReport?.images?.count { it.fileId == fileId } != 1 ||
            loadJob?.isActive == true) return
        val requestSession = sessions.getSession() ?: return
        loadJob = viewModelScope.launch {
            val result = getDetail(orderId)
            if (sessions.getSession() != requestSession) return@launch
            when (val detail = result.detail) {
                is WorkOrderDetailOutcome.Success -> {
                    keepSelectedFileIfPresent(detail)
                    _uiState.value = current.copy(result = result)
                }
                WorkOrderDetailOutcome.Failure.Unauthorized -> sessions.clearSession()
                WorkOrderDetailOutcome.Failure.Forbidden, WorkOrderDetailOutcome.Failure.NotFound ->
                    _uiState.value = ProviderTurnDetailUiState.Error(detail as WorkOrderDetailOutcome.Failure)
                else -> Unit
            }
        }
    }

    private fun keepSelectedFileIfPresent(detail: WorkOrderDetailOutcome.Success) {
        if (selectedFileId.value != null && detail.order.completionReport?.images
                ?.count { it.fileId == selectedFileId.value } != 1) {
            savedStateHandle["selectedFileId"] = null
        }
    }
}
