package com.loresuelvo.serviceprovider.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.account.ProviderEntryOutcome
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProviderProfileViewModel @Inject constructor(
    private val resolveProviderEntry: ResolveProviderEntryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProviderProfileUiState>(ProviderProfileUiState.Loading)
    val uiState: StateFlow<ProviderProfileUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        _uiState.value = ProviderProfileUiState.Loading
        refreshJob = viewModelScope.launch {
            _uiState.value = when (val outcome = resolveProviderEntry()) {
                is ProviderEntryOutcome.Provider -> ProviderProfileUiState.Ready(outcome.account)
                ProviderEntryOutcome.AccountMismatch -> ProviderProfileUiState.AccountMismatch
                ProviderEntryOutcome.IncompleteProfile -> ProviderProfileUiState.IncompleteProfile
                ProviderEntryOutcome.RetryableFailure -> ProviderProfileUiState.Unavailable
                ProviderEntryOutcome.SessionExpired -> ProviderProfileUiState.SessionExpired
                ProviderEntryOutcome.Unauthenticated -> ProviderProfileUiState.Unauthenticated
            }
        }
    }
}
