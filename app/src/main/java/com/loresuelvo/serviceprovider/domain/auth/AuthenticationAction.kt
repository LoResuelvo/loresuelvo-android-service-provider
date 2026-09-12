package com.loresuelvo.serviceprovider.domain.auth

/** A provider identity flow requested by an unauthenticated user. */
enum class AuthenticationAction {
    Signup,
    Login,
    GoogleLogin,
}
