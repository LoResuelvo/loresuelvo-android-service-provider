package com.loresuelvo.serviceprovider.domain.paymentaccount

sealed interface PaymentAccountAuthorizationOutcome {
    data class Success(val authorizationUrl: String) : PaymentAccountAuthorizationOutcome

    sealed interface Failure : PaymentAccountAuthorizationOutcome {
        data object Unauthorized : Failure
        data object Forbidden : Failure
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int, val message: String?) : Failure
        data class Unknown(val cause: Throwable) : Failure
    }
}
