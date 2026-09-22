package com.loresuelvo.serviceprovider.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.account.ProviderEntryOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
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
    private val getPaymentAccountStatus: GetPaymentAccountStatusUseCase,
    private val sessionStore: AuthSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProviderProfileUiState>(ProviderProfileUiState.Loading)
    val uiState: StateFlow<ProviderProfileUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        _uiState.value = ProviderProfileUiState.Loading
        refreshJob = viewModelScope.launch {
            val session = sessionStore.getSession()
            _uiState.value = when (val outcome = resolveProviderEntry()) {
                is ProviderEntryOutcome.Provider -> ProviderProfileUiState.Ready(outcome.account)
                ProviderEntryOutcome.AccountMismatch -> ProviderProfileUiState.AccountMismatch
                ProviderEntryOutcome.IncompleteProfile -> ProviderProfileUiState.IncompleteProfile
                ProviderEntryOutcome.RetryableFailure -> ProviderProfileUiState.Unavailable
                ProviderEntryOutcome.SessionExpired -> ProviderProfileUiState.SessionExpired
                ProviderEntryOutcome.Unauthenticated -> ProviderProfileUiState.Unauthenticated
            }
            val ready = _uiState.value as? ProviderProfileUiState.Ready ?: return@launch
            val outcome = getPaymentAccountStatus()
            if (session == null || sessionStore.getSession() != session) {
                _uiState.value = ProviderProfileUiState.SessionExpired
                return@launch
            }
            val payment = when (outcome) {
                is PaymentAccountStatusOutcome.Success -> when (outcome.status.status) {
                    ConnectionStatus.PENDING -> ProfilePaymentState.Pending
                    ConnectionStatus.CONNECTED -> ProfilePaymentState.Connected
                }
                PaymentAccountStatusOutcome.Failure.Unauthorized -> {
                    sessionStore.clearSession()
                    _uiState.value = ProviderProfileUiState.SessionExpired
                    return@launch
                }
                else -> ProfilePaymentState.Unavailable
            }
            _uiState.value = ready.copy(payment = payment)
        }
    }
}
