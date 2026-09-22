package com.loresuelvo.serviceprovider.bdd.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountAuthorizationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
import com.loresuelvo.serviceprovider.ui.components.bottomnav.BottomDestination
import com.loresuelvo.serviceprovider.ui.components.providerInitials
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.ProfilePaymentState
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileViewModel
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ProviderProfileWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val sessionStore = ProfileSessionStore()
    private val currentAccount = ProfileCurrentAccountRepository()
    private val paymentAccount = ProfilePaymentAccountRepository()
    private val viewModelStore = ViewModelStore()
    private val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            newViewModel() as T
    }
    private var currentRoute = Route.Home.path
    private lateinit var viewModel: ProviderProfileViewModel

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun configureAuthenticatedProvider() {
        sessionStore.saveSession(session())
        currentAccount.responses.clear()
        currentAccount.defaultResponse = CurrentAccountOutcome.Success(provider())
    }

    fun openTab(tab: String) {
        currentRoute = when (tab) {
            "Inicio" -> Route.Home.path
            "Mensajes" -> Route.Messages.path
            else -> error("Unsupported profile example tab: $tab")
        }
    }

    fun selectProfile() {
        assertTrue(BottomDestination.shouldShow(currentRoute))
        openProfile()
    }

    fun assertProviderIdentity() {
        val account = readyProvider()
        assertEquals("Juan", account.name)
        assertEquals("Gómez", account.surname)
        assertEquals("juan@example.com", account.email)
        assertEquals("Plomería", account.category.name)
    }

    fun assertPhotoOrAvatar() {
        val account = readyProvider()
        assertTrue(account.profilePhotoUrl.isNullOrBlank())
        assertEquals("JG", providerInitials(account.name, account.surname))
    }

    fun assertPrimaryTabs() {
        assertEquals(Route.Profile.path, BottomDestination.Profile.route)
        assertTrue(BottomDestination.shouldShow(Route.Home.path))
        assertTrue(BottomDestination.shouldShow(Route.Messages.path))
        assertTrue(BottomDestination.shouldShow(Route.Profile.path))
    }

    fun configureAccountSituation(situation: String) {
        when (situation) {
            "Sigue pendiente" -> currentAccount.pending = CompletableDeferred()
            "Falla por falta de red" -> currentAccount.defaultResponse =
                CurrentAccountOutcome.Failure.Network(IllegalStateException("offline"))
            "Falla en el servicio" -> currentAccount.defaultResponse =
                CurrentAccountOutcome.Failure.Server(503)
            else -> error("Unsupported account situation: $situation")
        }
    }

    fun assertAccountResult(result: String) {
        val expected = when (result) {
            "Una indicación de carga" -> ProviderProfileUiState.Loading
            "Un error con opción de reintentar" -> ProviderProfileUiState.Unavailable
            else -> error("Unsupported account result: $result")
        }
        assertEquals(expected, viewModel.uiState.value)
    }

    fun assertConnectionActionsUnavailable() {
        assertTrue(viewModel.uiState.value !is ProviderProfileUiState.Ready)
    }

    fun configureFailedProfileWithRetry() {
        currentAccount.defaultResponse = CurrentAccountOutcome.Failure.Network(
            IllegalStateException("offline"),
        )
        openProfile()
        assertEquals(ProviderProfileUiState.Unavailable, viewModel.uiState.value)
    }

    fun restoreAccountService() {
        currentAccount.defaultResponse = CurrentAccountOutcome.Success(
            provider().copy(name = "María"),
        )
    }

    fun selectRetry() {
        viewModel.refresh()
        scheduler.advanceUntilIdle()
    }

    fun assertUpdatedProvider() {
        assertEquals("María", readyProvider().name)
        assertEquals(2, currentAccount.calls)
    }

    fun assertErrorGone() {
        assertTrue(viewModel.uiState.value is ProviderProfileUiState.Ready)
    }

    fun expireSessionAtAccountLookup() {
        currentAccount.defaultResponse = CurrentAccountOutcome.Failure.Unauthorized
    }

    fun assertReLoginRequired() {
        assertEquals(ProviderProfileUiState.SessionExpired, viewModel.uiState.value)
        assertEquals(null, sessionStore.getSession())
    }

    fun assertPrivateDataGone() {
        assertTrue(viewModel.uiState.value !is ProviderProfileUiState.Ready)
    }

    fun configureIdentityStatus() {
        currentAccount.defaultResponse = CurrentAccountOutcome.Success(
            provider().copy(
                identityVerificationStatus = IdentityVerificationStatus.Approved,
                identityVerifiedOn = 1_768_480_496_000L,
            ),
        )
    }

    fun assertIdentityStatus() {
        assertEquals(IdentityVerificationStatus.Approved, readyProvider().identityVerificationStatus)
    }

    fun assertIdentityApprovalDate() {
        assertEquals(1_768_480_496_000L, readyProvider().identityVerifiedOn)
    }

    fun assertNoIdentityAction() {
        assertTrue(viewModel.uiState.value is ProviderProfileUiState.Ready)
    }

    fun configurePendingConnections() {
        currentAccount.defaultResponse = CurrentAccountOutcome.Success(
            provider().copy(identityVerificationStatus = IdentityVerificationStatus.Unverified),
        )
        paymentAccount.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(ConnectionStatus.PENDING),
        )
    }

    fun assertPendingPaymentConnection() {
        val ready = viewModel.uiState.value as ProviderProfileUiState.Ready
        assertEquals(ProfilePaymentState.Pending, ready.payment)
        assertEquals(1, paymentAccount.statusCalls)
        assertEquals(0, paymentAccount.authorizationCalls)
    }

    fun assertCalendarDoesNotBlockProfile() {
        assertTrue(viewModel.uiState.value is ProviderProfileUiState.Ready)
        assertEquals(0, paymentAccount.authorizationCalls)
    }

    fun openProfile() {
        viewModel = ViewModelProvider(viewModelStore, viewModelFactory)[
            "provider-profile",
            ProviderProfileViewModel::class.java,
        ]
        viewModel.refresh()
        scheduler.advanceUntilIdle()
    }

    override fun close() {
        Dispatchers.setMain(dispatcher)
        currentAccount.pending?.complete(CurrentAccountOutcome.Failure.Network(
            IllegalStateException("scenario finished"),
        ))
        scheduler.advanceUntilIdle()
        viewModelStore.clear()
        scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ProviderProfileViewModel(
        ResolveProviderEntryUseCase(sessionStore, currentAccount),
        GetPaymentAccountStatusUseCase(paymentAccount),
        sessionStore,
    )

    private fun readyProvider(): CurrentAccount.Provider =
        (viewModel.uiState.value as ProviderProfileUiState.Ready).provider

    private fun provider() = CurrentAccount.Provider(
        id = 20,
        name = "Juan",
        surname = "Gómez",
        email = "juan@example.com",
        category = Category(1, "Plomería"),
        profilePhotoUrl = null,
    )

    private fun session() = AuthSession(
        user = User("auth0|provider", "provider@example.com"),
        accessToken = "synthetic-token",
    )

    private class ProfileCurrentAccountRepository : CurrentAccountRepository {
        val responses = ArrayDeque<CurrentAccountOutcome>()
        var defaultResponse: CurrentAccountOutcome = CurrentAccountOutcome.Failure.NotFound
        var pending: CompletableDeferred<CurrentAccountOutcome>? = null
        var calls = 0

        override suspend fun getCurrentAccount(): CurrentAccountOutcome {
            calls += 1
            return pending?.await()
                ?: if (responses.isEmpty()) defaultResponse else responses.removeFirst()
        }
    }

    private class ProfilePaymentAccountRepository : PaymentAccountRepository {
        var outcome: PaymentAccountStatusOutcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(ConnectionStatus.PENDING),
        )
        var statusCalls = 0
        var authorizationCalls = 0

        override suspend fun getStatus(): PaymentAccountStatusOutcome {
            statusCalls += 1
            return outcome
        }

        override suspend fun requestAuthorization(): PaymentAccountAuthorizationOutcome {
            authorizationCalls += 1
            error("Profile must not start authorization")
        }
    }

    private class ProfileSessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(null)

        override val sessionFlow: StateFlow<AuthSession?> = state
        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() { state.value = null }
    }
}
