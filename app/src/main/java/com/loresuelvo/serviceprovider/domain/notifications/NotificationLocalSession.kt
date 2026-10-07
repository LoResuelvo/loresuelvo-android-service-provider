package com.loresuelvo.serviceprovider.domain.notifications

import javax.inject.Inject
import javax.inject.Singleton
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

    override fun invalidate() = synchronized(store) {
        invalidated = true
        val state = store.read()
        store.write(state.copy(binding = state.binding?.copy(active = false), handled = emptyList()))
        pendingTarget.value = null
        conversations.hide()
        display.cancelAll()
    }

    fun activate() { invalidated = false }
    fun isInvalidated(): Boolean = invalidated
    fun queue(target: HandledNotification?) { pendingTarget.value = target }
}
