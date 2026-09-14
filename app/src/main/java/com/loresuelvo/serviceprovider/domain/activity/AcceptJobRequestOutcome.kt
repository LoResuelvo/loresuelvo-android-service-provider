package com.loresuelvo.serviceprovider.domain.activity

sealed interface AcceptJobRequestOutcome {
    data class Success(
        val requestId: Int,
        val conversationId: Int,
    ) : AcceptJobRequestOutcome

    sealed interface Failure : AcceptJobRequestOutcome {
        data object Unauthorized : Failure
        data object Forbidden : Failure
        data object NotFound : Failure
        data object Conflict : Failure
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int) : Failure
        data object Invalid : Failure
    }
}
