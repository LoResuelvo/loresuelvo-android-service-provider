package com.loresuelvo.serviceprovider.ui.entry

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderEntryViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var sessionStore: RecordingSessionStore
    private lateinit var currentAccountRepository: RecordingCurrentAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessionStore = RecordingSessionStore(session())
        currentAccountRepository = RecordingCurrentAccountRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun resolves_a_complete_provider_to_home() = runTest(scheduler) {
        currentAccountRepository.nextOutcome = CurrentAccountOutcome.Success(provider())

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(ProviderEntryUiState.Home(provider()), viewModel.uiState.value)
        assertEquals(1, currentAccountRepository.calls)
    }

    @Test
    fun resolves_a_missing_profile_to_registration_without_clearing_session() = runTest(scheduler) {
        currentAccountRepository.nextOutcome = CurrentAccountOutcome.Failure.NotFound

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(ProviderEntryUiState.CompleteProviderProfile, viewModel.uiState.value)
        assertEquals(session(), sessionStore.getSession())
    }

    @Test
    fun rejects_consumer_and_returns_to_welcome_after_explicit_action() = runTest(scheduler) {
        currentAccountRepository.nextOutcome = CurrentAccountOutcome.Success(CurrentAccount.Consumer)

        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.continueToWelcome()
        advanceUntilIdle()

        assertEquals(ProviderEntryUiState.Welcome, viewModel.uiState.value)
        assertEquals(1, sessionStore.clearCalls)
    }

    @Test
    fun clears_expired_session_and_shows_welcome() = runTest(scheduler) {
        currentAccountRepository.nextOutcome = CurrentAccountOutcome.Failure.Unauthorized

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(ProviderEntryUiState.Welcome, viewModel.uiState.value)
        assertEquals(1, sessionStore.clearCalls)
    }

    @Test
    fun keeps_session_on_temporary_failure_and_retries_without_authentication() = runTest(scheduler) {
        currentAccountRepository.nextOutcome = CurrentAccountOutcome.Failure.Server(503)

        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(ProviderEntryUiState.RetryableError, viewModel.uiState.value)

        currentAccountRepository.nextOutcome = CurrentAccountOutcome.Success(provider())
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(ProviderEntryUiState.Home(provider()), viewModel.uiState.value)
        assertEquals(session(), sessionStore.getSession())
        assertEquals(2, currentAccountRepository.calls)
    }

    @Test
    fun ignores_retry_while_profile_resolution_is_in_flight() = runTest(scheduler) {
        val pending = CompletableDeferred<CurrentAccountOutcome>()
        currentAccountRepository.pendingOutcome = pending

        val viewModel = newViewModel()
        runCurrent()
        viewModel.retry()
        runCurrent()

        assertEquals(1, currentAccountRepository.calls)
        pending.complete(CurrentAccountOutcome.Success(provider()))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProviderEntryUiState.Home)
    }

    @Test
    fun starts_at_welcome_without_a_session() = runTest(scheduler) {
        sessionStore = RecordingSessionStore(null)

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(ProviderEntryUiState.Welcome, viewModel.uiState.value)
        assertEquals(0, currentAccountRepository.calls)
    }

    @Test
    fun can_replace_authenticated_shell_with_incomplete_profile_destination() = runTest(scheduler) {
        val viewModel = newViewModel()

        viewModel.showIncompleteProfile()

        assertEquals(ProviderEntryUiState.CompleteProviderProfile, viewModel.uiState.value)
    }

    private fun newViewModel() = ProviderEntryViewModel(
        sessionStore = sessionStore,
        resolveProviderEntry = ResolveProviderEntryUseCase(sessionStore, currentAccountRepository),
    )

    private fun provider() = CurrentAccount.Provider(
        id = 20,
        name = "Juan",
        surname = "Gómez",
        email = "juan@example.com",
        category = Category(1, "Plomería"),
        profilePhotoUrl = "https://cdn.example/profile.jpg",
    )

    private fun session() = AuthSession(
        user = User("auth0|provider", "provider@example.com"),
        accessToken = "synthetic-token",
    )

    private class RecordingCurrentAccountRepository : CurrentAccountRepository {
        var nextOutcome: CurrentAccountOutcome = CurrentAccountOutcome.Failure.NotFound
        var pendingOutcome: CompletableDeferred<CurrentAccountOutcome>? = null
        var calls = 0

        override suspend fun getCurrentAccount(): CurrentAccountOutcome {
            calls += 1
            return pendingOutcome?.await() ?: nextOutcome
        }
    }

    private class RecordingSessionStore(initial: AuthSession?) : AuthSessionStore {
        private val state = MutableStateFlow(initial)
        override val sessionFlow: StateFlow<AuthSession?> = state
        var clearCalls = 0

        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() {
            clearCalls += 1
            state.value = null
        }
    }
}
