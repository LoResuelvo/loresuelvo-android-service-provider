package com.loresuelvo.serviceprovider.domain.account

sealed interface CurrentAccountOutcome {
    data class Success(val account: CurrentAccount) : CurrentAccountOutcome

    sealed interface Failure : CurrentAccountOutcome {
        data object NotFound : Failure
        data object Unauthorized : Failure
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int) : Failure
        data object Invalid : Failure
    }
}
