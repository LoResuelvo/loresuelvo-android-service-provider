package com.loresuelvo.serviceprovider.domain.conversation

/**
 * Outcome of [ConversationRepository.sendMessage] and
 * [ConversationRepository.sendMediaMessage]. Sealed so callers
 * handle every branch explicitly — mirrors
 * [ConversationsOutcome].
 *
 * On success, the carried [ConversationMessage] is the
 * server-persisted bubble (with the backend-issued numeric id
 * and the authoritative `created_on` timestamp). The VM uses it
 * to replace the optimistic bubble it appended locally — the
 * optimistic id (`local-<uuid>`) is dropped in favour of the
 * stable server id so `LazyColumn` keys stay stable across
 * rotation and process death.
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

        /**
         * A media payload exceeded the client-side size limit
         * (audio only; images are bounded by the picker). The
         * use case rejects oversized payloads before the
         * network round-trip so the backend never sees them.
         *
         * Reserved for US-C (audio attachments). Kept in the
         * shared `Failure` tree so US-B callers can pattern-match
         * the exhaustive `when` without future churn.
         */
        data class PayloadTooLarge(val maxBytes: Long) : Failure
    }
}
