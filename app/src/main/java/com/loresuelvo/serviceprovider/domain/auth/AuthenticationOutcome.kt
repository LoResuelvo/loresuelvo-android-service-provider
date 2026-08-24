package com.loresuelvo.serviceprovider.domain.auth

sealed interface AuthenticationOutcome {
    data class Success(val session: AuthSession) : AuthenticationOutcome
    data object Cancelled : AuthenticationOutcome

    sealed interface Failure : AuthenticationOutcome {
        data class Provider(val cause: Throwable?) : Failure
    }
}