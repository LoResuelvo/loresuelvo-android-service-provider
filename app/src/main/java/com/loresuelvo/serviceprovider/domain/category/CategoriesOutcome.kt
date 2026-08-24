package com.loresuelvo.serviceprovider.domain.category

/**
 * Outcome of fetching the platform's service categories. Sealed so
 * callers explicitly handle the happy path and every failure branch
 * — mirrors [com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome].
 */
sealed interface CategoriesOutcome {

    data class Success(val categories: List<Category>) : CategoriesOutcome

    sealed interface Failure : CategoriesOutcome {

        /** Transport-level failure: timeouts, DNS, connection refused. */
        data class Network(val cause: Throwable) : Failure

        /**
         * Any non-2xx response. [code] is the HTTP status, [message]
         * the human-readable text extracted from the error body.
         */
        data class Server(val code: Int, val message: String) : Failure
    }
}