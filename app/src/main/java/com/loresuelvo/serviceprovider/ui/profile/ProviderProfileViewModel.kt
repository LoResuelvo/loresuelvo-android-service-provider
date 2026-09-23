package com.loresuelvo.serviceprovider.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.account.ProviderEntryOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.domain.usecase.identity.StartIdentityVerificationUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
import com.loresuelvo.serviceprovider.ui.identity.IdentityVerificationFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProviderProfileViewModel @Inject constructor(
    private val resolveProviderEntry: ResolveProviderEntryUseCase,
    private val getPaymentAccountStatus: GetPaymentAccountStatusUseCase,
    private val sessionStore: AuthSessionStore,
    private val startIdentityVerification: StartIdentityVerificationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProviderProfileUiState>(ProviderProfileUiState.Loading)
    val uiState: StateFlow<ProviderProfileUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var paymentRetryJob: Job? = null

    private val _identityState = MutableStateFlow(ProfileIdentityUiState())
    val identityState: StateFlow<ProfileIdentityUiState> = _identityState.asStateFlow()
    private val launches = Channel<ProfileIdentityLaunch>(Channel.BUFFERED)
    val identityLaunches = launches.receiveAsFlow()
    private var identityJob: Job? = null
    private var attemptId = 0L
    private var attemptSession: AuthSession? = null
    private var profileSession: AuthSession? = null
    private var sdkLaunched = false
    private var resumed = false
    private var identityRefreshPending = false

    fun onProfileResumed() {
        resumed = true
        if (identityRefreshPending) refreshAfterIdentity() else refresh()
    }

    fun onProfilePaused() { resumed = false }

    fun leaveProfile() {
        resumed = false
        attemptId++
        attemptSession = null
        sdkLaunched = false
        identityRefreshPending = false
        identityJob?.cancel()
        while (launches.tryReceive().isSuccess) Unit
        _identityState.value = ProfileIdentityUiState()
    }

    fun verifyIdentity() {
        val ready = _uiState.value as? ProviderProfileUiState.Ready ?: return
        if (_identityState.value.loading || ready.provider.identityVerificationStatus.availableAction == null) return
        val session = sessionStore.getSession() ?: return
        if (session != profileSession) return
        val id = ++attemptId
        attemptSession = session
        _identityState.value = ProfileIdentityUiState(loading = true)
        identityJob = viewModelScope.launch {
            val outcome = startIdentityVerification()
            if (!isCurrentAttempt(id)) return@launch
            when (outcome) {
                is StartIdentityVerificationOutcome.Success -> launches.send(ProfileIdentityLaunch(id, outcome.credential))
                StartIdentityVerificationOutcome.Failure.Unauthorized -> {
                    sessionStore.clearSession()
                    expireIdentityAttempt()
                }
                StartIdentityVerificationOutcome.AlreadyApproved -> finishIdentity(null)
                else -> finishIdentity(IdentityVerificationFeedback.SessionStartFailed)
            }
        }
    }

    fun claimIdentityLaunch(id: Long): Boolean {
        if (!isCurrentAttempt(id) || sdkLaunched || !resumed) return false
        sdkLaunched = true
        return true
    }

    fun onIdentityResult(id: Long, result: IdentityVerificationResult) {
        if (!isCurrentAttempt(id) || !sdkLaunched) return
        finishIdentity(when (result) {
            IdentityVerificationResult.Completed -> null
            IdentityVerificationResult.Cancelled -> IdentityVerificationFeedback.Cancelled
            IdentityVerificationResult.PermissionDenied -> IdentityVerificationFeedback.PermissionDenied
            IdentityVerificationResult.Failed -> IdentityVerificationFeedback.Failed
        })
    }

    private fun isCurrentAttempt(id: Long): Boolean {
        if (id != attemptId || attemptSession == null) return false
        if (attemptSession != sessionStore.getSession()) {
            expireIdentityAttempt()
            return false
        }
        return true
    }

    private fun expireIdentityAttempt() {
        attemptSession = null
        sdkLaunched = false
        identityRefreshPending = false
        _identityState.value = ProfileIdentityUiState()
        _uiState.value = ProviderProfileUiState.SessionExpired
    }

    private fun finishIdentity(feedback: IdentityVerificationFeedback?) {
        attemptSession = null
        sdkLaunched = false
        _identityState.value = ProfileIdentityUiState(loading = true, feedback = feedback)
        identityRefreshPending = true
        if (resumed) refreshAfterIdentity()
    }

    private fun refreshAfterIdentity() {
        identityRefreshPending = false
        refreshJob?.cancel()
        _identityState.value = _identityState.value.copy(loading = false)
        reloadProfile()
    }

    fun refresh() {
        if (_identityState.value.loading) return
        reloadProfile()
    }

    private fun reloadProfile() {
        if (refreshJob?.isActive == true) return
        paymentRetryJob?.cancel()
        _uiState.value = ProviderProfileUiState.Loading
        refreshJob = viewModelScope.launch {
            val session = sessionStore.getSession()
            profileSession = session
            _uiState.value = when (val outcome = resolveProviderEntry()) {
                is ProviderEntryOutcome.Provider -> ProviderProfileUiState.Ready(outcome.account)
                ProviderEntryOutcome.AccountMismatch -> ProviderProfileUiState.AccountMismatch
                ProviderEntryOutcome.IncompleteProfile -> ProviderProfileUiState.IncompleteProfile
                ProviderEntryOutcome.RetryableFailure -> ProviderProfileUiState.Unavailable
                ProviderEntryOutcome.SessionExpired -> ProviderProfileUiState.SessionExpired
                ProviderEntryOutcome.Unauthenticated -> ProviderProfileUiState.Unauthenticated
            }
            val ready = _uiState.value as? ProviderProfileUiState.Ready ?: return@launch
            paymentRetryJob = viewModelScope.launch { loadPaymentStatus(ready, session) }
        }
    }

    fun retryPaymentStatus() {
        val ready = _uiState.value as? ProviderProfileUiState.Ready ?: return
        if (ready.payment != ProfilePaymentState.Unavailable || paymentRetryJob?.isActive == true) return
        _uiState.value = ready.copy(payment = ProfilePaymentState.Loading)
        paymentRetryJob = viewModelScope.launch {
            loadPaymentStatus(ready, sessionStore.getSession())
        }
    }

    private suspend fun loadPaymentStatus(
        ready: ProviderProfileUiState.Ready,
        session: AuthSession?,
    ) {
        val outcome = getPaymentAccountStatus()
        if (session == null || sessionStore.getSession() != session) {
            _uiState.value = ProviderProfileUiState.SessionExpired
            return
        }
        if ((_uiState.value as? ProviderProfileUiState.Ready)?.provider != ready.provider) return
        val payment = when (outcome) {
            is PaymentAccountStatusOutcome.Success -> when (outcome.status.status) {
                ConnectionStatus.PENDING -> ProfilePaymentState.Pending
                ConnectionStatus.CONNECTED -> ProfilePaymentState.Connected
            }
            PaymentAccountStatusOutcome.Failure.Unauthorized -> {
                sessionStore.clearSession()
                _uiState.value = ProviderProfileUiState.SessionExpired
                return
            }
            else -> ProfilePaymentState.Unavailable
        }
        _uiState.value = ready.copy(payment = payment)
    }
}
