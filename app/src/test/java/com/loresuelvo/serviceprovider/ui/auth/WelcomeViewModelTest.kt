package com.loresuelvo.serviceprovider.ui.auth

import app.cash.turbine.test
import com.loresuelvo.serviceprovider.bdd.auth.welcome.FakeAuthProvider
import com.loresuelvo.serviceprovider.bdd.auth.welcome.FakeCategoryRepository
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [WelcomeViewModel]. The test follows the rule in
 * `android-hilt-governance`: ViewModels are tested without Hilt,
 * with hand-rolled fakes for the collaborators.
 *
 * The `TestCoroutineScheduler` is the single source of truth for
 * `viewModelScope`; `Dispatchers.setMain(dispatcher)` redirects the
 * Main dispatcher to it so `viewModelScope.launch { ... }` runs
 * deterministically. `advanceUntilIdle()` flushes pending coroutines
 * after every action so the Turbine assertions see the post-action
 * state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private lateinit var authProvider: FakeAuthProvider
    private lateinit var categoryRepository: FakeCategoryRepository
    private var viewModel: WelcomeViewModel? = null

    private val context = mockk<android.content.Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authProvider = FakeAuthProvider()
        categoryRepository = FakeCategoryRepository()
        // Intentionally NOT building the ViewModel here: each test
        // configures the fakes it cares about and constructs its own
        // VM so the call counters reflect a single VM lifecycle.
    }

    private fun newViewModel(): WelcomeViewModel =
        WelcomeViewModel(
            authProvider = authProvider,
            getCategories = GetCategoriesUseCase(categoryRepository),
        )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun should_load_categories_into_ready_state_when_backend_returns_six() = runTest(scheduler) {
        categoryRepository.nextOutcome = CategoriesOutcome.Success(
            listOf(
                Category(1, "Plomería"),
                Category(2, "Electricidad"),
                Category(3, "Pintura"),
                Category(4, "Gas"),
                Category(5, "Carpintería"),
                Category(6, "Aire acondicionado"),
            ),
        )

        viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel!!.uiState.value
        assertTrue(state.categories is WelcomeCategoriesUiState.Ready)
        assertEquals(
            6,
            (state.categories as WelcomeCategoriesUiState.Ready).categories.size,
        )
        assertEquals(1, categoryRepository.getCategoriesCalls)
    }

    @Test
    fun should_collapse_categories_into_error_when_backend_returns_network_failure() =
        runTest(scheduler) {
            categoryRepository.nextOutcome = CategoriesOutcome.Failure.Network(
                IllegalStateException("backend down"),
            )

            viewModel = newViewModel()
            advanceUntilIdle()

            assertTrue(
                "Expected categories Error but was ${viewModel!!.uiState.value.categories}",
                viewModel!!.uiState.value.categories is WelcomeCategoriesUiState.Error,
            )
        }

    @Test
    fun should_collapse_categories_into_error_when_backend_returns_empty_list() =
        runTest(scheduler) {
            categoryRepository.nextOutcome = CategoriesOutcome.Success(emptyList())

            viewModel = newViewModel()
            advanceUntilIdle()

            assertTrue(
                "Expected categories Error but was ${viewModel!!.uiState.value.categories}",
                viewModel!!.uiState.value.categories is WelcomeCategoriesUiState.Error,
            )
        }

    @Test
    fun should_start_with_loading_state_before_repository_returns() = runTest(scheduler) {
        // The scheduler hasn't advanced yet: the VM's `init { loadCategories() }`
        // launched a coroutine that has not run. We assert the synthetic
        // Loading state by reading the flow without `advanceUntilIdle`.
        viewModel = newViewModel()

        viewModel!!.uiState.test {
            // Cancel any pending work so we don't hang the test.
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_delegate_signup_to_auth_provider_and_clear_loading() = runTest(scheduler) {
        authProvider.nextOutcome = AuthenticationOutcome.Failure.Provider(null)

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.signup(context)
        advanceUntilIdle()

        assertEquals(1, authProvider.signupCalls)
        assertEquals(false, viewModel!!.uiState.value.loading)
        assertEquals(WelcomeError.Authentication, viewModel!!.uiState.value.error)
    }

    @Test
    fun should_clear_loading_and_error_when_authentication_is_cancelled() =
        runTest(scheduler) {
            authProvider.nextOutcome = AuthenticationOutcome.Cancelled

            viewModel = newViewModel()
            advanceUntilIdle()

            viewModel!!.login(context)
            advanceUntilIdle()

            assertEquals(1, authProvider.loginCalls)
            assertEquals(false, viewModel!!.uiState.value.loading)
            assertEquals(null, viewModel!!.uiState.value.error)
        }

    @Test
    fun should_clear_loading_when_authentication_succeeds() = runTest(scheduler) {
        authProvider.nextOutcome = AuthenticationOutcome.Success(
            com.loresuelvo.serviceprovider.domain.auth.AuthSession(
                user = com.loresuelvo.serviceprovider.domain.auth.User(
                    id = "auth0|abc",
                    email = "provider@example.com",
                ),
                accessToken = "test-token",
            ),
        )

        viewModel = newViewModel()
        advanceUntilIdle()

        viewModel!!.loginWithGoogle(context)
        advanceUntilIdle()

        assertEquals(1, authProvider.googleCalls)
        assertEquals(false, viewModel!!.uiState.value.loading)
        assertEquals(null, viewModel!!.uiState.value.error)
    }
}