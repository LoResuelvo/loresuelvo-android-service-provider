package com.loresuelvo.serviceprovider.domain.realtime

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RealtimeClient {
    val events: Flow<SessionEvent>
    val state: StateFlow<RealtimeState>
    /** Owns one authenticated connection until cancelled. May be invoked again after cancellation. */
    suspend fun connect(session: AuthSession)
}

data class SessionEvent(val session: AuthSession, val event: ProviderEvent)

sealed interface ProviderEvent {
    data class MessageCreated(val conversationId: Int, val message: ConversationMessage) : ProviderEvent
    data class NotificationCreated(val notification: ProviderNotification) : ProviderEvent
}

data class ProviderNotification(
    val id: Int,
    val userId: Int,
    val type: String,
    val resourceType: String,
    val resourceId: Int,
    val readOnEpochMillis: Long?,
    val createdOnEpochMillis: Long,
    val estimatedDurationMinutes: Int?,
)

data class RealtimeState(val session: AuthSession? = null, val connection: Connection = Connection.Stopped) {
    enum class Connection { Stopped, Connecting, Connected, Retrying, Unavailable, Unauthorized }
}
