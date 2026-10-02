package com.loresuelvo.serviceprovider.ui.profile.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.account.CalendarConnectionStatus
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConnectionRepository
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult
import com.loresuelvo.serviceprovider.domain.calendar.ConnectCalendarOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountAuthorizationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.domain.usecase.calendar.ConnectCalendarUseCase
import com.loresuelvo.serviceprovider.domain.usecase.identity.StartIdentityVerificationUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileViewModel
import com.loresuelvo.serviceprovider.ui.profile.ProfileCalendarLaunch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileCalendarFixture : AutoCloseable {
    val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = ViewModelStore()
    val sessionStore = SessionStore()
    var provider = CurrentAccount.Provider(
        20, "Juan", "Gómez", "juan@example.com", Category(1, "Plomería"), null,
        calendarConnectionStatus = CalendarConnectionStatus.Disconnected,
    )
    var accountCalls = 0
    var postCalls = 0
    var postedCode: String? = null
    var postedSession: AuthSession? = null
    var accountResponse: suspend () -> CurrentAccountOutcome = { CurrentAccountOutcome.Success(provider) }
    var postResponse: suspend () -> ConnectCalendarOutcome = { ConnectCalendarOutcome.Submitted }
    var identityCalls = 0
    var identityResponse: suspend () -> StartIdentityVerificationOutcome = { StartIdentityVerificationOutcome.Failure.Network }
    var paymentResponse: suspend () -> PaymentAccountStatusOutcome = {
        PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.PENDING))
    }
    val launches = mutableListOf<ProfileCalendarLaunch>()
    var viewModel: ProviderProfileViewModel
        private set
    private lateinit var factory: ViewModelProvider.Factory
    private var collector: kotlinx.coroutines.Job? = null

    init {
        Dispatchers.setMain(dispatcher)
        val account = object : CurrentAccountRepository {
            override suspend fun getCurrentAccount(): CurrentAccountOutcome {
                accountCalls++
                return accountResponse()
            }
        }
        val calendar = object : CalendarConnectionRepository {
            override suspend fun connect(serverAuthCode: String, session: AuthSession): ConnectCalendarOutcome {
                postCalls++
                postedCode = serverAuthCode
                postedSession = session
                return postResponse()
            }
        }
        val identity = object : IdentityVerificationRepository {
            override suspend fun start(): StartIdentityVerificationOutcome {
                identityCalls++
                return identityResponse()
            }
        }
        val payment = object : PaymentAccountRepository {
            override suspend fun getStatus() = paymentResponse()
            override suspend fun requestAuthorization(): PaymentAccountAuthorizationOutcome = error("Calendar must not authorize payments")
        }
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProviderProfileViewModel(
                ResolveProviderEntryUseCase(sessionStore, account), GetPaymentAccountStatusUseCase(payment),
                sessionStore, StartIdentityVerificationUseCase(identity), ConnectCalendarUseCase(calendar, sessionStore),
            ) as T
        }
        viewModel = ViewModelProvider(store, factory)[ProviderProfileViewModel::class.java]
        collectLaunches()
    }

    private fun collectLaunches() {
        val current = viewModel
        collector = scope.launch { current.calendarLaunches.collect { launches += it } }
    }
    fun restart() {
        collector?.cancel()
        store.clear()
        viewModel = ViewModelProvider(store, factory)[ProviderProfileViewModel::class.java]
        collectLaunches()
    }

    fun drain() = scheduler.advanceUntilIdle()
    fun open() { viewModel.onProfileResumed(); drain() }
    fun start(): ProfileCalendarLaunch {
        viewModel.authorizeCalendar()
        drain()
        return launches.last().also { check(viewModel.claimCalendarLaunch(it.attemptId)) }
    }
    fun result(result: CalendarConsentResult) {
        viewModel.onCalendarResult(launches.last().attemptId, result)
        drain()
    }
    fun status() = (viewModel.uiState.value as ProviderProfileUiState.Ready).provider.calendarConnectionStatus
    fun configureStatus(value: String) {
        provider = provider.copy(calendarConnectionStatus = when (value) {
            "disconnected" -> CalendarConnectionStatus.Disconnected
            "connected" -> CalendarConnectionStatus.Connected
            "action_required" -> CalendarConnectionStatus.ActionRequired
            else -> error("Unsupported calendar status: $value")
        })
    }

    override fun close() {
        store.clear()
        scope.cancel()
        drain()
        Dispatchers.resetMain()
    }

    class SessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(AuthSession(User("auth0|profile", "juan@example.com"), "test-access-token"))
        override val sessionFlow: StateFlow<AuthSession?> = state
        override fun getSession() = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() { state.value = null }
    }
}
