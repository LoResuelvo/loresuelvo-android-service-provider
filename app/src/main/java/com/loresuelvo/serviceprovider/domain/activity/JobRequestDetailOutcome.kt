package com.loresuelvo.serviceprovider.domain.activity

sealed interface JobRequestDetailOutcome {
    data class Success(val request: JobRequest) : JobRequestDetailOutcome
    data object NotFound : JobRequestDetailOutcome

    sealed interface Failure : JobRequestDetailOutcome {
        data object Unauthorized : Failure
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int) : Failure
        data object Invalid : Failure
    }
}
