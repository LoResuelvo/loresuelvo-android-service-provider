package com.loresuelvo.serviceprovider.bdd.auth.welcome

import com.loresuelvo.serviceprovider.domain.auth.AuthProvider
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.LogoutOutcome
import android.content.Context

/**
 * Test double for [AuthProvider]. Default behaviour: every auth
 * action returns `Failure.Provider(null)`. Tests can override the
 * `nextOutcome` field before triggering an action to drive
 * `WelcomeViewModel` through specific branches.
 */
class FakeAuthProvider(
    var nextOutcome: AuthenticationOutcome = AuthenticationOutcome.Failure.Provider(null),
    var nextLogoutOutcome: LogoutOutcome = LogoutOutcome.Failure.Provider(null),
) : AuthProvider {

    var loginCalls: Int = 0
        private set
    var signupCalls: Int = 0
        private set
    var googleCalls: Int = 0
        private set
    var logoutCalls: Int = 0
        private set

    override suspend fun login(context: Context): AuthenticationOutcome {
        loginCalls++
        return nextOutcome
    }

    override suspend fun signup(context: Context): AuthenticationOutcome {
        signupCalls++
        return nextOutcome
    }

    override suspend fun loginWithGoogle(context: Context): AuthenticationOutcome {
        googleCalls++
        return nextOutcome
    }

    override suspend fun logout(context: Context): LogoutOutcome {
        logoutCalls++
        return nextLogoutOutcome
    }
}