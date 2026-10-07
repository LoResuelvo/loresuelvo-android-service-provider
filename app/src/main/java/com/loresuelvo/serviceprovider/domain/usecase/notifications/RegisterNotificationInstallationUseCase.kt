package com.loresuelvo.serviceprovider.domain.usecase.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.notifications.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class RegisterNotificationInstallationUseCase @Inject constructor(
    private val sessions: AuthSessionStore,
    private val store: NotificationStateStore,
    private val repository: InstallationRepository,
    private val tokens: NotificationTokenSource,
    private val local: NotificationLocalSession,
) {
    private val registration = Mutex()

    suspend operator fun invoke(recipientId: Int, locale: String) = registration.withLock {
        val session = sessions.getSession() ?: return@withLock
        if (recipientId <= 0) return@withLock
        val installation = synchronized(store) {
            val state = store.read()
            val binding = state.binding
            if (binding?.active == true && binding.subject == session.user.id && binding.recipientId == recipientId && !local.isInvalidated()) state
            else {
                local.invalidate()
                state.copy(
                    binding = NotificationBinding(UUID.randomUUID().toString(), session.user.id, recipientId, active = true),
                    previousBindingId = binding?.takeIf { it.acknowledged }?.id ?: state.previousBindingId,
                    handled = emptyList(),
                ).also { if (store.write(it)) local.activate() }
            }
        }
        if (local.isInvalidated()) return@withLock
        val token = withTimeoutOrNull(3_000) { tokens.token() } ?: return@withLock
        if (sessions.getSession() != session || local.isInvalidated()) return@withLock
        val result = withTimeoutOrNull(3_000) {
            repository.register(installation, token, if (locale == "en") "en" else "es", session)
        }
        synchronized(store) {
            val current = store.read()
            if (sessions.getSession() == session && !local.isInvalidated() && current.binding?.active == true &&
                current.binding.id == installation.binding?.id && result == InstallationResult.Applied) {
                store.write(current.copy(binding = current.binding?.copy(acknowledged = true)))
            }
        }
    }
}
