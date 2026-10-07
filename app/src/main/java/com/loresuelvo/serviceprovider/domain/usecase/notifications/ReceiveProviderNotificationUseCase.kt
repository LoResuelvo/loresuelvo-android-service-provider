package com.loresuelvo.serviceprovider.domain.usecase.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.notifications.*
import java.util.UUID
import javax.inject.Inject

// Cohesion: receipt jointly enforces session, persistence, visibility, display, and time; ports stay explicit.
class ReceiveProviderNotificationUseCase @Inject constructor(
    private val sessions: AuthSessionStore,
    private val store: NotificationStateStore,
    private val display: NotificationDisplay,
    private val local: NotificationLocalSession,
    private val conversations: NotificationConversationState,
    private val clock: NotificationClock,
) {
    operator fun invoke(notice: ProviderNotification): ReceiptOutcome = synchronized(store) {
        val session = sessions.getSession() ?: return@synchronized ReceiptOutcome.Rejected
        val state = store.read()
        val binding = state.binding ?: return@synchronized ReceiptOutcome.Rejected
        val now = clock.nowMillis()
        if (local.isInvalidated() || !binding.active || !binding.acknowledged || binding.subject != session.user.id ||
            notice.recipientId != binding.recipientId || notice.installationId != state.id || notice.bindingId != binding.id ||
            notice.conversationId <= 0 || notice.eventId.isBlank() || notice.title.isBlank() || notice.body.isBlank() || notice.expiresAt <= now
        ) return@synchronized ReceiptOutcome.Rejected
        val handled = state.handled.filter { it.expiresAt > now }
        if (handled.any { it.eventId == notice.eventId }) return@synchronized ReceiptOutcome.Duplicate
        // ponytail: retain at most 512 unexpired IDs; drop overflow rather than replay alerts. Add a database if volume requires it.
        if (handled.size >= 512) return@synchronized ReceiptOutcome.Rejected
        val suppressed = conversations.suppress(session, notice.conversationId)
        val tapId = if (suppressed) null else UUID.randomUUID().toString()
        val record = HandledNotification(notice.eventId, binding.id, notice.conversationId, notice.expiresAt, tapId)
        if (!store.write(state.copy(handled = handled + record))) return@synchronized ReceiptOutcome.Rejected
        if (suppressed) return@synchronized ReceiptOutcome.Suppressed
        if (!display.canPost() || sessions.getSession() != session || local.isInvalidated() || notice.expiresAt <= clock.nowMillis()) return@synchronized ReceiptOutcome.Rejected
        if (display.post(notice, checkNotNull(tapId))) ReceiptOutcome.Posted else ReceiptOutcome.Rejected
    }
}

enum class ReceiptOutcome { Posted, Suppressed, Duplicate, Rejected }
