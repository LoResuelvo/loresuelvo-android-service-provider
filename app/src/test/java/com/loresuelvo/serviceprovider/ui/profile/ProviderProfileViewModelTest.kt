package com.loresuelvo.serviceprovider.ui.profile

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCurrentAccountRepository()
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

        assertEquals(ProviderProfileUiState.Ready(provider), viewModel.uiState.value)
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

    private fun viewModel() = ProviderProfileViewModel(
        ResolveProviderEntryUseCase(SessionStore(), repository),
    )

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
        var calls = 0

        override suspend fun getCurrentAccount(): CurrentAccountOutcome {
            calls += 1
            return outcome
        }
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
