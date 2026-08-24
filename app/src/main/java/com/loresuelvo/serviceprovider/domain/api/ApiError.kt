package com.loresuelvo.serviceprovider.domain.api

/**
 * Pure-domain hierarchy for backend failures. Every subclass is
 * sealed and carries the structured information callers need to
 * decide what to do. Use cases translate [ApiError] into their own
 * outcome-specific `Failure` subtypes; this type is the common
 * transport-level vocabulary.
 *
 * - [Network]: transport-level failure (timeouts, DNS, refused).
 * - [Server]: any non-2xx response with a parsable body. [code] is
 *   the HTTP status; [errorMessage] is the human-readable message.
 * - [Unauthorized]: 401. Callers must clear the local session.
 * - [Unknown]: anything we couldn't classify. Wraps an optional
 *   cause for diagnostics.
 *
 * The class extends [Exception] only so that callers that prefer
 * the throw-and-catch style (e.g. coroutine `runCatching` patterns)
 * can use it. The data layer does not rely on the throwable nature;
 * the `Network.cause` / `Unknown.cause` properties hold the original
 * exception explicitly.
 */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    data class Network(val networkCause: Throwable) :
        ApiError("Network error", networkCause)

    data class Server(val code: Int, val errorMessage: String) :
        ApiError(errorMessage)

    data class Unauthorized(val errorMessage: String = "Unauthorized") :
        ApiError(errorMessage)

    data class Unknown(val unknownCause: Throwable? = null) :
        ApiError("Unknown error", unknownCause)
}