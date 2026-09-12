package com.loresuelvo.serviceprovider.ui.auth

import app.cash.turbine.test
import com.loresuelvo.serviceprovider.bdd.auth.welcome.FakeCategoryRepository
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationAction
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.auth.EstablishAuthSessionUseCase
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** JVM proof that the ViewModel orchestrates pure authentication outcomes. */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var sessionStore: RecordingAuthSessionStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        categoryRepository = FakeCategoryRepository()
        sessionStore = RecordingAuthSessionStore()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun should_load_categories_into_ready_state_when_backend_returns_six() = runTest(scheduler) {
        categoryRepository.nextOutcome = CategoriesOutcome.Success((1..6).map { Category(it, "Category $it") })
        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value.categories
        assertTrue(state is WelcomeCategoriesUiState.Ready)
        if (state is WelcomeCategoriesUiState.Ready) {
            assertEquals(6, state.categories.size)
        }
    }

    @Test
    fun should_collapse_empty_categories_into_error() = runTest(scheduler) {
        categoryRepository.nextOutcome = CategoriesOutcome.Success(emptyList())
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.categories is WelcomeCategoriesUiState.Error)
    }

    @Test
    fun should_clear_loading_and_error_when_authentication_is_cancelled() = runTest(scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        requestAndReturn(viewModel, AuthenticationAction.Login, AuthenticationOutcome.Cancelled) {
            viewModel.login()
        }

        assertEquals(false, viewModel.uiState.value.loading)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun should_expose_a_safe_error_when_authentication_fails() = runTest(scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        requestAndReturn(
            viewModel,
            AuthenticationAction.Signup,
            AuthenticationOutcome.Failure.Provider(null),
        ) { viewModel.signup() }

        assertEquals(WelcomeError.Authentication, viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.loading)
    }

    @Test
    fun should_persist_session_and_navigate_after_successful_signup() = runTest(scheduler) {
        val viewModel = newViewModel()
        val session = sampleSession()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.signup()
            assertEquals(WelcomeEffect.LaunchAuthentication(AuthenticationAction.Signup), awaitItem())
            viewModel.onAuthenticationResult(AuthenticationOutcome.Success(session))
            assertEquals(WelcomeEffect.NavigateToProfessionalProfile, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(session, sessionStore.getSession())
        assertEquals(1, sessionStore.saveCalls)
        assertEquals(false, viewModel.uiState.value.loading)
    }

    @Test
    fun should_request_google_authentication_without_passing_android_state() = runTest(scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        requestAndReturn(
            viewModel,
            AuthenticationAction.GoogleLogin,
            AuthenticationOutcome.Cancelled,
        ) { viewModel.loginWithGoogle() }
    }

    @Test
    fun should_ignore_duplicate_authentication_actions_until_a_result_returns() = runTest(scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.signup()
            assertEquals(WelcomeEffect.LaunchAuthentication(AuthenticationAction.Signup), awaitItem())
            assertTrue(viewModel.uiState.value.loading)
            viewModel.login()
            viewModel.loginWithGoogle()
            expectNoEvents()
            viewModel.onAuthenticationResult(AuthenticationOutcome.Cancelled)
            assertEquals(false, viewModel.uiState.value.loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun requestAndReturn(
        viewModel: WelcomeViewModel,
        action: AuthenticationAction,
        outcome: AuthenticationOutcome,
        request: () -> Unit,
    ) {
        viewModel.effects.test {
            request()
            assertEquals(WelcomeEffect.LaunchAuthentication(action), awaitItem())
            viewModel.onAuthenticationResult(outcome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun newViewModel(): WelcomeViewModel = WelcomeViewModel(
        getCategories = GetCategoriesUseCase(categoryRepository),
        establishAuthSession = EstablishAuthSessionUseCase(sessionStore),
    )

    private fun sampleSession(): AuthSession = AuthSession(
        user = User(id = "auth0|provider", email = "provider@example.com"),
        accessToken = "synthetic-provider-access-token",
    )

    private class RecordingAuthSessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(null)
        override val sessionFlow: StateFlow<AuthSession?> = state
        var saveCalls: Int = 0
            private set

        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) {
            saveCalls += 1
            state.value = session
        }
        override fun clearSession() { state.value = null }
    }
}
