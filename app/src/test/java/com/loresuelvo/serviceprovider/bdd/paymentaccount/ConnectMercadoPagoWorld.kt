package com.loresuelvo.serviceprovider.bdd.paymentaccount

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
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
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectMercadoPagoWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val testScope = TestScope(dispatcher)

    private val sessionStore = FakeAuthSessionStore()
    private val repository = FakePaymentAccountRepository()
    private val getPaymentAccountStatus = GetPaymentAccountStatusUseCase(repository)

    lateinit var viewModel: MercadoPagoConnectViewModel
        private set

    private var latestEffect: MercadoPagoConnectEffect? = null
    private var effectsJob: Job? = null

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun arrangeNoAuthenticatedSession() {
        sessionStore.clearSession()
    }

    fun openMercadoPagoScreen() {
        viewModel = MercadoPagoConnectViewModel(
            sessionStore = sessionStore,
            getPaymentAccountStatus = getPaymentAccountStatus,
        )
        effectsJob = testScope.launch {
            viewModel.effects.collect { effect ->
                latestEffect = effect
            }
        }
        scheduler.advanceUntilIdle()
    }

    fun assertLoginPrompted() {
        assertEquals(MercadoPagoConnectEffect.NavigateToWelcome, latestEffect)
        assertTrue(viewModel.uiState.value.isUnauthenticated)
    }

    fun assertNoAuthorizationInitiated() {
        assertEquals(0, repository.getStatusCalls)
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

        override suspend fun getStatus(): PaymentAccountStatusOutcome {
            getStatusCalls++
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
