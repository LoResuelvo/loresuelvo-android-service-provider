package com.loresuelvo.serviceprovider.bdd.auth.welcome

import com.loresuelvo.serviceprovider.domain.auth.AuthenticationAction
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import kotlinx.coroutines.CompletableDeferred

/** Deterministic route-side fake that returns pure authentication outcomes. */
class FakeAuthenticationLauncher(
    var nextOutcome: AuthenticationOutcome = AuthenticationOutcome.Failure.Provider(null),
) {
    var authenticationGate: CompletableDeferred<Unit>? = null

    var loginCalls: Int = 0
        private set
    var signupCalls: Int = 0
        private set
    var googleCalls: Int = 0
        private set

    suspend fun launch(action: AuthenticationAction): AuthenticationOutcome {
        when (action) {
            AuthenticationAction.Login -> loginCalls++
            AuthenticationAction.Signup -> signupCalls++
            AuthenticationAction.GoogleLogin -> googleCalls++
        }
        authenticationGate?.await()
        return nextOutcome
    }
}
