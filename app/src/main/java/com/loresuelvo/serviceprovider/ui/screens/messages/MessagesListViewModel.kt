package com.loresuelvo.serviceprovider.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.realtime.ProviderEvent
import com.loresuelvo.serviceprovider.domain.realtime.RealtimeState.Connection
import com.loresuelvo.serviceprovider.domain.usecase.realtime.ObserveRealtimeStateUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.realtime.ObserveProviderEventsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.realtime.ObserveProviderSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MessagesListViewModel @Inject constructor(
    private val getConversations: GetConversationsUseCase,
    observeEvents: ObserveProviderEventsUseCase? = null,
    observeSession: ObserveProviderSessionUseCase? = null,
    observeState: ObserveRealtimeStateUseCase? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MessagesListUiState>(MessagesListUiState.Loading)
    val uiState: StateFlow<MessagesListUiState> = _uiState.asStateFlow()
    private val invalidations = Channel<Unit>(Channel.CONFLATED)
    private var refreshJob: Job? = null
    private var sessionGeneration = 0L
    private var requestInFlight = false
    private var hasSession = observeSession?.invoke()?.value != null || observeSession == null

    init {
        observeEvents?.let { observe ->
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                observe().collect { envelope ->
                    if (envelope.event is ProviderEvent.MessageCreated) invalidations.trySend(Unit)
                }
            }
        }
        observeSession?.let { observe ->
            var previous = observe().value
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                observe().collect { session ->
                    if (session == previous) return@collect
                    previous = session
                    hasSession = session != null
                    sessionGeneration++
                    refreshJob?.cancel()
                    requestInFlight = false
                    _uiState.value = MessagesListUiState.Loading
                    if (hasSession) startRefresh()
                }
            }
        }
        observeState?.let { observe ->
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                observe().collect { state ->
                    if (state.connection == Connection.Connected && state.session == observeSession?.invoke()?.value) invalidations.trySend(Unit)
                }
            }
        }
        startRefresh()
    }

    private fun startRefresh() {
        requestInFlight = hasSession
        refreshJob = viewModelScope.launch {
            for (ignored in invalidations) {
                if (!hasSession) continue
                val generation = sessionGeneration
                requestInFlight = true
                val outcome = try { getConversations() } finally {
                    if (generation == sessionGeneration) requestInFlight = false
                }
                if (generation != sessionGeneration) continue
                _uiState.update { current ->
                    when (outcome) {
                        is ConversationsOutcome.Success -> MessagesListUiState.Ready(outcome.conversations)
                        is ConversationsOutcome.Failure -> if (current is MessagesListUiState.Ready && outcome != ConversationsOutcome.Failure.Unauthorized && !(outcome is ConversationsOutcome.Failure.Server && outcome.code == 403)) current else MessagesListUiState.Error(outcome)
                    }
                }
            }
        }
        invalidations.trySend(Unit)
    }

    fun load() {
        if (requestInFlight || !hasSession) return
        requestInFlight = true
        _uiState.value = MessagesListUiState.Loading
        invalidations.trySend(Unit)
    }
}
