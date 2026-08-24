package com.loresuelvo.serviceprovider.domain.auth

import android.content.Context

/**
 * Port that abstracts the identity-provider layer (Auth0 today, any
 * OIDC provider tomorrow) behind a single signup/login API.
 *
 * `login(context)` requires an Activity-bound [Context] because the
 * underlying SDK launches an in-app WebView flow via `startActivity`
 * — passing the application context silently fails. The Activity
 * context is supplied by the Composable call site via
 * `LocalContext.current`, not by the ViewModel — keeping the VM
 * `@HiltViewModel`-clean.
 */
interface AuthProvider {
    suspend fun login(context: Context): AuthenticationOutcome
    suspend fun signup(context: Context): AuthenticationOutcome
    suspend fun loginWithGoogle(context: Context): AuthenticationOutcome
    suspend fun logout(context: Context): LogoutOutcome
}