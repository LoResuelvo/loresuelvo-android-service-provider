package com.loresuelvo.serviceprovider.domain.usecase.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
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

    suspend operator fun invoke(account: VerifiedNotificationAccount, locale: String): RegistrationOutcome =
        withTimeoutOrNull(8_000) {
            registration.withLock { register(account, if (locale == "en") "en" else "es") }
        } ?: RegistrationOutcome.Unavailable

    // Cohesion: this bounded transaction owns binding creation, durable uncertainty and HTTP acknowledgement.
    // Keep the transitions together to preserve one mutex/session guard. If retry policy expands, extract its
    // predecessor selection; stateful contract tests cover response loss, capacity and stale callbacks.
    private suspend fun register(account: VerifiedNotificationAccount, locale: String): RegistrationOutcome {
        val session = sessions.getSession()?.takeIf(account::matches) ?: return RegistrationOutcome.Unauthenticated
        if (account.recipientId <= 0 || local.accountFor(session) != account) return RegistrationOutcome.Unauthenticated
        val installation = synchronized(store) {
            val state = store.read()
            if (sessions.getSession() != session || local.accountFor(session) != account) return RegistrationOutcome.Unauthenticated
            val binding = state.binding
            if (binding?.active == true && binding.subject == account.subject && binding.sessionKey == account.sessionKey &&
                binding.recipientId == account.recipientId && !local.isInvalidated()) state
            else {
                // ponytail: preserve at most eight unresolved server candidates; stop rebinding at capacity rather than evicting evidence.
                if (state.attemptedBindingIds.size >= MAX_ATTEMPTED_BINDINGS) return RegistrationOutcome.CapacityReached
                local.clearPreviousTargets()
                state.copy(
                    binding = NotificationBinding(UUID.randomUUID().toString(), account.subject, account.recipientId,
                        active = true, sessionKey = account.sessionKey),
                    previousBindingId = state.acknowledgedBindingId,
                    handled = emptyList(), registrationRejected = false,
                ).also {
                    if (!store.write(it)) return RegistrationOutcome.StorageFailure
                    local.activate()
                }
            }
        }
        val token = withTimeoutOrNull(3_000) { tokens.token() } ?: return RegistrationOutcome.Unavailable
        if (token.isBlank() || token.length > 4096 || token.any(Char::isWhitespace)) return RegistrationOutcome.Rejected
        if (!owns(session, installation)) return RegistrationOutcome.Unauthenticated
        if (installation.registrationToken == token && installation.registrationLocale == locale) {
            if (installation.registrationRejected) return RegistrationOutcome.Rejected
            if (installation.binding?.acknowledged == true && installation.attemptedBindingIds.isEmpty()) return RegistrationOutcome.Ready
        }
        val bindingId = checkNotNull(installation.binding).id
        val attempted = synchronized(store) {
            val current = store.read()
            if (!owns(session, installation)) return RegistrationOutcome.Unauthenticated
            val candidates = (current.attemptedBindingIds + bindingId).distinct()
            if (candidates.size > MAX_ATTEMPTED_BINDINGS) return RegistrationOutcome.CapacityReached
            current.copy(attemptedBindingIds = candidates, registrationToken = token,
                registrationLocale = locale, registrationRejected = false).also {
                if (!store.write(it)) return RegistrationOutcome.StorageFailure
            }
        }
        val predecessors = (listOfNotNull(attempted.acknowledgedBindingId) +
            attempted.attemptedBindingIds.asReversed().filter { it != bindingId }).map { it as String? }.plus(null).distinct()
        for (previous in predecessors) {
            if (!owns(session, installation)) return RegistrationOutcome.Unauthenticated
            val result = withTimeoutOrNull(3_000) { repository.register(attempted.copy(previousBindingId = previous), token, locale, session) }
                ?: InstallationResult.TransientFailure
            when (result) {
                InstallationResult.Applied -> return synchronized(store) {
                    val current = store.read()
                    val active = owns(session, installation)
                    if (!store.write(current.copy(
                            acknowledgedBindingId = bindingId,
                            attemptedBindingIds = emptyList(),
                            binding = current.binding?.let { if (active) it.copy(acknowledged = true) else it },
                            registrationRejected = false,
                        ))) RegistrationOutcome.StorageFailure
                    else if (active) RegistrationOutcome.Ready else RegistrationOutcome.Unauthenticated
                }
                InstallationResult.Conflict -> continue
                InstallationResult.TransientFailure -> return RegistrationOutcome.Unavailable
                InstallationResult.Unauthorized -> {
                    sessions.clearSessionDurably(session)
                    return RegistrationOutcome.Unauthenticated
                }
                InstallationResult.Forbidden, InstallationResult.Invalid -> return reject(installation, session)
            }
        }
        return reject(installation, session)
    }

    private fun owns(session: AuthSession, installation: NotificationInstallation): Boolean = sessions.getSession() == session &&
        !local.isInvalidated() && store.read().binding?.let { it.active && it.id == installation.binding?.id } == true

    private fun reject(installation: NotificationInstallation, session: AuthSession): RegistrationOutcome = synchronized(store) {
        if (!owns(session, installation)) return@synchronized RegistrationOutcome.Unauthenticated
        if (!store.write(store.read().copy(registrationRejected = true))) RegistrationOutcome.StorageFailure else RegistrationOutcome.Rejected
    }

    companion object { const val MAX_ATTEMPTED_BINDINGS = 8 }
}

enum class RegistrationOutcome { Ready, Unavailable, Rejected, Unauthenticated, StorageFailure, CapacityReached }
