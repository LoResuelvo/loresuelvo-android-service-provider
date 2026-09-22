package com.loresuelvo.serviceprovider.ui.profile

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
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
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderProfileViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var repository: FakeCurrentAccountRepository
    private lateinit var paymentRepository: FakePaymentAccountRepository
    private lateinit var sessionStore: SessionStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCurrentAccountRepository()
        paymentRepository = FakePaymentAccountRepository()
        sessionStore = SessionStore()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun refresh_exposes_the_authenticated_provider_account() = runTest(scheduler) {
        val provider = provider()
        repository.outcome = CurrentAccountOutcome.Success(provider)
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(ProviderProfileUiState.Ready(provider, ProfilePaymentState.Pending), viewModel.uiState.value)
        assertEquals(1, repository.calls)
    }

    @Test
    fun preserves_incomplete_profile_boundary_for_a_missing_account() = runTest(scheduler) {
        repository.outcome = CurrentAccountOutcome.Failure.NotFound
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(ProviderProfileUiState.IncompleteProfile, viewModel.uiState.value)
    }

    @Test
    fun preserves_account_mismatch_boundary_for_a_consumer_account() = runTest(scheduler) {
        repository.outcome = CurrentAccountOutcome.Success(CurrentAccount.Consumer)
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(ProviderProfileUiState.AccountMismatch, viewModel.uiState.value)
    }

    @Test
    fun keeps_loading_visible_while_account_request_is_pending() = runTest(scheduler) {
        repository.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(ProviderProfileUiState.Loading, viewModel.uiState.value)
        assertEquals(1, repository.calls)
        repository.pending?.complete(CurrentAccountOutcome.Success(provider()))
        advanceUntilIdle()
    }

    @Test
    fun network_and_server_failures_offer_a_retryable_state() = runTest(scheduler) {
        val viewModel = viewModel()

        repository.outcome = CurrentAccountOutcome.Failure.Network(IllegalStateException("offline"))
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(ProviderProfileUiState.Unavailable, viewModel.uiState.value)

        repository.outcome = CurrentAccountOutcome.Failure.Server(503)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(ProviderProfileUiState.Unavailable, viewModel.uiState.value)
        assertEquals(2, repository.calls)
    }

    @Test
    fun retry_replaces_an_error_with_fresh_provider_data() = runTest(scheduler) {
        val viewModel = viewModel()
        repository.outcome = CurrentAccountOutcome.Failure.Network(IllegalStateException("offline"))
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(ProviderProfileUiState.Unavailable, viewModel.uiState.value)

        val updated = provider().copy(name = "María")
        repository.outcome = CurrentAccountOutcome.Success(updated)
        viewModel.refresh()
        assertEquals(ProviderProfileUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(ProviderProfileUiState.Ready(updated, ProfilePaymentState.Pending), viewModel.uiState.value)
        assertEquals(2, repository.calls)
    }

    private fun viewModel() = ProviderProfileViewModel(
        ResolveProviderEntryUseCase(sessionStore, repository),
        GetPaymentAccountStatusUseCase(paymentRepository),
        sessionStore,
    )

    @Test
    fun account_is_visible_while_payment_status_is_pending() = runTest(scheduler) {
        val provider = provider()
        repository.outcome = CurrentAccountOutcome.Success(provider)
        paymentRepository.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(ProviderProfileUiState.Ready(provider), viewModel.uiState.value)
        paymentRepository.pending?.complete(
            PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.PENDING)),
        )
        advanceUntilIdle()
        assertEquals(ProviderProfileUiState.Ready(provider, ProfilePaymentState.Pending), viewModel.uiState.value)
    }

    @Test
    fun connected_payment_status_disables_the_pending_action() = runTest(scheduler) {
        repository.outcome = CurrentAccountOutcome.Success(provider())
        paymentRepository.outcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(ConnectionStatus.CONNECTED),
        )
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(ProfilePaymentState.Connected, (viewModel.uiState.value as ProviderProfileUiState.Ready).payment)
    }

    @Test
    fun payment_failure_keeps_provider_data_visible() = runTest(scheduler) {
        repository.outcome = CurrentAccountOutcome.Success(provider())
        paymentRepository.outcome = PaymentAccountStatusOutcome.Failure.Network(
            IllegalStateException("offline"),
        )
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            ProviderProfileUiState.Ready(provider(), ProfilePaymentState.Unavailable),
            viewModel.uiState.value,
        )
    }

    @Test
    fun late_payment_result_cannot_restore_a_cleared_session() = runTest(scheduler) {
        repository.outcome = CurrentAccountOutcome.Success(provider())
        paymentRepository.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()
        sessionStore.clearSession()
        paymentRepository.pending?.complete(
            PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.PENDING)),
        )
        advanceUntilIdle()

        assertEquals(ProviderProfileUiState.SessionExpired, viewModel.uiState.value)
    }

    @Test
    fun old_payment_unauthorized_response_cannot_clear_a_new_session() = runTest(scheduler) {
        repository.outcome = CurrentAccountOutcome.Success(provider())
        paymentRepository.pending = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()
        val replacement = AuthSession(User("auth0|replacement", "new@example.com"), "new-token")
        sessionStore.saveSession(replacement)
        paymentRepository.pending?.complete(PaymentAccountStatusOutcome.Failure.Unauthorized)
        advanceUntilIdle()

        assertEquals(ProviderProfileUiState.SessionExpired, viewModel.uiState.value)
        assertEquals(replacement, sessionStore.getSession())
    }

    private fun provider() = CurrentAccount.Provider(
        id = 20,
        name = "Juan",
        surname = "Gómez",
        email = "juan@example.com",
        category = Category(1, "Plomería"),
        profilePhotoUrl = null,
    )

    private class FakeCurrentAccountRepository : CurrentAccountRepository {
        var outcome: CurrentAccountOutcome = CurrentAccountOutcome.Failure.NotFound
        var pending: CompletableDeferred<CurrentAccountOutcome>? = null
        var calls = 0

        override suspend fun getCurrentAccount(): CurrentAccountOutcome {
            calls += 1
            return pending?.await() ?: outcome
        }
    }

    private class FakePaymentAccountRepository : PaymentAccountRepository {
        var outcome: PaymentAccountStatusOutcome = PaymentAccountStatusOutcome.Success(
            PaymentAccountStatus(ConnectionStatus.PENDING),
        )
        var pending: CompletableDeferred<PaymentAccountStatusOutcome>? = null

        override suspend fun getStatus(): PaymentAccountStatusOutcome = pending?.await() ?: outcome

        override suspend fun requestAuthorization(): PaymentAccountAuthorizationOutcome =
            error("Profile must not request payment authorization")
    }

    private class SessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(
            AuthSession(User("auth0|provider", "provider@example.com"), "token"),
        )

        override val sessionFlow: StateFlow<AuthSession?> = state
        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() { state.value = null }
    }
}
