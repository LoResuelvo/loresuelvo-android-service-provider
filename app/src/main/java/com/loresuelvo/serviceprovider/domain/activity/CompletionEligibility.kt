package com.loresuelvo.serviceprovider.domain.activity

sealed interface CompletionEligibility {
    data object Eligible : CompletionEligibility
    data object TooEarly : CompletionEligibility
    data object AlreadyReported : CompletionEligibility
    data object Forbidden : CompletionEligibility
    data object ChangedOrder : CompletionEligibility
    data object Unavailable : CompletionEligibility

    sealed interface Failure : CompletionEligibility {
        data object Unauthorized : Failure
        data object NotFound : Failure
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int) : Failure
        data object Invalid : Failure
    }
}
