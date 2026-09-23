package com.loresuelvo.serviceprovider.ui.profile.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.account.*
import com.loresuelvo.serviceprovider.domain.auth.*
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.identity.*
import com.loresuelvo.serviceprovider.domain.paymentaccount.*
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.domain.usecase.identity.StartIdentityVerificationUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileViewModel
import com.loresuelvo.serviceprovider.ui.profile.ProfileIdentityLaunch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileIdentityFixture : AutoCloseable {
    val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = ViewModelStore()
    val sessionStore = SessionStore()
    var provider = CurrentAccount.Provider(
        20, "Juan", "Gómez", "juan@example.com", Category(1, "Plomería"), null,
        IdentityVerificationStatus.Unverified,
    )
    var accountCalls = 0
    var startCalls = 0
    var accountResponse: suspend () -> CurrentAccountOutcome = { CurrentAccountOutcome.Success(provider) }
    var startResponse: suspend () -> StartIdentityVerificationOutcome = {
        StartIdentityVerificationOutcome.Success(IdentityVerificationCredential("test-session"))
    }
    var paymentResponse: suspend () -> PaymentAccountStatusOutcome = {
        PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.PENDING))
    }
    val launches = mutableListOf<ProfileIdentityLaunch>()
    val viewModel: ProviderProfileViewModel

    init {
        Dispatchers.setMain(dispatcher)
        val account = object : CurrentAccountRepository {
            override suspend fun getCurrentAccount(): CurrentAccountOutcome {
                accountCalls++
                return accountResponse()
            }
        }
        val identity = object : IdentityVerificationRepository {
            override suspend fun start(): StartIdentityVerificationOutcome {
                startCalls++
                return startResponse()
            }
        }
        val payment = object : PaymentAccountRepository {
            override suspend fun getStatus() = paymentResponse()
            override suspend fun requestAuthorization(): PaymentAccountAuthorizationOutcome =
                error("Identity must not authorize payments")
        }
        viewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProviderProfileViewModel(
                ResolveProviderEntryUseCase(sessionStore, account),
                GetPaymentAccountStatusUseCase(payment), sessionStore,
                StartIdentityVerificationUseCase(identity),
            ) as T
        })[ProviderProfileViewModel::class.java]
        scope.launch { viewModel.identityLaunches.collect { launches += it } }
    }

    fun drain() = scheduler.advanceUntilIdle()
    fun open() { viewModel.onProfileResumed(); drain() }
    fun start(): ProfileIdentityLaunch {
        viewModel.verifyIdentity()
        drain()
        return launches.last().also { check(viewModel.claimIdentityLaunch(it.attemptId)) }
    }

    override fun close() {
        store.clear()
        scope.cancel()
        drain()
        Dispatchers.resetMain()
    }

    class SessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(
            AuthSession(User("auth0|profile", "juan@example.com"), "test-access-token"),
        )
        override val sessionFlow: StateFlow<AuthSession?> = state
        override fun getSession() = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() { state.value = null }
    }
}
