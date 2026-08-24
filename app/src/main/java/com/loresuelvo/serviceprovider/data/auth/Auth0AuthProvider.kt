package com.loresuelvo.serviceprovider.data.auth

import android.content.Context
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.result.Credentials
import com.loresuelvo.serviceprovider.domain.auth.AuthProvider
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.LogoutOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Auth0-backed [AuthProvider]. The Activity [Context] is supplied by
 * the call site (the [com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel]
 * receives it from `LocalContext.current`); the SDK config comes
 * through the Hilt graph. No `Context` is captured at construction
 * time, so the provider is `@Singleton`-safe.
 */
@Singleton
class Auth0AuthProvider @Inject constructor(
    private val credentialsMapper: Auth0CredentialsMapper,
    private val webAuthLauncher: Auth0WebAuthLauncher,
) : AuthProvider {

    override suspend fun login(context: Context): AuthenticationOutcome =
        authenticate { callback -> webAuthLauncher.startLogin(context, callback) }

    override suspend fun signup(context: Context): AuthenticationOutcome =
        authenticate { callback -> webAuthLauncher.startSignup(context, callback) }

    override suspend fun loginWithGoogle(context: Context): AuthenticationOutcome =
        authenticate { callback -> webAuthLauncher.startGoogleLogin(context, callback) }

    override suspend fun logout(context: Context): LogoutOutcome =
        suspendCancellableCoroutine { cont ->
            webAuthLauncher.startLogout(
                context = context,
                callback = Auth0LogoutCallback(cont),
            )
        }

    private suspend fun authenticate(
        launch: (Callback<Credentials, AuthenticationException>) -> Unit,
    ): AuthenticationOutcome = suspendCancellableCoroutine { cont ->
        launch(Auth0AuthenticationCallback(credentialsMapper, cont))
    }
}

private class Auth0AuthenticationCallback(
    private val credentialsMapper: Auth0CredentialsMapper,
    private val cont: kotlinx.coroutines.CancellableContinuation<AuthenticationOutcome>,
) : Callback<Credentials, AuthenticationException> {

    override fun onSuccess(result: Credentials) {
        val session = credentialsMapper.toSession(result)
        if (session != null) {
            cont.resume(AuthenticationOutcome.Success(session))
        } else {
            cont.resume(AuthenticationOutcome.Failure.Provider(null))
        }
    }

    override fun onFailure(error: AuthenticationException) {
        if (error.getCode() == "a0.authentication_canceled") {
            cont.resume(AuthenticationOutcome.Cancelled)
        } else {
            cont.resume(AuthenticationOutcome.Failure.Provider(error))
        }
    }
}

private class Auth0LogoutCallback(
    private val cont: kotlinx.coroutines.CancellableContinuation<LogoutOutcome>,
) : Callback<Void?, AuthenticationException> {

    override fun onSuccess(result: Void?) {
        cont.resume(LogoutOutcome.Success)
    }

    override fun onFailure(error: AuthenticationException) {
        if (error.getCode() == "a0.authentication_canceled") {
            cont.resume(LogoutOutcome.Cancelled)
        } else {
            cont.resume(LogoutOutcome.Failure.Provider(error))
        }
    }
}