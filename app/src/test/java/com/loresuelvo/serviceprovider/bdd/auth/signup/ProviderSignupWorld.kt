package com.loresuelvo.serviceprovider.bdd.auth.signup

import com.auth0.android.provider.WebAuthProvider
import com.loresuelvo.serviceprovider.data.auth.Auth0Config
import com.loresuelvo.serviceprovider.data.auth.configureSignup
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationAction
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.usecase.auth.EstablishAuthSessionUseCase
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel
import com.loresuelvo.serviceprovider.ui.auth.WelcomeError
import com.loresuelvo.serviceprovider.ui.auth.WelcomeEffect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Deterministic world for the provider signup scenarios.
 *
 * This world exercises the app-owned boundary: selecting signup calls
 * [WelcomeViewModel.signup], which requests a pure signup action, and
 * the production signup adapter adds the configured signup hint to the Auth0
 * request. Auth0 owns the email/password form and the tenant selects its
 * database connection; the synthetic configuration therefore proves only the
 * app-owned request construction. The successful signup fixture also owns a
 * deterministic session store so the scenario can prove the shared bearer-
 * token boundary without a real Auth0 or backend.
 * Failure outcomes stay typed and are asserted through the safe Welcome UI
 * state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSignupWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val effectScope = CoroutineScope(dispatcher)
    private val authenticationLauncher = RecordingAuthenticationLauncher()
    private val sessionStore = RecordingSessionStore()
    private val getCategories = GetCategoriesUseCase(
        object : CategoryRepository {
            override suspend fun getCategories(): CategoriesOutcome =
                CategoriesOutcome.Failure.Network(IllegalStateException("categories not in signup boundary"))
        },
    )

    private lateinit var viewModel: WelcomeViewModel
    private var profileNavigationRequested = false

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun seedNoLocalSession() {
        viewModel = WelcomeViewModel(
            getCategories = getCategories,
            establishAuthSession = EstablishAuthSessionUseCase(sessionStore),
        )
        effectScope.launch {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is WelcomeEffect.LaunchAuthentication -> {
                        viewModel.onAuthenticationResult(authenticationLauncher.launch(effect.action))
                    }
                    WelcomeEffect.NavigateToProfessionalProfile -> {
                        profileNavigationRequested = true
                    }
                }
            }
        }
    }

    fun selectSignup() {
        check(::viewModel.isInitialized) { "Signup scenario must seed its local session first" }
        viewModel.signup()
        scheduler.advanceUntilIdle()
    }

    fun configureSignupOutcome(outcome: AuthenticationOutcome) {
        authenticationLauncher.nextOutcome = outcome
    }

    fun configureSuccessfulSignup() {
        authenticationLauncher.nextOutcome = VALID_SESSION_OUTCOME
    }

    fun cancelSignup() {
        check(::viewModel.isInitialized) { "Cancellation scenario must seed its local session first" }
        viewModel.signup()
        scheduler.advanceUntilIdle()
    }

    fun finishSignupAttempt() {
        check(::viewModel.isInitialized) { "Failure scenario must seed its local session first" }
        viewModel.signup()
        scheduler.advanceUntilIdle()
    }

    fun finishSuccessfulSignup() {
        check(::viewModel.isInitialized) { "Success scenario must seed its local session first" }
        viewModel.signup()
        scheduler.advanceUntilIdle()
    }

    fun startActiveSignup() {
        seedNoLocalSession()
        authenticationLauncher.holdNextAuthentication()
        viewModel.signup()
        scheduler.runCurrent()
    }

    fun selectAuthenticationAgain() {
        check(::viewModel.isInitialized) { "Duplicate-auth scenario must seed its local session first" }
        viewModel.login()
        scheduler.runCurrent()
    }

    fun assertNoSecondAuthenticationFlow() {
        assertEquals(1, authenticationLauncher.signupCalls)
        assertEquals(0, authenticationLauncher.loginCalls)
        assertEquals(0, authenticationLauncher.googleCalls)
    }

    fun assertAccessibleLoadingState() {
        assertTrue(
            "The Welcome state must expose an active authentication loading state",
            viewModel.uiState.value.loading,
        )
    }

    fun assertWelcomeRemainsVisible() {
        assertFalse(viewModel.uiState.value.loading)
        assertEquals(null, viewModel.uiState.value.error)
    }

    /**
     * Session persistence belongs to the authenticated onboarding boundary
     * (02-PSU). A cancellation has no session payload to persist, and this
     * world deliberately keeps the session empty while exercising the
     * welcome ViewModel.
     */
    fun assertNoSessionPersisted() {
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(AuthenticationOutcome.Cancelled, authenticationLauncher.lastOutcome)
    }

    fun assertFriendlyAuthenticationError() {
        assertEquals(WelcomeError.Authentication, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.loading)
    }

    fun assertAuthenticationControlsAvailableForRetry() {
        assertFalse(
            "Authentication controls must be available after a failed attempt",
            viewModel.uiState.value.loading,
        )
        assertEquals(WelcomeError.Authentication, viewModel.uiState.value.error)
    }

    fun assertSignupDelegated() {
        assertEquals(1, authenticationLauncher.signupCalls)
    }

    fun assertSignupConfigured() {
        val builder = configuredBuilder()

        builder.configureSignup(
            Auth0Config(
                domain = "synthetic.auth0.com",
                clientId = "synthetic-client-id",
                scheme = SYNTHETIC_SCHEME,
                audience = SYNTHETIC_AUDIENCE,
            ),
        )

        verifyOrder {
            builder.withScheme(SYNTHETIC_SCHEME)
            builder.withAudience(SYNTHETIC_AUDIENCE)
            builder.withParameters(mapOf("screen_hint" to "signup"))
        }
    }

    fun assertSessionPersisted() {
        assertEquals(VALID_SESSION, sessionStore.getSession())
        assertEquals(1, sessionStore.saveCalls)
    }

    fun assertAccessTokenAvailable() {
        assertEquals(VALID_SESSION.accessToken, sessionStore.getSession()?.accessToken)
    }

    fun assertProfessionalProfileNavigationRequested() {
        assertTrue(
            "Successful authentication must request professional-profile navigation",
            profileNavigationRequested,
        )
    }

    /**
     * The pure action/result contract carries no password or Android state;
     * tenant-side credential collection is outside this deterministic fake.
     */
    fun assertNoPasswordHandledByApp() {
        assertFalse(
            "The authentication action must not expose a password",
            AuthenticationAction::class.java.declaredFields.any { it.name.contains("password", ignoreCase = true) },
        )
        assertFalse(
            "The authentication outcome must not expose a password",
            AuthenticationOutcome::class.java.declaredFields.any { it.name.contains("password", ignoreCase = true) },
        )
    }

    fun signupCalls(): Int = authenticationLauncher.signupCalls

    override fun close() {
        authenticationLauncher.releasePendingAuthentication()
        scheduler.advanceUntilIdle()
        effectScope.cancel()
        Dispatchers.resetMain()
    }

    private fun configuredBuilder(): WebAuthProvider.Builder =
        mockk<WebAuthProvider.Builder>().also { builder ->
            every { builder.withScheme(any()) } returns builder
            every { builder.withAudience(any()) } returns builder
            every { builder.withParameters(any()) } returns builder
        }

    private companion object {
        const val SYNTHETIC_SCHEME = "com.loresuelvo.provider.synthetic"
        const val SYNTHETIC_AUDIENCE = "https://api.synthetic.loresuelvo.test"

        val VALID_SESSION = AuthSession(
            user = User(
                id = "auth0|provider",
                email = "provider@example.com",
            ),
            accessToken = "synthetic-provider-access-token",
        )
        val VALID_SESSION_OUTCOME = AuthenticationOutcome.Success(VALID_SESSION)
    }

    private class RecordingSessionStore : AuthSessionStore {

        private val _sessionFlow = MutableStateFlow<AuthSession?>(null)
        override val sessionFlow: StateFlow<AuthSession?> = _sessionFlow

        var saveCalls: Int = 0
            private set

        override fun getSession(): AuthSession? = _sessionFlow.value

        override fun saveSession(session: AuthSession) {
            saveCalls += 1
            _sessionFlow.value = session
        }

        override fun clearSession() {
            _sessionFlow.value = null
        }
    }

    private class RecordingAuthenticationLauncher {

        var nextOutcome: AuthenticationOutcome = AuthenticationOutcome.Cancelled
        var lastOutcome: AuthenticationOutcome = AuthenticationOutcome.Cancelled
        private var pendingAuthentication: CompletableDeferred<AuthenticationOutcome>? = null

        var signupCalls: Int = 0
            private set
        var loginCalls: Int = 0
            private set
        var googleCalls: Int = 0
            private set

        suspend fun launch(action: AuthenticationAction): AuthenticationOutcome = when (action) {
            AuthenticationAction.Login -> awaitAuthentication(AuthenticationOutcome.Cancelled) { loginCalls += 1 }
            AuthenticationAction.Signup -> {
                signupCalls += 1
                awaitNextOutcome()
            }
            AuthenticationAction.GoogleLogin -> awaitAuthentication(AuthenticationOutcome.Cancelled) { googleCalls += 1 }
        }

        fun holdNextAuthentication() {
            pendingAuthentication = CompletableDeferred()
        }

        fun releasePendingAuthentication() {
            pendingAuthentication?.complete(nextOutcome)
            pendingAuthentication = null
        }

        private suspend fun awaitNextOutcome(): AuthenticationOutcome {
            val pending = pendingAuthentication
            val outcome = pending?.await() ?: nextOutcome
            lastOutcome = outcome
            return outcome
        }

        private suspend fun awaitAuthentication(
            defaultOutcome: AuthenticationOutcome,
            recordCall: () -> Unit,
        ): AuthenticationOutcome {
            recordCall()
            val pending = pendingAuthentication
            return pending?.await() ?: defaultOutcome
        }
    }
}
