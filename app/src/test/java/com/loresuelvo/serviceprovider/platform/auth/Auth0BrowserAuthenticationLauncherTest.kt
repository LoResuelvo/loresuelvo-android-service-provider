package com.loresuelvo.serviceprovider.platform.auth

import android.content.Context
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.result.Credentials
import com.loresuelvo.serviceprovider.data.auth.Auth0CredentialsMapper
import com.loresuelvo.serviceprovider.data.auth.Auth0WebAuthLauncher
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationAction
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Auth0BrowserAuthenticationLauncherTest {

    private val webAuthLauncher = RecordingWebAuthLauncher()
    private val launcher = Auth0BrowserAuthenticationLauncher(
        credentialsMapper = Auth0CredentialsMapper(),
        webAuthLauncher = webAuthLauncher,
    )
    private val context = mockk<Context>(relaxed = true)

    @Test
    fun signup_starts_the_signup_browser_flow() {
        launcher.launch(context, AuthenticationAction.Signup) { }

        assertEquals(AuthenticationAction.Signup, webAuthLauncher.lastAction)
    }

    @Test
    fun google_login_starts_the_google_browser_flow() {
        launcher.launch(context, AuthenticationAction.GoogleLogin) { }

        assertEquals(AuthenticationAction.GoogleLogin, webAuthLauncher.lastAction)
    }

    @Test
    fun cancellation_returns_a_pure_cancelled_outcome_once() {
        var outcome: AuthenticationOutcome? = null
        launcher.launch(context, AuthenticationAction.Login) { outcome = it }
        val cancellation = mockk<AuthenticationException>()
        every { cancellation.getCode() } returns "a0.authentication_canceled"

        webAuthLauncher.authenticationCallback.onFailure(cancellation)
        webAuthLauncher.authenticationCallback.onFailure(cancellation)

        assertEquals(AuthenticationOutcome.Cancelled, outcome)
    }

    private class RecordingWebAuthLauncher : Auth0WebAuthLauncher {
        lateinit var authenticationCallback: Callback<Credentials, AuthenticationException>
        var lastAction: AuthenticationAction? = null

        override fun startLogin(
            context: Context,
            callback: Callback<Credentials, AuthenticationException>,
        ) = record(AuthenticationAction.Login, callback)

        override fun startSignup(
            context: Context,
            callback: Callback<Credentials, AuthenticationException>,
        ) = record(AuthenticationAction.Signup, callback)

        override fun startGoogleLogin(
            context: Context,
            callback: Callback<Credentials, AuthenticationException>,
        ) = record(AuthenticationAction.GoogleLogin, callback)

        override fun startLogout(
            context: Context,
            callback: Callback<Void?, AuthenticationException>,
        ) = Unit

        private fun record(
            action: AuthenticationAction,
            callback: Callback<Credentials, AuthenticationException>,
        ) {
            lastAction = action
            authenticationCallback = callback
        }
    }
}
