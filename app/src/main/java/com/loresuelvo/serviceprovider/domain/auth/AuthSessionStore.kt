package com.loresuelvo.serviceprovider.domain.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthSessionStore {

    val sessionFlow: StateFlow<AuthSession?>

    fun getSession(): AuthSession?

    fun saveSession(session: AuthSession)

    fun clearSession()

    /** Reports durable removal while retaining the existing expiration cleanup contract. */
    fun clearSessionDurably(): SessionClearOutcome {
        clearSession()
        return SessionClearOutcome.Cleared
    }
}

sealed interface SessionClearOutcome {
    data object Cleared : SessionClearOutcome
    data object PersistenceFailure : SessionClearOutcome
}
