package com.loresuelvo.serviceprovider.domain.usecase.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.notifications.*
import javax.inject.Inject

class ConsumeNotificationTargetUseCase @Inject constructor(
    private val sessions: AuthSessionStore,
    private val store: NotificationStateStore,
    private val local: NotificationLocalSession,
    private val clock: NotificationClock,
) {
    operator fun invoke(): Int? = synchronized(store) {
        val target = local.target.value ?: return@synchronized null
        local.queue(null)
        val binding = store.read().binding
        val session = sessions.getSession() ?: return@synchronized null
        val state = store.read()
        if (local.isInvalidated() || binding?.active != true || binding.subject != session.user.id ||
            target.bindingId != binding.id || target.expiresAt <= clock.nowMillis() ||
            state.handled.none { it.tapId == target.tapId && it == target }
        ) return@synchronized null
        if (!store.write(state.copy(handled = state.handled.map { if (it == target) it.copy(tapId = null) else it }))) null
        else target.conversationId
    }
}
