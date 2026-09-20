package com.loresuelvo.serviceprovider.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationCredential
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.identity.StartIdentityVerificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class OptionalIdentityVerificationUiState(val loading: Boolean = false)

sealed interface OptionalIdentityVerificationEffect {
    data class LaunchVerification(
        val credential: IdentityVerificationCredential,
    ) : OptionalIdentityVerificationEffect
    data object NavigateToMercadoPago : OptionalIdentityVerificationEffect
    data object NavigateToWelcome : OptionalIdentityVerificationEffect
}

@HiltViewModel
class OptionalIdentityVerificationViewModel @Inject constructor(
    private val startIdentityVerification: StartIdentityVerificationUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OptionalIdentityVerificationUiState())
    val uiState: StateFlow<OptionalIdentityVerificationUiState> = _uiState.asStateFlow()

    private val _effects = Channel<OptionalIdentityVerificationEffect>(Channel.BUFFERED)
    val effects: Flow<OptionalIdentityVerificationEffect> = _effects.receiveAsFlow()

    private var navigationRequested = false

    fun verifyNow() {
        if (navigationRequested || _uiState.value.loading) return
        _uiState.value = OptionalIdentityVerificationUiState(loading = true)
        viewModelScope.launch {
            when (val outcome = startIdentityVerification()) {
                is StartIdentityVerificationOutcome.Success ->
                    _effects.send(OptionalIdentityVerificationEffect.LaunchVerification(outcome.credential))
                StartIdentityVerificationOutcome.AlreadyApproved -> navigateToMercadoPago()
                StartIdentityVerificationOutcome.Failure.Unauthorized -> {
                    navigationRequested = true
                    _effects.send(OptionalIdentityVerificationEffect.NavigateToWelcome)
                }
                else -> _uiState.value = OptionalIdentityVerificationUiState()
            }
        }
    }

    fun onVerificationResult(result: IdentityVerificationResult) = Unit

    fun later() {
        if (navigationRequested || _uiState.value.loading) return
        navigateToMercadoPago()
    }

    private fun navigateToMercadoPago() {
        if (navigationRequested) return
        navigationRequested = true
        _effects.trySend(OptionalIdentityVerificationEffect.NavigateToMercadoPago)
    }
}
