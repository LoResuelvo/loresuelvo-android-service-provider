package com.loresuelvo.serviceprovider.data.auth

import android.content.Context
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the SDK-level knobs (Auth0 account + scheme) used by
 * `Auth0SdkWebAuthLauncher`. Built from `BuildConfig` values inside
 * `AuthModule.provideAuth0Config`; kept here so the launcher stays
 * SDK-only.
 */
data class Auth0Config(
    val domain: String,
    val clientId: String,
    val scheme: String,
    val audience: String,
)

/**
 * Port for launching Auth0 authentication and SSO logout. A fake
 * implementation lives in `Auth0AuthProviderTest`; the production
 * implementation [Auth0SdkWebAuthLauncher] is the only consumer in
 * app code.
 */
interface Auth0WebAuthLauncher {
    fun startLogin(
        context: Context,
        callback: Callback<Credentials, AuthenticationException>,
    )

    fun startSignup(
        context: Context,
        callback: Callback<Credentials, AuthenticationException>,
    )

    fun startGoogleLogin(
        context: Context,
        callback: Callback<Credentials, AuthenticationException>,
    )

    fun startLogout(
        context: Context,
        callback: Callback<Void?, AuthenticationException>,
    )
}

/**
 * Auth0-SDK-backed [Auth0WebAuthLauncher]. `@Inject` so Hilt wires
 * the [Auth0Config] from `BuildConfig` automatically. The launcher
 * itself is `@Singleton` because the Auth0 account and scheme are
 * effectively immutable for the process lifetime.
 */
@Singleton
class Auth0SdkWebAuthLauncher @Inject constructor(
    private val config: Auth0Config,
) : Auth0WebAuthLauncher {

    override fun startLogin(
        context: Context,
        callback: Callback<Credentials, AuthenticationException>,
    ) {
        WebAuthProvider
            .login(account())
            .configureLogin(config)
            .start(context, callback)
    }

    override fun startSignup(
        context: Context,
        callback: Callback<Credentials, AuthenticationException>,
    ) {
        WebAuthProvider
            .login(account())
            .configureSignup(config)
            .start(context, callback)
    }

    override fun startGoogleLogin(
        context: Context,
        callback: Callback<Credentials, AuthenticationException>,
    ) {
        WebAuthProvider
            .login(account())
            .configureGoogleLogin(config)
            .start(context, callback)
    }

    override fun startLogout(
        context: Context,
        callback: Callback<Void?, AuthenticationException>,
    ) {
        WebAuthProvider
            .logout(account())
            .configureLogout(config)
            .start(context, callback)
    }

    private fun account(): Auth0 = Auth0(config.clientId, config.domain)
}

internal fun WebAuthProvider.Builder.configureLogin(
    config: Auth0Config,
): WebAuthProvider.Builder =
    withScheme(config.scheme)
        .withAudience(config.audience)

internal fun WebAuthProvider.Builder.configureSignup(
    config: Auth0Config,
): WebAuthProvider.Builder =
    withScheme(config.scheme)
        .withAudience(config.audience)
        .withScreenHint("signup")

internal fun WebAuthProvider.Builder.configureGoogleLogin(
    config: Auth0Config,
): WebAuthProvider.Builder =
    configureLogin(config)
        .withConnection(GOOGLE_CONNECTION)

internal fun WebAuthProvider.LogoutBuilder.configureLogout(
    config: Auth0Config,
): WebAuthProvider.LogoutBuilder = withScheme(config.scheme)

private fun WebAuthProvider.Builder.withScreenHint(
    screenHint: String,
): WebAuthProvider.Builder = withParameters(mapOf("screen_hint" to screenHint))

private const val GOOGLE_CONNECTION = "google-oauth2"