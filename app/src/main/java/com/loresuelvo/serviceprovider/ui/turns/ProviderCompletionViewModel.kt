package com.loresuelvo.serviceprovider.ui.turns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.CompletionEligibility
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderCompletionUiState {
    data object Closed : ProviderCompletionUiState
    data class Checking(val order: WorkOrder) : ProviderCompletionUiState
    data class Ready(val order: WorkOrder, val eligibility: CompletionEligibility) : ProviderCompletionUiState
    data object SessionExpired : ProviderCompletionUiState
}

@HiltViewModel
class ProviderCompletionViewModel @Inject constructor(
    private val getEligibility: GetCompletionEligibilityUseCase,
    private val sessionStore: AuthSessionStore,
) : ViewModel() {
    private var activeSession = sessionStore.getSession()
    private var queryJob: Job? = null
    private var draftOrderId: Int? = null
    private val _uiState = MutableStateFlow<ProviderCompletionUiState>(ProviderCompletionUiState.Closed)
    val uiState: StateFlow<ProviderCompletionUiState> = _uiState.asStateFlow()
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    queryJob?.cancel()
                    clearDraft()
                    _uiState.value = ProviderCompletionUiState.SessionExpired
                }
            }
        }
    }

    fun open(order: WorkOrder) {
        queryJob?.cancel()
        if (draftOrderId != order.id) {
            _description.value = ""
            draftOrderId = order.id
        }
        val requestSession = sessionStore.getSession()
        if (requestSession == null) {
            clearDraft()
            _uiState.value = ProviderCompletionUiState.SessionExpired
            return
        }
        _uiState.value = ProviderCompletionUiState.Checking(order)
        queryJob = viewModelScope.launch {
            val eligibility = getEligibility(order)
            if (sessionStore.getSession() != requestSession) return@launch
            if (eligibility == CompletionEligibility.Failure.Unauthorized) {
                clearDraft()
                _uiState.value = ProviderCompletionUiState.SessionExpired
                sessionStore.clearSession()
            } else {
                _uiState.value = ProviderCompletionUiState.Ready(order, eligibility)
            }
        }
    }

    fun retry() {
        val order = (_uiState.value as? ProviderCompletionUiState.Ready)?.order ?: return
        open(order)
    }

    fun onDescriptionChange(value: String) {
        if ((_uiState.value as? ProviderCompletionUiState.Ready)?.eligibility == CompletionEligibility.Eligible)
            _description.value = value
    }

    private fun clearDraft() {
        _description.value = ""
        draftOrderId = null
    }
}
