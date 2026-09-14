package com.loresuelvo.serviceprovider.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MessagesListViewModel @Inject constructor(
    private val getConversations: GetConversationsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MessagesListUiState>(MessagesListUiState.Loading)
    val uiState: StateFlow<MessagesListUiState> = _uiState.asStateFlow()
    private var requestInFlight = false

    init {
        load()
    }

    fun load() {
        if (requestInFlight) return
        requestInFlight = true
        viewModelScope.launch {
            try {
                _uiState.update { MessagesListUiState.Loading }
                _uiState.update {
                    when (val outcome = getConversations()) {
                        is ConversationsOutcome.Success -> MessagesListUiState.Ready(outcome.conversations)
                        is ConversationsOutcome.Failure -> MessagesListUiState.Error(outcome)
                    }
                }
            } finally {
                requestInFlight = false
            }
        }
    }
}
