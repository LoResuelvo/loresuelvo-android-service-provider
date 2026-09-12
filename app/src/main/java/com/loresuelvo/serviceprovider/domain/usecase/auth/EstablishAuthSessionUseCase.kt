package com.loresuelvo.serviceprovider.domain.usecase.auth

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Establishes the local authenticated-session boundary after an identity
 * provider returns validated credentials.
 *
 * The use case deliberately owns no Auth0 or Android details. The supplied
 * [AuthSessionStore] is the same process-wide port consumed by authenticated
 * HTTP infrastructure, so saving here makes the bearer token immediately
 * available to that infrastructure.
 */
@Singleton
class EstablishAuthSessionUseCase @Inject constructor(
    private val sessionStore: AuthSessionStore,
) {

    operator fun invoke(session: AuthSession) {
        sessionStore.saveSession(session)
    }
}
