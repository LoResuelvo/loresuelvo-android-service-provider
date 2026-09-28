package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.usecase.activity.ConversationWorkOrderResolution
import com.loresuelvo.serviceprovider.domain.usecase.activity.ResolveConversationWorkOrderUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConversationOrderLinkUiState {
    data object Loading : ConversationOrderLinkUiState
    data class Linked(val orderId: Int) : ConversationOrderLinkUiState
    data object Missing : ConversationOrderLinkUiState
    data object Error : ConversationOrderLinkUiState
}

@HiltViewModel
class ConversationOrderLinkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resolve: ResolveConversationWorkOrderUseCase,
    private val sessions: AuthSessionStore,
) : ViewModel() {
    private val conversationId: Int = savedStateHandle[Route.Conversation.argument] ?: 0
    private var job: Job? = null
    private var activeSession = sessions.getSession()
    private val _uiState = MutableStateFlow<ConversationOrderLinkUiState>(ConversationOrderLinkUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    job?.cancel()
                    _uiState.value = ConversationOrderLinkUiState.Missing
                }
            }
        }
        load()
    }

    fun load() {
        if (job?.isActive == true) return
        val requestSession = sessions.getSession()
        if (requestSession == null) {
            _uiState.value = ConversationOrderLinkUiState.Missing
            return
        }
        _uiState.value = ConversationOrderLinkUiState.Loading
        job = viewModelScope.launch {
            val result = resolve(conversationId)
            if (sessions.getSession() != requestSession) return@launch
            _uiState.value = when (result) {
                is ConversationWorkOrderResolution.Linked -> ConversationOrderLinkUiState.Linked(result.orderId)
                ConversationWorkOrderResolution.Missing -> ConversationOrderLinkUiState.Missing
                ConversationWorkOrderResolution.Unavailable -> ConversationOrderLinkUiState.Error
                ConversationWorkOrderResolution.SessionExpired -> {
                    sessions.clearSession()
                    ConversationOrderLinkUiState.Missing
                }
            }
        }
    }
}
