package com.loresuelvo.serviceprovider.ui.profile

import androidx.lifecycle.ViewModel
import com.loresuelvo.serviceprovider.domain.account.CalendarConnectionStatus
import com.loresuelvo.serviceprovider.domain.calendar.ConnectCalendarOutcome
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult
import com.loresuelvo.serviceprovider.domain.usecase.calendar.ConnectCalendarUseCase
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
import java.util.UUID
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
    private val connectCalendar: ConnectCalendarUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProviderProfileUiState>(ProviderProfileUiState.Loading)
    val uiState: StateFlow<ProviderProfileUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
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

    private val _calendarState = MutableStateFlow(ProfileCalendarUiState())
    val calendarState: StateFlow<ProfileCalendarUiState> = _calendarState.asStateFlow()
    private val calendarEvents = Channel<ProfileCalendarLaunch>(Channel.BUFFERED)
    val calendarLaunches = calendarEvents.receiveAsFlow()
    private var calendarAttemptId = ""
    private var calendarSession: AuthSession? = null
    private var calendarClaimed = false
    private var calendarResultConsumed = false
    private var calendarJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if ((profileSession != null && profileSession != session) ||
                    (calendarSession != null && calendarSession != session)) {
                    leaveProfile()
                    _uiState.value = ProviderProfileUiState.SessionExpired
                }
            }
        }
    }

    fun authorizeCalendar() {
        val ready = _uiState.value as? ProviderProfileUiState.Ready ?: return
        if (_identityState.value.loading || _calendarState.value.loading || _calendarState.value.confirmationRetry ||
            !ready.provider.calendarConnectionStatus.canAuthorize) return
        val session = sessionStore.getSession() ?: return
        if (session != profileSession) return
        calendarSession = session
        calendarClaimed = false
        calendarResultConsumed = false
        val id = UUID.randomUUID().toString()
        calendarAttemptId = id
        _calendarState.value = ProfileCalendarUiState(loading = true)
        calendarEvents.trySend(ProfileCalendarLaunch(id))
    }

    fun claimCalendarLaunch(id: String): Boolean {
        if (!isCurrentCalendarAttempt(id) || calendarClaimed || !resumed) return false
        calendarClaimed = true
        return true
    }

    fun acceptsCalendarResolution(id: String): Boolean =
        isCurrentCalendarAttempt(id) && calendarClaimed && !calendarResultConsumed

    fun onCalendarResult(id: String, result: CalendarConsentResult) {
        if (!acceptsCalendarResolution(id)) return
        calendarResultConsumed = true
        when (result) {
            is CalendarConsentResult.Authorized -> {
                val session = calendarSession ?: return
                calendarJob = viewModelScope.launch {
                    if (!isCurrentCalendarAttempt(id)) return@launch
                    val outcome = connectCalendar(result.serverAuthCode, session)
                    if (!isCurrentCalendarAttempt(id)) return@launch
                    when (outcome) {
                        ConnectCalendarOutcome.Submitted -> confirmCalendar(id)
                        ConnectCalendarOutcome.Unauthorized -> expireCalendar()
                        ConnectCalendarOutcome.Rejected -> finishCalendar(CalendarFeedback.CodeRejected)
                        ConnectCalendarOutcome.Unavailable -> finishCalendar(CalendarFeedback.SubmissionFailed)
                    }
                }
            }
            CalendarConsentResult.Cancelled -> finishCalendar(CalendarFeedback.Cancelled)
            CalendarConsentResult.Denied -> finishCalendar(CalendarFeedback.Denied)
            CalendarConsentResult.Failed -> finishCalendar(CalendarFeedback.ConsentFailed)
        }
    }

    fun abandonUnlaunchedCalendarConsent() {
        if (calendarSession != null && !calendarResultConsumed) {
            invalidateCalendar()
            _calendarState.value = ProfileCalendarUiState(feedback = CalendarFeedback.Cancelled)
        }
    }

    fun retryCalendarConfirmation() {
        if (_identityState.value.loading || !_calendarState.value.confirmationRetry || _calendarState.value.loading) return
        val id = calendarAttemptId
        if (!isCurrentCalendarAttempt(id)) return
        _calendarState.value = ProfileCalendarUiState(loading = true, confirmationRetry = true)
        calendarJob = viewModelScope.launch { confirmCalendar(id) }
    }

    private suspend fun confirmCalendar(id: String) {
        val outcome = resolveProviderEntry()
        if (!isCurrentCalendarAttempt(id)) return
        when (outcome) {
            is ProviderEntryOutcome.Provider -> {
                val ready = _uiState.value as? ProviderProfileUiState.Ready ?: return
                _uiState.value = ready.copy(provider = outcome.account)
                if (outcome.account.calendarConnectionStatus == CalendarConnectionStatus.Unavailable) {
                    confirmationFailed()
                } else finishCalendar(null)
            }
            ProviderEntryOutcome.SessionExpired, ProviderEntryOutcome.Unauthenticated -> expireCalendar()
            ProviderEntryOutcome.RetryableFailure -> confirmationFailed()
            ProviderEntryOutcome.AccountMismatch -> {
                invalidateCalendar()
                _uiState.value = ProviderProfileUiState.AccountMismatch
            }
            ProviderEntryOutcome.IncompleteProfile -> {
                invalidateCalendar()
                _uiState.value = ProviderProfileUiState.IncompleteProfile
            }
        }
    }

    private fun confirmationFailed() {
        _calendarState.value = ProfileCalendarUiState(
            feedback = CalendarFeedback.ConfirmationFailed, confirmationRetry = true,
        )
    }

    private fun finishCalendar(feedback: CalendarFeedback?) {
        calendarSession = null
        _calendarState.value = ProfileCalendarUiState(feedback = feedback)
    }

    private fun isCurrentCalendarAttempt(id: String): Boolean {
        if (id != calendarAttemptId || calendarSession == null) return false
        if (calendarSession != sessionStore.getSession()) {
            expireCalendar()
            return false
        }
        return true
    }

    private fun expireCalendar() {
        invalidateCalendar()
        _uiState.value = ProviderProfileUiState.SessionExpired
    }

    private fun invalidateCalendar() {
        calendarAttemptId = ""
        calendarSession = null
        calendarClaimed = false
        calendarJob?.cancel()
        while (calendarEvents.tryReceive().isSuccess) Unit
        _calendarState.value = ProfileCalendarUiState()
    }

    fun onProfileResumed() {
        resumed = true
        if (identityRefreshPending) refreshAfterIdentity() else refresh()
    }

    fun onProfilePaused() { resumed = false }

    fun leaveProfile() {
        refreshGeneration++
        refreshJob?.cancel()
        paymentRetryJob?.cancel()
        invalidateCalendar()
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
        if (_identityState.value.loading || _calendarState.value.loading || ready.provider.identityVerificationStatus.availableAction == null) return
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
        if (_identityState.value.loading || _calendarState.value.loading) return
        if (_calendarState.value.confirmationRetry) {
            retryCalendarConfirmation()
            return
        }
        reloadProfile()
    }

    private fun reloadProfile() {
        if (refreshJob?.isActive == true) return
        paymentRetryJob?.cancel()
        _uiState.value = ProviderProfileUiState.Loading
        val generation = ++refreshGeneration
        val session = sessionStore.getSession()
        profileSession = session
        refreshJob = viewModelScope.launch {
            val outcome = resolveProviderEntry()
            if (generation != refreshGeneration || sessionStore.getSession() != session) return@launch
            _uiState.value = when (outcome) {
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
        if (session == null || sessionStore.getSession() != session) return
        if ((_uiState.value as? ProviderProfileUiState.Ready)?.provider?.id != ready.provider.id) return
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
        val current = _uiState.value as? ProviderProfileUiState.Ready ?: return
        _uiState.value = current.copy(payment = payment)
    }
}
