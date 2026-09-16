package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationByIdUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMessageUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UDF ViewModel for the provider conversation detail screen
 * (`Route.Conversation`). Drives `GET /conversations/{id}` through
 * [GetConversationByIdUseCase] and `POST
 * /conversations/{id}/messages` through [SendMessageUseCase],
 * mapping the typed outcomes into the sealed
 * [ProviderConversationUiState].
 *
 * Loading:
 *  - [init] fires [load] once; the screen rerun on configuration
 *    change gets the same VM instance through Hilt's route-scoping
 *    so the state survives rotation.
 *  - [onRetryLoad] re-fires the same fetch — used by the screen's
 *    error-state retry CTA.
 *
 * Compose flow (mirrors the consumer's [com.loresuelvo.consumer.ui.screens.chat.ConversationViewModel]
 *  but with persistent pending / failed bubbles — see scenarios
 *  03-PCC / 04-PCC / 05-PCC):
 *  - [onPromptChange] mirrors the field on [ProviderConversationUiState.Ready].
 *  - [onSendClick]:
 *      * Trims the prompt, bails on blank or `sending = true`.
 *      * Appends a [ChatListItem.LocalPending] optimistic bubble
 *        immediately, clears the prompt, flips `sending = true`.
 *      * Fires [SendMessageUseCase]; on success, replaces the
 *        pending bubble with [ChatListItem.ServerConfirmed]
 *        carrying the server-persisted message; on failure,
 *        replaces with [ChatListItem.LocalFailed] (the bubble
 *        stays visible with its own retry CTA).
 *  - [onRetrySendFailedBubble] re-fires [SendMessageUseCase] with
 *    the stored [ChatListItem.LocalFailed.pendingPrompt] for the
 *    given local key. The flow mirrors [onSendClick] — pending
 *    bubble replaced by confirmed or failed.
 *
 * The conversation id is provided by the host
 * ([com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNav])
 * via `SavedStateHandle` (read from the `Route.Conversation.argument`
 * nav argument) and is immutable for the lifetime of the route
 * entry.
 */
@HiltViewModel
class ProviderConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getConversationById: GetConversationByIdUseCase,
    private val sendMessage: SendMessageUseCase,
) : ViewModel() {

    private val conversationId: Int = savedStateHandle.get<Int>(Route.Conversation.argument)
        ?: error("conversationId must be present in the nav arguments")

    private val _uiState = MutableStateFlow<ProviderConversationUiState>(
        ProviderConversationUiState.Loading,
    )

    val uiState: StateFlow<ProviderConversationUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetryLoad() {
        load()
    }

    fun onPromptChange(value: String) {
        _uiState.update { current ->
            when (current) {
                is ProviderConversationUiState.Ready ->
                    current.copy(promptInput = value)
                else -> current
            }
        }
    }

    fun onSendClick() {
        val state = _uiState.value as? ProviderConversationUiState.Ready ?: return
        val prompt = state.promptInput.trim()
        if (prompt.isEmpty() || state.sending) return

        val pending = ChatListItem.LocalPending(
            key = newLocalKey(),
            sender = ConversationSender.Provider,
            content = prompt,
            createdOnEpochMillis = System.currentTimeMillis(),
        )
        _uiState.update { current ->
            (current as ProviderConversationUiState.Ready).copy(
                promptInput = "",
                items = current.items + pending,
                sending = true,
            )
        }
        fireSend(pending, prompt)
    }

    fun onRetrySendFailedBubble(localKey: String) {
        val state = _uiState.value as? ProviderConversationUiState.Ready ?: return
        val failed = state.items
            .firstOrNull { it.key == localKey }
            as? ChatListItem.LocalFailed
            ?: return
        if (state.sending) return

        val pending = ChatListItem.LocalPending(
            key = failed.key,
            sender = failed.sender,
            content = failed.content,
            createdOnEpochMillis = failed.createdOnEpochMillis,
        )
        _uiState.update { current ->
            (current as ProviderConversationUiState.Ready).copy(
                items = current.items.map { item ->
                    if (item.key == localKey) pending else item
                },
                sending = true,
            )
        }
        fireSend(pending, failed.pendingPrompt)
    }

    private fun load() {
        _uiState.value = ProviderConversationUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val outcome = getConversationById(conversationId)) {
                is ConversationDetailOutcome.Success -> {
                    ProviderConversationUiState.Ready(
                        detail = outcome.detail,
                        items = outcome.detail.messages.map(ChatListItem::ServerConfirmed),
                        promptInput = "",
                        sending = false,
                    )
                }
                is ConversationDetailOutcome.Failure ->
                    ProviderConversationUiState.Error(outcome)
            }
        }
    }

    private fun fireSend(pending: ChatListItem.LocalPending, prompt: String) {
        viewModelScope.launch {
            val outcome = sendMessage(conversationId, prompt)
            _uiState.update { current ->
                val ready = current as? ProviderConversationUiState.Ready ?: return@update current
                ready.copy(
                    items = ready.items.map { item ->
                        if (item.key == pending.key) item.toResolved(outcome) else item
                    },
                    sending = false,
                )
            }
        }
    }

    private fun ChatListItem.toResolved(outcome: SendMessageOutcome): ChatListItem = when (outcome) {
        is SendMessageOutcome.Success -> ChatListItem.ServerConfirmed(outcome.message)
        is SendMessageOutcome.Failure -> ChatListItem.LocalFailed(
            key = key,
            sender = sender,
            content = content,
            createdOnEpochMillis = createdOnEpochMillis,
            pendingPrompt = content,
        )
    }

    private fun newLocalKey(): String = "local-${UUID.randomUUID()}"
}
