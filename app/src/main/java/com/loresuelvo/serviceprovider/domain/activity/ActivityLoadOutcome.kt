package com.loresuelvo.serviceprovider.domain.activity

sealed interface ActivityLoadOutcome<out T> {
    data class Success<T>(val items: List<T>) : ActivityLoadOutcome<T>

    sealed interface Failure : ActivityLoadOutcome<Nothing> {
        data object Unauthorized : Failure
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int) : Failure
        data object Invalid : Failure
    }
}
