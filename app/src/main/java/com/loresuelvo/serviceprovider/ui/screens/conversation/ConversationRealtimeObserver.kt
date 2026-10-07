package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.realtime.ProviderEvent
import com.loresuelvo.serviceprovider.domain.realtime.RealtimeState.Connection
import com.loresuelvo.serviceprovider.domain.usecase.realtime.ObserveProviderEventsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.realtime.ObserveProviderSessionUseCase
import com.loresuelvo.serviceprovider.domain.usecase.realtime.ObserveRealtimeStateUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Subscription ownership is separate from the conversation's existing composer/media state machine. */
class ConversationRealtimeObserver @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeEvents: ObserveProviderEventsUseCase,
    private val observeSession: ObserveProviderSessionUseCase,
    private val observeState: ObserveRealtimeStateUseCase,
    private val notifications: com.loresuelvo.serviceprovider.domain.notifications.NotificationConversationState? = null,
) {
    private val conversationId: Int = checkNotNull(savedStateHandle[Route.Conversation.argument])
    val hasSession: Boolean get() = observeSession().value != null
    fun setVisible(visible: Boolean) {
        if (visible) observeSession().value?.let { notifications?.visible(it, conversationId) }
        else notifications?.hide(conversationId)
    }

    fun start(scope: CoroutineScope, onMessage: (ConversationMessage) -> Unit, onSessionChanged: () -> Unit, onConnected: () -> Unit) {
        val originalSession = observeSession().value
        notifications?.let { notificationState ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                notificationState.refreshes.collect { (session, id) ->
                    if (session == originalSession && observeSession().value == originalSession && id == conversationId) onConnected()
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            observeEvents().collect { envelope ->
                val event = envelope.event as? ProviderEvent.MessageCreated ?: return@collect
                if (envelope.session == originalSession && event.conversationId == conversationId) onMessage(event.message)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            observeSession().collect { if (it != originalSession) onSessionChanged() }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            observeState().collect {
                if (it.session == originalSession && it.connection == Connection.Connected && observeSession().value == originalSession) onConnected()
            }
        }
    }
}
