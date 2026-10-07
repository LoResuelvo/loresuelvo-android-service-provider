package com.loresuelvo.serviceprovider.domain.usecase.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.SessionClearOutcome
import com.loresuelvo.serviceprovider.domain.notifications.*
import javax.inject.Inject
import kotlinx.coroutines.withTimeoutOrNull

class LogoutNotificationSessionUseCase @Inject constructor(
    private val sessions: AuthSessionStore,
    private val store: NotificationStateStore,
    private val repository: InstallationRepository,
    private val local: NotificationLocalSession,
) {
    suspend operator fun invoke(session: AuthSession?): SessionClearOutcome {
        val installation = synchronized(store) {
            if (sessions.getSession() != session) return SessionClearOutcome.Cleared
            store.read().also { local.invalidate() }
        }
        var outcome: SessionClearOutcome = SessionClearOutcome.Cleared
        try {
            if (session != null && installation.binding != null) withTimeoutOrNull(3_000) { repository.remove(installation, session) }
        } finally {
            outcome = sessions.clearSessionDurably(session)
        }
        return outcome
    }
}
