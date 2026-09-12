package com.loresuelvo.serviceprovider.domain.provider

/**
 * Sealed outcome for provider profile registration.
 *
 * Keeps domain and UI callers away from generic HTTP exceptions and error strings.
 */
sealed interface RegistrationOutcome {

    data class Success(val providerId: Int) : RegistrationOutcome

    sealed interface Failure : RegistrationOutcome {
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int, val message: String) : Failure
        data object Unauthorized : Failure
        data object AlreadyRegistered : Failure
    }
}
