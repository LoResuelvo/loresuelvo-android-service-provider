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

    /** Concurrent persistent stores override this to compare and clear within the same write lock. */
    fun clearSessionDurably(expectedSession: AuthSession?): SessionClearOutcome {
        if (getSession() != expectedSession) return SessionClearOutcome.Cleared
        return clearSessionDurably()
    }
}

sealed interface SessionClearOutcome {
    data object Cleared : SessionClearOutcome
    data object PersistenceFailure : SessionClearOutcome
}
