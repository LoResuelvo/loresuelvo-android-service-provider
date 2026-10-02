package com.loresuelvo.serviceprovider.ui.screens.messages

import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome

sealed interface MessagesListUiState {
    data object Loading : MessagesListUiState
    data class Ready(val conversations: List<Conversation>, val refreshFailure: ConversationsOutcome.Failure? = null) : MessagesListUiState
    data class Error(val failure: ConversationsOutcome.Failure) : MessagesListUiState
}
