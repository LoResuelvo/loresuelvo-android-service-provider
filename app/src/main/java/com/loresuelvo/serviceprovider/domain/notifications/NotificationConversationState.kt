package com.loresuelvo.serviceprovider.domain.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class NotificationConversationState @Inject constructor() {
    @Volatile private var visible: Pair<AuthSession, Int>? = null
    private val refreshEvents = MutableSharedFlow<Pair<AuthSession, Int>>(extraBufferCapacity = 16)
    val refreshes = refreshEvents.asSharedFlow()

    fun visible(session: AuthSession, conversationId: Int) { visible = session to conversationId }
    fun hide(conversationId: Int? = null) {
        if (conversationId == null || visible?.second == conversationId) visible = null
    }
    fun suppress(session: AuthSession, conversationId: Int): Boolean {
        if (visible != (session to conversationId)) return false
        refreshEvents.tryEmit(session to conversationId)
        return true
    }
}
