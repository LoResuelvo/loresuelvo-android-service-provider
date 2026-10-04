package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderConversionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderConversionUiState {
    data object Loading : ProviderConversionUiState
    data class Ready(val conversion: ProviderConversion) : ProviderConversionUiState
    data class Error(val failure: ConversionOutcome.Failure) : ProviderConversionUiState
    data object SessionExpired : ProviderConversionUiState
}

@HiltViewModel
class ProviderConversionViewModel @Inject constructor(
    private val getConversion: GetProviderConversionUseCase,
    private val sessionStore: AuthSessionStore,
    clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProviderConversionUiState>(ProviderConversionUiState.Loading)
    val uiState = mutableState.asStateFlow()
    private val mutableExpansion = MutableStateFlow(false)
    val advancesExpanded = mutableExpansion.asStateFlow()
    private val end = clock.instant()
    val query = ConversionQuery(end.minus(Duration.ofDays(30)).atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC))
    private var opened = false
    private var loadJob: Job? = null
    private var requestId = 0L
    private var activeSession = sessionStore.getSession()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    requestId++
                    loadJob?.cancel()
                    mutableExpansion.value = false
                    mutableState.value = ProviderConversionUiState.SessionExpired
                }
            }
        }
    }
    fun expandAdvances(expanded: Boolean) { mutableExpansion.value = expanded }
    fun open() {
        if (opened) return
        opened = true
        request()
    }
    fun retry() {
        if (loadJob?.isActive == true || mutableState.value == ProviderConversionUiState.SessionExpired) return
        request()
    }
    private fun request() {
        val session = sessionStore.getSession()
        if (session == null || session != activeSession || mutableState.value == ProviderConversionUiState.SessionExpired) {
            mutableState.value = ProviderConversionUiState.SessionExpired
            return
        }
        val id = ++requestId
        mutableState.value = ProviderConversionUiState.Loading
        loadJob = viewModelScope.launch {
            val outcome = getConversion(query)
            if (id != requestId || sessionStore.getSession() != session) return@launch
            mutableState.value = when (outcome) {
                is ConversionOutcome.Success -> ProviderConversionUiState.Ready(outcome.conversion)
                ConversionOutcome.Failure.Unauthorized -> {
                    sessionStore.clearSession()
                    mutableExpansion.value = false
                    ProviderConversionUiState.SessionExpired
                }
                is ConversionOutcome.Failure -> ProviderConversionUiState.Error(outcome)
            }
        }
    }
}
