package com.loresuelvo.serviceprovider.ui.paymentaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountAuthorizationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibility
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.CheckPaymentAccountEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.RequestPaymentAccountAuthorizationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MercadoPagoConnectViewModel @Inject constructor(
    private val sessionStore: AuthSessionStore,
    private val getPaymentAccountStatus: GetPaymentAccountStatusUseCase,
    private val checkPaymentAccountEligibility: CheckPaymentAccountEligibilityUseCase,
    private val requestPaymentAccountAuthorization: RequestPaymentAccountAuthorizationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MercadoPagoConnectUiState())
    val uiState: StateFlow<MercadoPagoConnectUiState> = _uiState.asStateFlow()

    private val _effects = Channel<MercadoPagoConnectEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        evaluateAvailability()
    }

    fun evaluateAvailability() {
        checkSessionAndLoadStatus()
    }

    fun onContinueWithoutConnecting() {
        viewModelScope.launch {
            _effects.send(MercadoPagoConnectEffect.NavigateToHome)
        }
    }

    fun onContinueHome() {
        viewModelScope.launch {
            _effects.send(MercadoPagoConnectEffect.NavigateToHome)
        }
    }

    fun onReturnViaSuccessLink() {
        _uiState.update { it.copy(isConnecting = false, connectionIncomplete = false) }
        checkSessionAndLoadStatus()
    }

    fun onReturnViaCancellationLink() {
        _uiState.update { it.copy(isConnecting = false, connectionIncomplete = true) }
        checkSessionAndLoadStatus()
    }

    fun onResumeFromBrowser() {
        if (_uiState.value.isConnecting) {
            _uiState.update { it.copy(isConnecting = false) }
            checkSessionAndLoadStatus()
        }
    }

    fun verifyAccountStatus() {
        checkSessionAndLoadStatus()
    }

    fun onRetryVerification() {
        checkSessionAndLoadStatus()
    }

    fun onBrowserLaunchFailed() {
        _uiState.update {
            it.copy(
                isConnecting = false,
                error = MercadoPagoConnectError.BrowserLaunchFailed,
            )
        }
    }

    fun onConnectClick() {
        if (_uiState.value.isConnecting || _uiState.value.loading || _uiState.value.isIneligible || _uiState.value.isUnauthenticated) return
        _uiState.update { it.copy(isConnecting = true, connectionIncomplete = false, error = null) }
        viewModelScope.launch {
            when (val outcome = requestPaymentAccountAuthorization()) {
                is PaymentAccountAuthorizationOutcome.Success -> {
                    _effects.send(MercadoPagoConnectEffect.LaunchBrowser(outcome.authorizationUrl))
                }
                is PaymentAccountAuthorizationOutcome.Failure.Unauthorized -> {
                    sessionStore.clearSession()
                    _uiState.update { it.copy(isConnecting = false, isUnauthenticated = true) }
                    _effects.send(MercadoPagoConnectEffect.NavigateToWelcome)
                }
                is PaymentAccountAuthorizationOutcome.Failure.Forbidden -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isIneligible = true,
                            ineligibleOrientation = "utilizar una cuenta de prestador",
                        )
                    }
                }
                is PaymentAccountAuthorizationOutcome.Failure.Network -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            error = MercadoPagoConnectError.Network(outcome.cause.message ?: "Network error"),
                        )
                    }
                }
                is PaymentAccountAuthorizationOutcome.Failure.Server -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            error = MercadoPagoConnectError.Server(outcome.code, outcome.message),
                        )
                    }
                }
                is PaymentAccountAuthorizationOutcome.Failure.Unknown -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            error = MercadoPagoConnectError.Server(500, outcome.cause.message),
                        )
                    }
                }
            }
        }
    }

    fun checkSessionAndLoadStatus() {
        val session = sessionStore.getSession()
        if (session == null) {
            _uiState.update { it.copy(isUnauthenticated = true) }
            viewModelScope.launch {
                _effects.send(MercadoPagoConnectEffect.NavigateToWelcome)
            }
            return
        }

        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (checkPaymentAccountEligibility()) {
                is PaymentAccountEligibility.Ineligible.IncompleteProfile -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            isIneligible = true,
                            ineligibleOrientation = "completar el perfil profesional",
                        )
                    }
                }
                is PaymentAccountEligibility.Ineligible.NotAProvider -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            isIneligible = true,
                            ineligibleOrientation = "utilizar una cuenta de prestador",
                        )
                    }
                }
                PaymentAccountEligibility.Eligible -> {
                    loadAccountStatus()
                }
            }
        }
    }

    private suspend fun loadAccountStatus() {
        when (val outcome = getPaymentAccountStatus()) {
            is PaymentAccountStatusOutcome.Success -> {
                _uiState.update {
                    it.copy(
                        loading = false,
                        accountStatus = outcome.status,
                        connectionIncomplete = if (outcome.status.status == ConnectionStatus.CONNECTED) false else it.connectionIncomplete,
                        error = null,
                    )
                }
            }
            is PaymentAccountStatusOutcome.Failure.Unauthorized -> {
                sessionStore.clearSession()
                _uiState.update { it.copy(loading = false, isUnauthenticated = true) }
                _effects.send(MercadoPagoConnectEffect.NavigateToWelcome)
            }
            is PaymentAccountStatusOutcome.Failure.Forbidden -> {
                _uiState.update {
                    it.copy(
                        loading = false,
                        isIneligible = true,
                        ineligibleOrientation = "utilizar una cuenta de prestador",
                    )
                }
            }
            is PaymentAccountStatusOutcome.Failure.Network -> {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = MercadoPagoConnectError.Network(outcome.cause.message.orEmpty()),
                    )
                }
            }
            is PaymentAccountStatusOutcome.Failure.Server -> {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = MercadoPagoConnectError.Server(outcome.code, outcome.message),
                    )
                }
            }
            is PaymentAccountStatusOutcome.Failure.Unknown -> {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = MercadoPagoConnectError.Server(500, outcome.cause.message),
                    )
                }
            }
        }
    }
}
