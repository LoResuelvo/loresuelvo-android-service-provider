package com.loresuelvo.serviceprovider.domain.usecase.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.notifications.*
import javax.inject.Inject

class AcceptNotificationTapUseCase @Inject constructor(
    private val sessions: AuthSessionStore,
    private val store: NotificationStateStore,
    private val local: NotificationLocalSession,
    private val clock: NotificationClock,
) {
    /** A tap capability must have been issued locally; exported Activity extras alone grant no access. */
    operator fun invoke(tapId: String?) = synchronized(store) {
        val state = store.read()
        val binding = state.binding
        val session = sessions.getSession()
        val record = state.handled.firstOrNull { it.tapId != null && it.tapId == tapId }
        if (session == null || local.isInvalidated() || binding?.active != true || binding.subject != session.user.id ||
            record == null || record.bindingId != binding.id || record.expiresAt <= clock.nowMillis()
        ) { local.queue(null); return@synchronized }
        local.queue(record)
    }
}
