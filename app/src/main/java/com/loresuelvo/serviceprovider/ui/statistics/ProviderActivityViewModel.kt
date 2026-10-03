package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderActivityUiState {
    data object Loading : ProviderActivityUiState
    data class Ready(val activity: ProviderActivity) : ProviderActivityUiState
    data class Error(val failure: ActivityOutcome.Failure) : ProviderActivityUiState
    data object SessionExpired : ProviderActivityUiState
}

@HiltViewModel
class ProviderActivityViewModel @Inject constructor(
    private val getActivity: GetProviderActivityUseCase,
    private val sessionStore: AuthSessionStore,
    clock: Clock,
) : ViewModel() {
    private val end = clock.instant()
    val query = ActivityQuery(end.minus(Duration.ofDays(30)), end)
    private val mutableState = MutableStateFlow<ProviderActivityUiState>(ProviderActivityUiState.Loading)
    val uiState = mutableState.asStateFlow()
    private var requestId = 0L
    private var loadJob: Job? = null
    private var activeSession = sessionStore.getSession()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    requestId++
                    loadJob?.cancel()
                    mutableState.value = ProviderActivityUiState.SessionExpired
                }
            }
        }
        retry()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        val session = sessionStore.getSession()
        if (session == null) {
            mutableState.value = ProviderActivityUiState.SessionExpired
            return
        }
        val id = ++requestId
        mutableState.value = ProviderActivityUiState.Loading
        loadJob = viewModelScope.launch {
            val outcome = getActivity(query)
            if (id != requestId || sessionStore.getSession() != session) return@launch
            mutableState.value = when (outcome) {
                is ActivityOutcome.Success -> ProviderActivityUiState.Ready(outcome.activity)
                ActivityOutcome.Failure.Unauthorized -> {
                    sessionStore.clearSession()
                    ProviderActivityUiState.SessionExpired
                }
                is ActivityOutcome.Failure -> ProviderActivityUiState.Error(outcome)
            }
        }
    }
}
