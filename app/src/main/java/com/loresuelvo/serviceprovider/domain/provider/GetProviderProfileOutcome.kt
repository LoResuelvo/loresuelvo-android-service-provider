package com.loresuelvo.serviceprovider.domain.provider

sealed interface GetProviderProfileOutcome {
    data class Success(val profile: ProviderProfile) : GetProviderProfileOutcome

    sealed interface Failure : GetProviderProfileOutcome {
        data object NotFound : Failure
        data object Unauthorized : Failure
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int, val message: String) : Failure
    }
}
