package com.loresuelvo.serviceprovider.domain.notifications

import javax.inject.Inject
import javax.inject.Singleton
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NotificationLocalSession @Inject constructor(
    private val store: NotificationStateStore,
    private val display: NotificationDisplay,
    private val conversations: NotificationConversationState,
) : NotificationSessionCleanup {
    private val pendingTarget = MutableStateFlow<HandledNotification?>(null)
    val target = pendingTarget.asStateFlow()
    @Volatile private var invalidated = false
    @Volatile private var generation = 0L
    @Volatile private var verifiedAccount: VerifiedNotificationAccount? = null

    override fun invalidate() = synchronized(store) {
        generation++
        invalidated = true
        verifiedAccount = null
        val state = store.read()
        store.write(state.copy(binding = state.binding?.copy(active = false), handled = emptyList(), establishedSessionKey = null))
        pendingTarget.value = null
        conversations.hide()
        display.cancelAll()
    }

    override fun establish(session: AuthSession) = synchronized(store) {
        generation++
        verifiedAccount = null
        store.write(store.read().copy(establishedSessionKey = VerifiedNotificationAccount.from(session, 0).sessionKey))
        Unit
    }

    fun activate() { invalidated = false }
    fun isInvalidated(): Boolean = invalidated
    fun queue(target: HandledNotification?) { pendingTarget.value = target }

    fun generation(): Long = generation

    /** /me proof cannot cross logout, or revive the JWT temporarily available during bounded removal. */
    fun verify(session: AuthSession, recipientId: Int, expectedGeneration: Long = generation): Boolean = synchronized(store) {
        val account = VerifiedNotificationAccount.from(session, recipientId)
        val binding = store.read().binding
        if (generation != expectedGeneration ||
            (binding?.active == false && store.read().establishedSessionKey != account.sessionKey)) return@synchronized false
        verifiedAccount = account
        true
    }

    /** Rebinding cancels the previous UI capability without revoking the already verified new login. */
    fun clearPreviousTargets() = synchronized(store) {
        pendingTarget.value = null
        conversations.hide()
        display.cancelAll()
    }

    fun accountFor(session: AuthSession?): VerifiedNotificationAccount? = synchronized(store) {
        if (session == null) return@synchronized null
        verifiedAccount?.takeIf { it.matches(session) } ?: store.read().binding?.let { binding ->
            val key = binding.sessionKey ?: return@let null
            VerifiedNotificationAccount(binding.subject, key, binding.recipientId)
                .takeIf { binding.active && binding.acknowledged && !invalidated && it.matches(session) }
        }
    }
}
