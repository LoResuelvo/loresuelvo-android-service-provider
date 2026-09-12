package com.loresuelvo.serviceprovider.platform.auth

import android.content.Context
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.result.Credentials
import com.loresuelvo.serviceprovider.data.auth.Auth0CredentialsMapper
import com.loresuelvo.serviceprovider.data.auth.Auth0WebAuthLauncher
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationAction
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auth0 implementation of the outer [BrowserAuthenticationLauncher]. It is
 * the sole bridge from the route's Activity context to the Auth0 SDK and
 * maps SDK callbacks to the domain's pure authentication result.
 */
@Singleton
class Auth0BrowserAuthenticationLauncher @Inject constructor(
    private val credentialsMapper: Auth0CredentialsMapper,
    private val webAuthLauncher: Auth0WebAuthLauncher,
) : BrowserAuthenticationLauncher {

    override fun launch(
        activityContext: Context,
        action: AuthenticationAction,
        onResult: (AuthenticationOutcome) -> Unit,
    ) {
        val callback = AuthenticationCallback(credentialsMapper, onResult)
        try {
            when (action) {
                AuthenticationAction.Signup -> webAuthLauncher.startSignup(activityContext, callback)
                AuthenticationAction.Login -> webAuthLauncher.startLogin(activityContext, callback)
                AuthenticationAction.GoogleLogin -> webAuthLauncher.startGoogleLogin(activityContext, callback)
            }
        } catch (error: Throwable) {
            callback.deliver(AuthenticationOutcome.Failure.Provider(error))
        }
    }
}

private class AuthenticationCallback(
    private val credentialsMapper: Auth0CredentialsMapper,
    private val onResult: (AuthenticationOutcome) -> Unit,
) : Callback<Credentials, AuthenticationException> {

    private val delivered = AtomicBoolean(false)

    override fun onSuccess(result: Credentials) {
        deliver(
            credentialsMapper.toSession(result)
                ?.let(AuthenticationOutcome::Success)
                ?: AuthenticationOutcome.Failure.Provider(null),
        )
    }

    override fun onFailure(error: AuthenticationException) {
        deliver(
            if (error.getCode() == CANCELLATION_CODE) {
                AuthenticationOutcome.Cancelled
            } else {
                AuthenticationOutcome.Failure.Provider(error)
            },
        )
    }

    fun deliver(outcome: AuthenticationOutcome) {
        if (delivered.compareAndSet(false, true)) onResult(outcome)
    }

    private companion object {
        const val CANCELLATION_CODE = "a0.authentication_canceled"
    }
}
