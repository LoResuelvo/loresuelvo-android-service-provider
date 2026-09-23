package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationCredential
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import java.net.URI
import java.util.UUID
import javax.inject.Inject

class ApiIdentityVerificationRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val sessionStore: AuthSessionStore,
) : IdentityVerificationRepository {
    override suspend fun start(): StartIdentityVerificationOutcome {
        val requestSession = sessionStore.getSession()
        return try {
            val response = backendApi.startIdentityVerification()
            if (!response.isValid()) {
                StartIdentityVerificationOutcome.Failure.InvalidResponse
            } else {
                StartIdentityVerificationOutcome.Success(
                    IdentityVerificationCredential(response.sessionToken),
                )
            }
        } catch (throwable: Throwable) {
            when (val error = throwable.toApiError()) {
                is ApiError.Unauthorized -> {
                    if (sessionStore.getSession() == requestSession) sessionStore.clearSession()
                    StartIdentityVerificationOutcome.Failure.Unauthorized
                }
                is ApiError.Network -> StartIdentityVerificationOutcome.Failure.Network
                is ApiError.Server -> when (error.code) {
                    403 -> StartIdentityVerificationOutcome.Failure.Forbidden
                    409 -> StartIdentityVerificationOutcome.AlreadyApproved
                    else -> StartIdentityVerificationOutcome.Failure.Server(error.code)
                }
                is ApiError.Unknown -> StartIdentityVerificationOutcome.Failure.Unknown
            }
        }
    }

    private fun com.loresuelvo.serviceprovider.data.api.dto.IdentityVerificationSessionDto.isValid(): Boolean =
        runCatching { UUID.fromString(sessionId) }.isSuccess &&
            sessionToken.isNotBlank() &&
            status.isNotBlank() &&
            runCatching { URI(verificationUrl).scheme.equals("https", ignoreCase = true) }.getOrDefault(false)
}
