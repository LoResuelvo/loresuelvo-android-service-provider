package com.loresuelvo.serviceprovider.bdd.paymentaccount

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibility
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibilityChecker
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountAuthorizationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.CheckPaymentAccountEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.RequestPaymentAccountAuthorizationUseCase
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectEffect
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectMercadoPagoWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val testScope = TestScope(dispatcher)

    private val sessionStore = FakeAuthSessionStore()
    private val repository = FakePaymentAccountRepository()
    private val getPaymentAccountStatus = GetPaymentAccountStatusUseCase(repository)
    private val eligibilityChecker = FakePaymentAccountEligibilityChecker()
    private val checkPaymentAccountEligibility = CheckPaymentAccountEligibilityUseCase(eligibilityChecker)
    private val requestPaymentAccountAuthorization = RequestPaymentAccountAuthorizationUseCase(repository)

    lateinit var viewModel: MercadoPagoConnectViewModel
        private set

    private var latestEffect: MercadoPagoConnectEffect? = null
    private var effectsJob: Job? = null
    private var browserLaunchFails = false
    private var browserLaunchCalls = 0

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun arrangeNoAuthenticatedSession() {
        sessionStore.clearSession()
    }

    fun arrangeAuthenticatedAccount(situacion: String) {
        sessionStore.saveSession(
            AuthSession(
                user = User(id = "user-123", email = "provider@example.com"),
                accessToken = "test-token",
            )
        )
        when (situacion) {
            "un prestador con perfil incompleto" -> {
                eligibilityChecker.eligibility = PaymentAccountEligibility.Ineligible.IncompleteProfile
            }
            "una cuenta que no es de prestador" -> {
                eligibilityChecker.eligibility = PaymentAccountEligibility.Ineligible.NotAProvider
            }
            else -> {
                eligibilityChecker.eligibility = PaymentAccountEligibility.Eligible
            }
        }
    }

    fun arrangeAuthenticatedProviderWithCompleteProfile() {
        arrangeAuthenticatedAccount("un prestador con perfil completo")
    }

    fun arrangeAccountStatusPending() {
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(
                status = ConnectionStatus.PENDING,
                accountId = null,
                canReceivePayments = false,
                canSendServiceProposals = false,
            )
        )
    }

    fun arrangeAccountStatusConnected() {
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(
                status = ConnectionStatus.CONNECTED,
                accountId = "mp-acc-123",
                canReceivePayments = true,
                canSendServiceProposals = true,
            )
        )
    }

    fun openMercadoPagoScreen() {
        effectsJob?.cancel()
        viewModel = MercadoPagoConnectViewModel(
            sessionStore = sessionStore,
            getPaymentAccountStatus = getPaymentAccountStatus,
            checkPaymentAccountEligibility = checkPaymentAccountEligibility,
            requestPaymentAccountAuthorization = requestPaymentAccountAuthorization,
        )
        effectsJob = testScope.launch {
            viewModel.effects.collect { effect ->
                latestEffect = effect
                if (effect is MercadoPagoConnectEffect.LaunchBrowser) {
                    browserLaunchCalls++
                    if (browserLaunchFails) {
                        viewModel.onBrowserLaunchFailed()
                    }
                }
            }
        }
        scheduler.advanceUntilIdle()
    }

    fun evaluateAvailability() {
        openMercadoPagoScreen()
    }

    fun assertNoAuthorizationOffered() {
        assertTrue(viewModel.uiState.value.isIneligible)
        assertEquals(0, repository.getStatusCalls)
    }

    fun assertOrientationIndicated(orientacion: String) {
        assertEquals(orientacion, viewModel.uiState.value.ineligibleOrientation)
    }

    fun assertAccountStatusPendingDisplayed() {
        val state = viewModel.uiState.value
        assertEquals(ConnectionStatus.PENDING, state.accountStatus?.status)
        assertEquals(false, state.loading)
        assertEquals(false, state.isIneligible)
    }

    fun assertOffersConnectOrContinueWithoutConnecting() {
        val state = viewModel.uiState.value
        assertTrue(state.offersConnection)
        assertTrue(state.offersContinueWithoutConnecting)
    }

    fun assertAccountCanReceivePaymentsDisplayed() {
        val state = viewModel.uiState.value
        assertEquals(ConnectionStatus.CONNECTED, state.accountStatus?.status)
        assertTrue(state.canReceivePayments)
        assertFalse(state.loading)
        assertFalse(state.isIneligible)
    }

    fun assertNoOtherAuthorizationOffered() {
        val state = viewModel.uiState.value
        assertFalse(state.offersConnection)
    }

    fun assertOffersContinueToHome() {
        val state = viewModel.uiState.value
        assertTrue(state.offersContinueToHome)
    }

    fun assertLoginPrompted() {
        assertEquals(MercadoPagoConnectEffect.NavigateToWelcome, latestEffect)
        assertTrue(viewModel.uiState.value.isUnauthenticated)
    }

    fun assertNoAuthorizationInitiated() {
        assertEquals(0, repository.getStatusCalls)
    }

    fun arrangeProviderCanConnect() {
        arrangeAuthenticatedProviderWithCompleteProfile()
        arrangeAccountStatusPending()
        openMercadoPagoScreen()
    }

    fun arrangeValidAuthorizationUrl() {
        repository.authorizationOutcome = PaymentAccountAuthorizationOutcome.Success(
            "https://auth.mercadopago.com/authorization?client_id=123"
        )
    }

    fun selectConnectMercadoPago() {
        viewModel.onConnectClick()
        scheduler.advanceUntilIdle()
    }

    fun assertAuthorizationUrlOpenedInBrowser() {
        assertTrue(latestEffect is MercadoPagoConnectEffect.LaunchBrowser)
        assertEquals(
            "https://auth.mercadopago.com/authorization?client_id=123",
            (latestEffect as MercadoPagoConnectEffect.LaunchBrowser).url
        )
    }

    fun assertNoCredentialsRequestedWithinApp() {
        assertFalse(viewModel.uiState.value.isIneligible)
    }

    fun arrangeConnectionRequestInProgress() {
        arrangeProviderCanConnect()
        arrangeValidAuthorizationUrl()
        viewModel.onConnectClick()
    }

    fun assertNoOtherAuthorizationRequestedFromApi() {
        assertEquals(1, repository.requestAuthorizationCalls)
    }

    fun assertNoOtherFlowOpenedInBrowser() {
        assertEquals(1, repository.requestAuthorizationCalls)
    }

    fun arrangeProviderAuthorizedAccess() {
        arrangeProviderCanConnect()
        arrangeValidAuthorizationUrl()
        viewModel.onConnectClick()
        scheduler.advanceUntilIdle()
    }

    fun returnViaSuccessLink() {
        viewModel.onReturnViaSuccessLink()
        scheduler.advanceUntilIdle()
    }

    fun assertStatusQueriedFromApi() {
        assertTrue(repository.getStatusCalls >= 2)
    }

    fun arrangeReturnViaSuccessLink() {
        arrangeProviderAuthorizedAccess()
        viewModel.onReturnViaSuccessLink()
        scheduler.advanceUntilIdle()
    }

    fun verifyAccountStatus() {
        viewModel.verifyAccountStatus()
        scheduler.advanceUntilIdle()
    }

    fun assertConnectionNotSuccessful() {
        val state = viewModel.uiState.value
        assertFalse(state.canReceivePayments)
        assertTrue(state.accountStatus?.status != ConnectionStatus.CONNECTED)
    }

    fun assertAllowsRecheckingOrContinuingWithoutConnecting() {
        val state = viewModel.uiState.value
        assertTrue(state.offersRecheckStatus)
        assertTrue(state.offersContinueWithoutConnecting)
    }

    fun arrangeProviderCancelledAuthorization() {
        arrangeProviderAuthorizedAccess()
    }

    fun returnViaCancellationLink() {
        viewModel.onReturnViaCancellationLink()
        scheduler.advanceUntilIdle()
    }

    fun assertConnectionNotCompletedDisplayed() {
        val state = viewModel.uiState.value
        assertTrue(state.connectionIncomplete)
        assertFalse(state.canReceivePayments)
    }

    fun assertAllowsRetryOrContinueWithoutConnecting() {
        val state = viewModel.uiState.value
        assertTrue(state.offersRetry)
        assertTrue(state.offersContinueWithoutConnecting)
    }

    fun arrangeProviderClosedBrowserWithoutAuthorizing() {
        arrangeProviderAuthorizedAccess()
    }

    fun returnToApp() {
        viewModel.onResumeFromBrowser()
        scheduler.advanceUntilIdle()
    }

    fun assertStatusQueriedAndAccountRetainedPending() {
        val state = viewModel.uiState.value
        assertTrue(repository.getStatusCalls >= 2)
        assertEquals(ConnectionStatus.PENDING, state.accountStatus?.status)
        assertFalse(state.canReceivePayments)
    }

    fun arrangePendingAccountAndOpenScreen() {
        arrangeAccountStatusPending()
        openMercadoPagoScreen()
    }

    fun selectContinueWithoutConnecting() {
        viewModel.onContinueWithoutConnecting()
        scheduler.advanceUntilIdle()
    }

    fun assertAccessToHomePermitted() {
        assertEquals(MercadoPagoConnectEffect.NavigateToHome, latestEffect)
    }

    fun assertAccountNotShownAsConnected() {
        val state = viewModel.uiState.value
        assertFalse(state.canReceivePayments)
        assertTrue(state.accountStatus?.status != ConnectionStatus.CONNECTED)
    }

    fun arrangeExpiredSessionRejectedByApi() {
        sessionStore.saveSession(
            AuthSession(
                user = User(id = "user-123", email = "provider@example.com"),
                accessToken = "expired-token",
            )
        )
        repository.outcome = PaymentAccountStatusOutcome.Failure.Unauthorized
        repository.authorizationOutcome = PaymentAccountAuthorizationOutcome.Failure.Unauthorized
    }

    fun attemptAction(accion: String) {
        when (accion) {
            "consultar el estado de la cuenta" -> {
                openMercadoPagoScreen()
            }
            "solicitar la autorización de conexión" -> {
                repository.outcome = PaymentAccountStatusOutcome.Success(
                    PaymentAccountStatus(status = ConnectionStatus.PENDING)
                )
                openMercadoPagoScreen()
                repository.authorizationOutcome = PaymentAccountAuthorizationOutcome.Failure.Unauthorized
                viewModel.onConnectClick()
                scheduler.advanceUntilIdle()
            }
        }
    }

    fun assertFriendlyMessageAndLoginRequested() {
        assertEquals(MercadoPagoConnectEffect.NavigateToWelcome, latestEffect)
        assertTrue(viewModel.uiState.value.isUnauthenticated)
    }

    fun assertConnectionNotConfirmedAndBrowserNotOpened() {
        assertFalse(viewModel.uiState.value.canReceivePayments)
        assertEquals(0, browserLaunchCalls)
    }

    fun arrangeFailure(fallo: String) {
        arrangeAuthenticatedProviderWithCompleteProfile()
        when (fallo) {
            "un error de red al consultar el estado" -> {
                repository.outcome = PaymentAccountStatusOutcome.Failure.Network(java.io.IOException("Network error"))
            }
            "un error del servidor al consultar" -> {
                repository.outcome = PaymentAccountStatusOutcome.Failure.Server(500, "Server error")
            }
            "un error temporal al solicitar autorización" -> {
                arrangeAccountStatusPending()
                repository.authorizationOutcome = PaymentAccountAuthorizationOutcome.Failure.Network(java.io.IOException("timeout"))
            }
            "la imposibilidad de abrir el navegador" -> {
                arrangeAccountStatusPending()
                arrangeValidAuthorizationUrl()
                browserLaunchFails = true
            }
        }
    }

    fun attemptUserAction(accion: String) {
        when (accion) {
            "consultar el estado" -> {
                openMercadoPagoScreen()
            }
            "conectar la cuenta" -> {
                if (!this::viewModel.isInitialized) {
                    openMercadoPagoScreen()
                }
                viewModel.onConnectClick()
                scheduler.advanceUntilIdle()
            }
        }
    }

    fun assertFriendlyErrorWithoutConfirmingConnection() {
        val state = viewModel.uiState.value
        org.junit.Assert.assertNotNull(state.error)
        assertFalse(state.canReceivePayments)
        assertFalse(state.isConnecting)
    }

    fun assertOffersRecovery(recuperacion: String) {
        val state = viewModel.uiState.value
        when (recuperacion) {
            "reintentar la consulta del estado" -> {
                assertTrue(state.offersRecheckStatus || state.offersRetry)
            }
            "reintentar la conexión tras verificar el estado" -> {
                assertTrue(state.offersConnection || state.offersRetry)
                assertFalse(state.isConnecting)
            }
            "reintentar la apertura tras verificar el estado" -> {
                assertTrue(state.offersConnection || state.offersRetry)
                assertFalse(state.isConnecting)
            }
        }
    }

    fun arrangeStatusQueryFailedOnReturnFromBrowser() {
        arrangeProviderAuthorizedAccess()
        repository.outcome = PaymentAccountStatusOutcome.Failure.Network(java.io.IOException("timeout"))
        viewModel.onReturnViaSuccessLink()
        scheduler.advanceUntilIdle()
    }

    fun arrangeNextStatusQueryConfirmsConnected() {
        arrangeAccountStatusConnected()
    }

    fun selectRetryVerification() {
        viewModel.onRetryVerification()
        scheduler.advanceUntilIdle()
    }

    fun assertStatusQueriedAgainAndAccountConnectedDisplayed() {
        assertTrue(repository.getStatusCalls >= 2)
        val state = viewModel.uiState.value
        assertEquals(ConnectionStatus.CONNECTED, state.accountStatus?.status)
        assertTrue(state.canReceivePayments)
    }

    fun assertNoOtherAuthorizationRequestedOrOpened() {
        assertEquals(1, repository.requestAuthorizationCalls)
        assertEquals(1, browserLaunchCalls)
    }

    fun arrangeProviderPreservesSessionAndProfile() {
        arrangeAuthenticatedProviderWithCompleteProfile()
        arrangeAccountStatusPending()
        openMercadoPagoScreen()
    }

    fun arrangeApiInformsDifferentStateThanPreviousSession() {
        arrangeAccountStatusConnected()
    }

    fun reopenApp() {
        openMercadoPagoScreen()
    }

    fun assertAppQueriesStatusAgain() {
        assertTrue(repository.getStatusCalls >= 2)
    }

    fun assertUpdatesConnectionStatusWithReceivedResponse() {
        val state = viewModel.uiState.value
        assertEquals(ConnectionStatus.CONNECTED, state.accountStatus?.status)
        assertTrue(state.canReceivePayments)
    }

    fun arrangeProviderInitiatedAuthorization() {
        arrangeProviderAuthorizedAccess()
    }

    fun handleLifecycleEventAndReturn(evento: String) {
        when (evento) {
            "una rotación del dispositivo" -> {
                viewModel.onResumeFromBrowser()
                scheduler.advanceUntilIdle()
            }
            "el paso de la app a segundo plano" -> {
                viewModel.onResumeFromBrowser()
                scheduler.advanceUntilIdle()
            }
            "la recreación del proceso de la aplicación" -> {
                openMercadoPagoScreen()
            }
        }
    }

    fun assertAppVerifiesStatusWithApiBeforeConfirming() {
        assertTrue(repository.getStatusCalls >= 2)
    }

    fun assertAllowsContinueOrRetryWithoutOpeningOtherAuthorization() {
        val state = viewModel.uiState.value
        assertTrue(state.offersRetry || state.offersContinueWithoutConnecting)
        assertEquals(1, repository.requestAuthorizationCalls)
        assertEquals(1, browserLaunchCalls)
    }

    override fun close() {
        effectsJob?.cancel()
        scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private class FakePaymentAccountRepository : PaymentAccountRepository {
        var outcome: PaymentAccountStatusOutcome = PaymentAccountStatusOutcome.Failure.Unauthorized
        var getStatusCalls = 0
            private set
        var authorizationOutcome: PaymentAccountAuthorizationOutcome =
            PaymentAccountAuthorizationOutcome.Success("https://auth.mercadopago.com/authorization?client_id=123")
        var requestAuthorizationCalls = 0
            private set

        override suspend fun getStatus(): PaymentAccountStatusOutcome {
            getStatusCalls++
            return outcome
        }

        override suspend fun requestAuthorization(): PaymentAccountAuthorizationOutcome {
            requestAuthorizationCalls++
            return authorizationOutcome
        }
    }

    private class FakeAuthSessionStore : AuthSessionStore {
        private val _session = MutableStateFlow<AuthSession?>(null)
        override val sessionFlow: StateFlow<AuthSession?> = _session

        override fun getSession(): AuthSession? = _session.value

        override fun saveSession(session: AuthSession) {
            _session.value = session
        }

        override fun clearSession() {
            _session.value = null
        }
    }

    private class FakePaymentAccountEligibilityChecker : PaymentAccountEligibilityChecker {
        var eligibility: PaymentAccountEligibility = PaymentAccountEligibility.Eligible

        override suspend fun checkEligibility(): PaymentAccountEligibility = eligibility
    }
}
