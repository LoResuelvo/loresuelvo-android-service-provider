package com.loresuelvo.serviceprovider.domain.auth

/**
 * Local representation of an authenticated session. Holds the
 * identity returned by the identity provider plus the short-lived
 * access token used by [com.loresuelvo.serviceprovider.data.api.AuthInterceptor].
 */
data class AuthSession(
    val user: User,
    val accessToken: String,
)