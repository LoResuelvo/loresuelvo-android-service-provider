package com.loresuelvo.serviceprovider.domain.paymentaccount

sealed interface PaymentAccountStatusOutcome {
    data class Success(val status: PaymentAccountStatus) : PaymentAccountStatusOutcome

    sealed interface Failure : PaymentAccountStatusOutcome {
        data object Unauthorized : Failure
        data object Forbidden : Failure
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int, val message: String?) : Failure
        data class Unknown(val cause: Throwable) : Failure
    }
}
