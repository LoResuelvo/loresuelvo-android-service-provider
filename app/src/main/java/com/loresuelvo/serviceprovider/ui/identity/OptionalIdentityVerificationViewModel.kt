package com.loresuelvo.serviceprovider.ui.identity

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class OptionalIdentityVerificationUiState(val loading: Boolean = false)

sealed interface OptionalIdentityVerificationEffect {
    data object NavigateToMercadoPago : OptionalIdentityVerificationEffect
}

@HiltViewModel
class OptionalIdentityVerificationViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(OptionalIdentityVerificationUiState())
    val uiState: StateFlow<OptionalIdentityVerificationUiState> = _uiState.asStateFlow()

    private val _effects = Channel<OptionalIdentityVerificationEffect>(Channel.BUFFERED)
    val effects: Flow<OptionalIdentityVerificationEffect> = _effects.receiveAsFlow()

    private var navigationRequested = false

    fun verifyNow() = Unit

    fun later() {
        if (navigationRequested || _uiState.value.loading) return
        navigationRequested = true
        _effects.trySend(OptionalIdentityVerificationEffect.NavigateToMercadoPago)
    }
}
