package com.loresuelvo.serviceprovider.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.account.ProviderEntryOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProviderEntryViewModel @Inject constructor(
    private val sessionStore: AuthSessionStore,
    private val resolveProviderEntry: ResolveProviderEntryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProviderEntryUiState>(ProviderEntryUiState.Loading)
    val uiState: StateFlow<ProviderEntryUiState> = _uiState.asStateFlow()

    private var resolutionJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                    if (session == null) {
                        _uiState.value = ProviderEntryUiState.Welcome
                    } else {
                        resolve()
                    }
                }
        }
    }

    fun retry() = resolve()

    fun continueToWelcome() {
        sessionStore.clearSession()
    }

    fun refresh() = resolve()

    private fun resolve() {
        if (resolutionJob?.isActive == true) return
        if (sessionStore.getSession() == null) {
            _uiState.value = ProviderEntryUiState.Welcome
            return
        }

        _uiState.value = ProviderEntryUiState.Loading
        resolutionJob = viewModelScope.launch {
            _uiState.value = when (val outcome = resolveProviderEntry()) {
                ProviderEntryOutcome.Unauthenticated,
                ProviderEntryOutcome.SessionExpired,
                -> ProviderEntryUiState.Welcome
                is ProviderEntryOutcome.Provider -> ProviderEntryUiState.Home(outcome.account)
                ProviderEntryOutcome.IncompleteProfile -> ProviderEntryUiState.CompleteProviderProfile
                ProviderEntryOutcome.AccountMismatch -> ProviderEntryUiState.AccountMismatch
                ProviderEntryOutcome.RetryableFailure -> ProviderEntryUiState.RetryableError
            }
        }
    }
}
