package com.loresuelvo.serviceprovider.domain.auth

sealed interface LogoutOutcome {
    data object Success : LogoutOutcome
    data object Cancelled : LogoutOutcome

    sealed interface Failure : LogoutOutcome {
        data class Provider(val cause: Throwable?) : Failure
    }
}