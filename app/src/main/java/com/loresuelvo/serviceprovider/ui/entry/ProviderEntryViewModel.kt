package com.loresuelvo.serviceprovider.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.account.ProviderEntryOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.LogoutOutcome
import com.loresuelvo.serviceprovider.domain.auth.SessionClearOutcome
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
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProviderEntryUiState>(ProviderEntryUiState.Loading)
    val uiState: StateFlow<ProviderEntryUiState> = _uiState.asStateFlow()

    private var resolutionJob: Job? = null
    private var sessionGeneration = 0L
    private var logoutAttempt = 0L
    private var claimedLogout: Long? = null
    private val _logoutState = MutableStateFlow(ProviderLogoutUiState(
        localRemovalPending = savedStateHandle[LOCAL_PENDING] ?: false,
        externalLogoutPending = savedStateHandle[EXTERNAL_PENDING] ?: false,
    ))
    val logoutState: StateFlow<ProviderLogoutUiState> = _logoutState.asStateFlow()

    fun requestLogout() {
        if (sessionStore.getSession() != null && !_logoutState.value.processing) {
            setLogoutState(_logoutState.value.copy(confirmationVisible = true))
        }
    }

    fun dismissLogout() {
        setLogoutState(_logoutState.value.copy(confirmationVisible = false))
    }

    fun confirmLogout() {
        if (!_logoutState.value.confirmationVisible || _logoutState.value.processing) return
        sessionGeneration += 1
        resolutionJob?.cancel()
        resolutionJob = null
        _uiState.value = ProviderEntryUiState.Welcome
        setLogoutState(ProviderLogoutUiState(processing = true, externalLogoutPending = true))
        clearLocalSessionAndLaunch()
    }

    fun retryLogout() {
        val state = _logoutState.value
        if (state.processing || (!state.localRemovalPending && !state.externalLogoutPending)) return
        if (sessionStore.getSession() != null) return
        setLogoutState(state.copy(processing = true))
        if (state.localRemovalPending) clearLocalSessionAndLaunch() else queueLogoutLaunch()
    }

    private fun clearLocalSessionAndLaunch() {
        when (sessionStore.clearSessionDurably()) {
            SessionClearOutcome.Cleared -> {
                setLogoutState(_logoutState.value.copy(localRemovalPending = false))
                queueLogoutLaunch()
            }
            SessionClearOutcome.PersistenceFailure ->
                setLogoutState(_logoutState.value.copy(processing = false, localRemovalPending = true))
        }
    }

    private fun queueLogoutLaunch() {
        logoutAttempt += 1
        claimedLogout = null
        setLogoutState(_logoutState.value.copy(launchId = logoutAttempt))
    }

    fun claimLogoutLaunch(id: Long): Boolean {
        if (_logoutState.value.launchId != id || claimedLogout != null) return false
        claimedLogout = id
        return true
    }

    fun onLogoutResult(id: Long, outcome: LogoutOutcome) {
        if (claimedLogout != id || _logoutState.value.launchId != id) return
        claimedLogout = null
        setLogoutState(_logoutState.value.copy(
            processing = false, launchId = null,
            externalLogoutPending = outcome != LogoutOutcome.Success,
        ))
    }

    fun authenticationGeneration(): Long = sessionGeneration
    fun acceptsAuthentication(generation: Long): Boolean = generation == sessionGeneration &&
        !_logoutState.value.processing && !_logoutState.value.localRemovalPending

    private fun setLogoutState(state: ProviderLogoutUiState) {
        savedStateHandle[LOCAL_PENDING] = state.localRemovalPending
        savedStateHandle[EXTERNAL_PENDING] = state.externalLogoutPending
        _logoutState.value = state
    }

    init {
        if (_logoutState.value.localRemovalPending) {
            if (sessionStore.clearSessionDurably() == SessionClearOutcome.Cleared) {
                setLogoutState(_logoutState.value.copy(localRemovalPending = false))
            }
            _uiState.value = ProviderEntryUiState.Welcome
        }
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session == null) {
                    resolutionJob?.cancel()
                    resolutionJob = null
                    _uiState.value = ProviderEntryUiState.Welcome
                } else if (sessionStore.getSession() == session && !_logoutState.value.localRemovalPending) {
                    // New authentication supersedes callbacks from a former logout attempt.
                    claimedLogout = null
                    setLogoutState(ProviderLogoutUiState())
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

    fun showAccountMismatch() {
        _uiState.value = ProviderEntryUiState.AccountMismatch
    }

    fun showIncompleteProfile() {
        _uiState.value = ProviderEntryUiState.CompleteProviderProfile
    }

    private fun resolve() {
        if (resolutionJob?.isActive == true) return
        if (sessionStore.getSession() == null) {
            _uiState.value = ProviderEntryUiState.Welcome
            return
        }

        _uiState.value = ProviderEntryUiState.Loading
        resolutionJob = viewModelScope.launch {
            val generation = sessionGeneration
            val outcome = resolveProviderEntry()
            if (generation != sessionGeneration || sessionStore.getSession() == null) return@launch
            _uiState.value = when (outcome) {
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
    private companion object {
        const val LOCAL_PENDING = "logout.local.pending"
        const val EXTERNAL_PENDING = "logout.external.pending"
    }
}
