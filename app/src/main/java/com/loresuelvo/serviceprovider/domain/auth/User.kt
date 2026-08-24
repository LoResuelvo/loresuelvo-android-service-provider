package com.loresuelvo.serviceprovider.domain.auth

/**
 * Provider-agnostic identity of an authenticated user. Mirrors the
 * shape that `GET /me` will eventually return: the fields populated
 * for now are enough for the Auth0 login/sync flow on the Welcome
 * screen. The richer profile (categories, zone, description) lands
 * alongside the Provider onboarding feature.
 */
data class User(
    val id: String,
    val email: String,
)