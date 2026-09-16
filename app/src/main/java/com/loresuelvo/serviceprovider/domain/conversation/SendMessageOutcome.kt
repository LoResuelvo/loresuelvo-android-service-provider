package com.loresuelvo.serviceprovider.domain.conversation

/**
 * Outcome of [ConversationRepository.sendMessage]. Sealed so
 * callers handle every branch explicitly — mirrors
 * [ConversationsOutcome].
 *
 * On success, the carried [ConversationMessage] is the
 * server-persisted bubble (with the backend-issued id and the
 * authoritative `created_on` timestamp). The VM uses it to replace
 * the optimistic bubble it appended locally.
 *
 * The repository never throws on HTTP / network failures: every
 * exception is mapped to a typed [SendMessageOutcome.Failure].
 */
sealed interface SendMessageOutcome {

    data class Success(val message: ConversationMessage) : SendMessageOutcome

    sealed interface Failure : SendMessageOutcome {

        /** Transport-level failure: timeouts, DNS, connection refused. */
        data class Network(val cause: Throwable) : Failure

        /** Any non-2xx response with a parsable body. */
        data class Server(val code: Int, val message: String) : Failure

        /** 401: Auth0 session expired or invalid. */
        data object Unauthorized : Failure

        /**
         * 404 on `conversationId` — the consumer or provider dropped
         * the thread. The use case surfaces this distinctly from a
         * generic server error so the VM can route back to the
         * inbox rather than offering a retry.
         */
        data class ConversationNotFound(val message: String) : Failure
    }
}
