package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.statistics.ProviderReputation
import com.loresuelvo.serviceprovider.domain.statistics.ReputationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderReputationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderReputationUiState {
    data object Loading : ProviderReputationUiState
    data class Ready(val reputation: ProviderReputation) : ProviderReputationUiState
    data class Error(val failure: ReputationOutcome.Failure) : ProviderReputationUiState
    data object SessionExpired : ProviderReputationUiState
}

@HiltViewModel
class ProviderReputationViewModel @Inject constructor(
    private val getReputation: GetProviderReputationUseCase,
    private val sessionStore: AuthSessionStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProviderReputationUiState>(ProviderReputationUiState.Loading)
    val uiState = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var requestId = 0L
    private var activeSession = sessionStore.getSession()
    private var opened = false

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    requestId++
                    loadJob?.cancel()
                    mutableState.value = ProviderReputationUiState.SessionExpired
                }
            }
        }
    }
    fun open() {
        if (opened) return
        opened = true
        retry()
    }
    fun retry() {
        if (loadJob?.isActive == true) return
        val session = sessionStore.getSession()
        if (session == null || session != activeSession) {
            mutableState.value = ProviderReputationUiState.SessionExpired
            return
        }
        val id = ++requestId
        mutableState.value = ProviderReputationUiState.Loading
        loadJob = viewModelScope.launch {
            val outcome = getReputation()
            if (id != requestId || sessionStore.getSession() != session) return@launch
            mutableState.value = when (outcome) {
                is ReputationOutcome.Success -> ProviderReputationUiState.Ready(outcome.reputation)
                ReputationOutcome.Failure.Unauthorized -> {
                    sessionStore.clearSession()
                    ProviderReputationUiState.SessionExpired
                }
                is ReputationOutcome.Failure -> ProviderReputationUiState.Error(outcome)
            }
        }
    }
}
