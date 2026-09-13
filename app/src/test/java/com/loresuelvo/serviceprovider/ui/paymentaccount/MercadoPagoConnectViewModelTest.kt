package com.loresuelvo.serviceprovider.ui.paymentaccount

import app.cash.turbine.test
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibility
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountEligibilityChecker
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.CheckPaymentAccountEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessionStore = FakeAuthSessionStore()
        repository = FakePaymentAccountRepository()
        getStatusUseCase = GetPaymentAccountStatusUseCase(repository)
        eligibilityChecker = FakePaymentAccountEligibilityChecker()
        checkEligibilityUseCase = CheckPaymentAccountEligibilityUseCase(eligibilityChecker)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun should_emit_navigate_to_welcome_when_no_session_present() = runTest(scheduler) {
        val viewModel = MercadoPagoConnectViewModel(sessionStore, getStatusUseCase, checkEligibilityUseCase)

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

        val viewModel = MercadoPagoConnectViewModel(sessionStore, getStatusUseCase, checkEligibilityUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.loading)
        assertEquals(ConnectionStatus.PENDING, state.accountStatus?.status)
        assertNull(state.error)
        assertEquals(1, repository.callCount)
    }

    @Test
    fun should_set_ineligible_when_profile_is_incomplete() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        eligibilityChecker.eligibility = PaymentAccountEligibility.Ineligible.IncompleteProfile

        val viewModel = MercadoPagoConnectViewModel(sessionStore, getStatusUseCase, checkEligibilityUseCase)
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

        val viewModel = MercadoPagoConnectViewModel(sessionStore, getStatusUseCase, checkEligibilityUseCase)
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

        val viewModel = MercadoPagoConnectViewModel(sessionStore, getStatusUseCase, checkEligibilityUseCase)

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

        val viewModel = MercadoPagoConnectViewModel(sessionStore, getStatusUseCase, checkEligibilityUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isIneligible)
        assertEquals("utilizar una cuenta de prestador", state.ineligibleOrientation)
    }

    @Test
    fun should_set_network_error_when_network_fails() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Failure.Network(IOException("timeout"))

        val viewModel = MercadoPagoConnectViewModel(sessionStore, getStatusUseCase, checkEligibilityUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error is MercadoPagoConnectError.Network)
    }

    @Test
    fun should_set_server_error_when_server_fails() = runTest(scheduler) {
        sessionStore.saveSession(AuthSession(User("1", "provider@example.com"), "token"))
        repository.outcome = PaymentAccountStatusOutcome.Failure.Server(500, "Internal Server Error")

        val viewModel = MercadoPagoConnectViewModel(sessionStore, getStatusUseCase, checkEligibilityUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error is MercadoPagoConnectError.Server)
        assertEquals(500, (state.error as MercadoPagoConnectError.Server).code)
    }

    private class FakePaymentAccountEligibilityChecker : PaymentAccountEligibilityChecker {
        var eligibility: PaymentAccountEligibility = PaymentAccountEligibility.Eligible

        override suspend fun checkEligibility(): PaymentAccountEligibility = eligibility
    }

    private class FakePaymentAccountRepository : PaymentAccountRepository {
        var outcome: PaymentAccountStatusOutcome = PaymentAccountStatusOutcome.Failure.Unauthorized
        var callCount = 0
            private set

        override suspend fun getStatus(): PaymentAccountStatusOutcome {
            callCount++
            return outcome
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
