package com.loresuelvo.serviceprovider.domain.conversation

/**
 * Outcome of [ConversationRepository.getConversationById]. Sealed
 * so callers handle every branch explicitly — mirrors
 * [ConversationsOutcome].
 *
 * The repository never throws on HTTP / network failures: every
 * exception is mapped to a typed
 * [ConversationDetailOutcome.Failure] via `Throwable.toApiError()`,
 * so the use case / VM never has to reach for `try / catch`.
 */
sealed interface ConversationDetailOutcome {

    data class Success(val detail: ConversationDetail) : ConversationDetailOutcome

    sealed interface Failure : ConversationDetailOutcome {

        /** Transport-level failure: timeouts, DNS, connection refused. */
        data class Network(val cause: Throwable) : Failure

        /** Any non-2xx response with a parsable body. */
        data class Server(val code: Int, val message: String) : Failure

        /** 401: Auth0 session expired or invalid. */
        data object Unauthorized : Failure

        /** 404: the conversation no longer exists for this account. */
        data class NotFound(val message: String) : Failure
    }
}
