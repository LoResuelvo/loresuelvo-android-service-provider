package com.loresuelvo.serviceprovider.ui.paymentaccount

import app.cash.turbine.test
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountAuthorizationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibility
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibilityChecker
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.CheckPaymentAccountEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.RequestPaymentAccountAuthorizationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MercadoPagoConnectViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var sessionStore: FakeAuthSessionStore
    private lateinit var repository: FakePaymentAccountRepository
    private lateinit var getStatusUseCase: GetPaymentAccountStatusUseCase
    private lateinit var eligibilityChecker: FakePaymentAccountEligibilityChecker
    private lateinit var checkEligibilityUseCase: CheckPaymentAccountEligibilityUseCase
    private lateinit var requestAuthorizationUseCase: RequestPaymentAccountAuthorizationUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessionStore = FakeAuthSessionStore()
        repository = FakePaymentAccountRepository()
        getStatusUseCase = GetPaymentAccountStatusUseCase(repository)
        eligibilityChecker = FakePaymentAccountEligibilityChecker()
        checkEligibilityUseCase = CheckPaymentAccountEligibilityUseCase(eligibilityChecker)
        requestAuthorizationUseCase = RequestPaymentAccountAuthorizationUseCase(repository)
    }

    private fun createViewModel(): MercadoPagoConnectViewModel =
        MercadoPagoConnectViewModel(
            sessionStore = sessionStore,
            getPaymentAccountStatus = getStatusUseCase,
            checkPaymentAccountEligibility = checkEligibilityUseCase,
            requestPaymentAccountAuthorization = requestAuthorizationUseCase,
        )

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun should_emit_navigate_to_welcome_when_no_session_present() = runTest(scheduler) {
        val viewModel = createViewModel()

        viewModel.effects.test {
            assertEquals(MercadoPagoConnectEffect.NavigateToWelcome, awaitItem())
        }
        assertTrue(viewModel.uiState.value.isUnauthenticated)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun should_load_account_status_when_session_present() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.loading)
        assertEquals(ConnectionStatus.PENDING, state.accountStatus?.status)
        assertTrue(state.offersConnection)
        assertTrue(state.offersContinueWithoutConnecting)
        assertNull(state.error)
        assertEquals(1, repository.callCount)
    }

    @Test
    fun should_emit_navigate_to_home_on_continue_without_connecting() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onContinueWithoutConnecting()
            assertEquals(MercadoPagoConnectEffect.NavigateToHome, awaitItem())
        }
    }

    @Test
    fun should_display_connected_status_and_offer_continue_to_home() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(
                status = ConnectionStatus.CONNECTED,
                accountId = "mp-acc-123",
                canReceivePayments = true,
                canSendServiceProposals = true,
            ),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(ConnectionStatus.CONNECTED, state.accountStatus?.status)
        assertTrue(state.canReceivePayments)
        assertFalse(state.offersConnection)
        assertTrue(state.offersContinueToHome)
    }

    @Test
    fun should_emit_navigate_to_home_on_continue_home() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(
                status = ConnectionStatus.CONNECTED,
                accountId = "mp-acc-123",
                canReceivePayments = true,
                canSendServiceProposals = true,
            ),
        )
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onContinueHome()
            assertEquals(MercadoPagoConnectEffect.NavigateToHome, awaitItem())
        }
    }

    @Test
    fun should_set_ineligible_when_profile_is_incomplete() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        eligibilityChecker.eligibility = PaymentAccountEligibility.Ineligible.IncompleteProfile

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isIneligible)
        assertEquals("completar el perfil profesional", state.ineligibleOrientation)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun should_set_ineligible_when_not_a_provider() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "user@example.com"), "token"))
        eligibilityChecker.eligibility = PaymentAccountEligibility.Ineligible.NotAProvider

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isIneligible)
        assertEquals("utilizar una cuenta de prestador", state.ineligibleOrientation)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun should_clear_session_and_emit_welcome_when_unauthorized() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Failure.Unauthorized

        val viewModel = createViewModel()

        viewModel.effects.test {
            assertEquals(MercadoPagoConnectEffect.NavigateToWelcome, awaitItem())
        }
        advanceUntilIdle()

        assertNull(sessionStore.getSession())
        assertTrue(viewModel.uiState.value.isUnauthenticated)
    }

    @Test
    fun should_set_ineligible_when_forbidden() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "user@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Failure.Forbidden

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isIneligible)
        assertEquals("utilizar una cuenta de prestador", state.ineligibleOrientation)
    }

    @Test
    fun should_set_network_error_when_network_fails() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Failure.Network(IOException("timeout"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error is MercadoPagoConnectError.Network)
    }

    @Test
    fun should_set_server_error_when_server_fails() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Failure.Server(500, "Internal Server Error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error is MercadoPagoConnectError.Server)
        assertEquals(500, (state.error as MercadoPagoConnectError.Server).code)
    }

    @Test
    fun should_emit_launch_browser_when_request_authorization_succeeds() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onConnectClick()
            val effect = awaitItem()
            assertTrue(effect is MercadoPagoConnectEffect.LaunchBrowser)
            assertEquals(
                "https://auth.mercadopago.com/authorization?client_id=123",
                (effect as MercadoPagoConnectEffect.LaunchBrowser).url,
            )
        }
        assertEquals(1, repository.requestAuthorizationCalls)
    }

    @Test
    fun should_not_request_authorization_again_when_already_connecting() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConnectClick()
        viewModel.onConnectClick()
        advanceUntilIdle()

        assertEquals(1, repository.requestAuthorizationCalls)
    }

    @Test
    fun should_emit_welcome_and_clear_session_when_authorization_fails_unauthorized() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        repository.authorizationOutcome = PaymentAccountAuthorizationOutcome.Failure.Unauthorized
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onConnectClick()
            assertEquals(MercadoPagoConnectEffect.NavigateToWelcome, awaitItem())
        }
        assertNull(sessionStore.getSession())
        assertTrue(viewModel.uiState.value.isUnauthenticated)
    }

    @Test
    fun should_set_network_error_when_authorization_fails_network() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        repository.authorizationOutcome = PaymentAccountAuthorizationOutcome.Failure.Network(IOException("timeout"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConnectClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error is MercadoPagoConnectError.Network)
    }

    @Test
    fun should_set_server_error_when_authorization_fails_server() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        repository.authorizationOutcome = PaymentAccountAuthorizationOutcome.Failure.Server(500, "Error")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConnectClick()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error is MercadoPagoConnectError.Server)
    }

    @Test
    fun should_reconcile_status_and_update_to_connected_on_return_via_success_link() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(
                status = ConnectionStatus.CONNECTED,
                accountId = "mp-acc-123",
                canReceivePayments = true,
                canSendServiceProposals = true,
            ),
        )
        viewModel.onReturnViaSuccessLink()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, repository.callCount)
        assertEquals(ConnectionStatus.CONNECTED, state.accountStatus?.status)
        assertTrue(state.canReceivePayments)
        assertFalse(state.isConnecting)
    }

    @Test
    fun should_keep_status_pending_and_allow_recheck_when_api_still_returns_pending_on_return_via_success_link() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onReturnViaSuccessLink()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, repository.callCount)
        assertEquals(ConnectionStatus.PENDING, state.accountStatus?.status)
        assertFalse(state.canReceivePayments)
        assertTrue(state.offersRecheckStatus)
        assertTrue(state.offersContinueWithoutConnecting)
        assertFalse(state.isConnecting)
    }

    @Test
    fun should_recheck_status_when_verify_account_status_called() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.verifyAccountStatus()
        advanceUntilIdle()

        assertEquals(2, repository.callCount)
    }

    @Test
    fun should_handle_cancellation_link_and_mark_connection_incomplete() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onReturnViaCancellationLink()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, repository.callCount)
        assertTrue(state.connectionIncomplete)
        assertTrue(state.offersRetry)
        assertTrue(state.offersContinueWithoutConnecting)
        assertFalse(state.isConnecting)
    }

    @Test
    fun should_reset_is_connecting_and_query_status_on_resume_from_browser() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onConnectClick()
        assertTrue(viewModel.uiState.value.isConnecting)

        viewModel.onResumeFromBrowser()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, repository.callCount)
        assertFalse(state.isConnecting)
        assertEquals(ConnectionStatus.PENDING, state.accountStatus?.status)
        assertTrue(state.offersRetry)
        assertTrue(state.offersContinueWithoutConnecting)
    }

    @Test
    fun should_reset_connection_incomplete_when_status_becomes_connected() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(status = ConnectionStatus.PENDING),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onReturnViaCancellationLink()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connectionIncomplete)

        repository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(
                status = ConnectionStatus.CONNECTED,
                accountId = "mp-acc-123",
                canReceivePayments = true,
                canSendServiceProposals = true,
            ),
        )
        viewModel.checkSessionAndLoadStatus()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.connectionIncomplete)
        assertEquals(ConnectionStatus.CONNECTED, state.accountStatus?.status)
        assertTrue(state.canReceivePayments)
    }

    private class FakePaymentAccountEligibilityChecker : PaymentAccountEligibilityChecker {
        var eligibility: PaymentAccountEligibility = PaymentAccountEligibility.Eligible

        override suspend fun checkEligibility(): PaymentAccountEligibility = eligibility
    }

    private class FakePaymentAccountRepository : PaymentAccountRepository {
        var outcome: PaymentAccountStatusOutcome = PaymentAccountStatusOutcome.Failure.Unauthorized
        var callCount = 0
            private set
        var authorizationOutcome: PaymentAccountAuthorizationOutcome =
            PaymentAccountAuthorizationOutcome.Success("https://auth.mercadopago.com/authorization?client_id=123")
        var requestAuthorizationCalls = 0
            private set

        override suspend fun getStatus(): PaymentAccountStatusOutcome {
            callCount++
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
}
