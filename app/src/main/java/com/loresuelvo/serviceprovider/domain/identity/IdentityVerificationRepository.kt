package com.loresuelvo.serviceprovider.domain.identity

@JvmInline
value class IdentityVerificationCredential(val token: String)

sealed interface StartIdentityVerificationOutcome {
    data class Success(val credential: IdentityVerificationCredential) : StartIdentityVerificationOutcome
    data object AlreadyApproved : StartIdentityVerificationOutcome

    sealed interface Failure : StartIdentityVerificationOutcome {
        data object Unauthorized : Failure
        data object Forbidden : Failure
        data object InvalidResponse : Failure
        data object Network : Failure
        data class Server(val code: Int) : Failure
        data object Unknown : Failure
    }
}

interface IdentityVerificationRepository {
    suspend fun start(): StartIdentityVerificationOutcome
}
