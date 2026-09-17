package com.loresuelvo.serviceprovider.ui.screens.conversation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.data.media.MediaReader
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationByIdUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMediaMessageUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMessageUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
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
 * [GetConversationByIdUseCase] and either
 * `POST /conversations/{id}/messages` for text
 * ([SendMessageUseCase]) or media ([SendMediaMessageUseCase]),
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
 *  03-PCC / 04-PCC / 05-PCC, and 03-PCM / 05-PCM / 06-PCM for
 *  media):
 *  - [onPromptChange] mirrors the text field on
 *    [ProviderConversationUiState.Ready].
 *  - [onMediaPicked] resolves the URI through [MediaReader]
 *    (gallery picker or camera capture) and stages the resulting
 *    [MediaUpload.Image] into [pendingMedia]. IO failures map
 *    to a typed `transientMediaError` so the chat surface can
 *    surface them inline.
 *  - [onClearStagedMedia] discards the staged media and returns
 *    the input bar to its text-only state.
 *  - [onSendClick]:
 *      * Trims the prompt, bails on blank + no media + `sending`.
 *      * If [pendingMedia] is staged, fires [SendMediaMessageUseCase];
 *        otherwise fires [SendMessageUseCase].
 *      * Appends a [ChatListItem.LocalPending] optimistic bubble
 *        immediately (carrying the local bytes when sending
 *        media), clears the prompt + pendingMedia, flips
 *        `sending = true`.
 *      * On success, replaces the pending bubble with
 *        [ChatListItem.ServerConfirmed] carrying the
 *        server-persisted message.
 *      * On failure, replaces the pending bubble with
 *        [ChatListItem.LocalFailed] (carrying the original bytes
 *        / prompt so the retry handler can resubmit without the
 *        user re-typing / re-picking).
 *  - [onRetrySendFailedBubble] re-fires the use case for the
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
    private val sendMediaMessage: SendMediaMessageUseCase,
    private val mediaReader: MediaReader,
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

    fun onMediaPicked(uri: Uri) {
        viewModelScope.launch {
            val media = try {
                mediaReader.read(uri)
            } catch (e: IOException) {
                _uiState.update { current ->
                    if (current is ProviderConversationUiState.Ready) {
                        current.copy(
                            transientMediaError = SendMessageOutcome.Failure.Network(
                                cause = e,
                            ),
                        )
                    } else {
                        current
                    }
                }
                return@launch
            }
            _uiState.update { current ->
                when (current) {
                    is ProviderConversationUiState.Ready ->
                        current.copy(
                            pendingMedia = media,
                            promptInput = "",
                            transientMediaError = null,
                        )
                    else -> current
                }
            }
        }
    }

    fun onClearStagedMedia() {
        _uiState.update { current ->
            when (current) {
                is ProviderConversationUiState.Ready ->
                    current.copy(pendingMedia = null, transientMediaError = null)
                else -> current
            }
        }
    }

    fun onDismissMediaError() {
        _uiState.update { current ->
            when (current) {
                is ProviderConversationUiState.Ready ->
                    current.copy(transientMediaError = null)
                else -> current
            }
        }
    }

    fun onSendClick() {
        val state = _uiState.value as? ProviderConversationUiState.Ready ?: return
        val prompt = state.promptInput.trim()
        val media = state.pendingMedia
        if ((prompt.isEmpty() && media == null) || state.sending) return

        when {
            media != null -> fireSendMedia(media)
            prompt.isNotEmpty() -> fireSendText(prompt)
        }
    }

    fun onRetrySendFailedBubble(localKey: String) {
        val state = _uiState.value as? ProviderConversationUiState.Ready ?: return
        val failed = state.items
            .firstOrNull { it.key == localKey }
            as? ChatListItem.LocalFailed
            ?: return
        if (state.sending) return

        // Replace failed with pending (preserving the local key so
        // the LazyColumn animates the replacement in place).
        val pending = ChatListItem.LocalPending(
            key = failed.key,
            sender = failed.sender,
            content = failed.content,
            createdOnEpochMillis = failed.createdOnEpochMillis,
            pendingMedia = failed.pendingMedia,
        )
        _uiState.update { current ->
            (current as ProviderConversationUiState.Ready).copy(
                items = current.items.map { item ->
                    if (item.key == localKey) pending else item
                },
                sending = true,
            )
        }
        if (failed.pendingMedia != null) {
            fireSendMedia(failed.pendingMedia, retryKey = pending.key)
        } else {
            fireSendText(failed.pendingPrompt, retryKey = pending.key)
        }
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
                        pendingMedia = null,
                        transientMediaError = null,
                    )
                }
                is ConversationDetailOutcome.Failure ->
                    ProviderConversationUiState.Error(outcome)
            }
        }
    }

    private fun fireSendText(prompt: String, retryKey: String? = null) {
        val pendingKey = retryKey ?: newLocalKey()
        // On a fresh send the optimistic pending is appended to the
        // list; on a retry the caller already replaced the failed
        // bubble with a pending at the same key, so we only refresh
        // the input bar / `sending` flag and skip the append.
        if (retryKey == null) {
            val pending = ChatListItem.LocalPending(
                key = pendingKey,
                sender = ConversationSender.Provider,
                content = prompt,
                createdOnEpochMillis = System.currentTimeMillis(),
                pendingMedia = null,
            )
            _uiState.update { current ->
                (current as ProviderConversationUiState.Ready).copy(
                    promptInput = "",
                    pendingMedia = null,
                    items = current.items + pending,
                    sending = true,
                    transientMediaError = null,
                )
            }
        } else {
            _uiState.update { current ->
                (current as ProviderConversationUiState.Ready).copy(
                    promptInput = "",
                    pendingMedia = null,
                    sending = true,
                    transientMediaError = null,
                )
            }
        }
        viewModelScope.launch {
            val outcome = sendMessage(conversationId, prompt)
            resolveSendOutcome(pendingKey, prompt, null, outcome)
        }
    }

    private fun fireSendMedia(media: MediaUpload, retryKey: String? = null) {
        val pendingKey = retryKey ?: newLocalKey()
        if (retryKey == null) {
            val pending = ChatListItem.LocalPending(
                key = pendingKey,
                sender = ConversationSender.Provider,
                content = "",
                createdOnEpochMillis = System.currentTimeMillis(),
                pendingMedia = media,
            )
            _uiState.update { current ->
                (current as ProviderConversationUiState.Ready).copy(
                    promptInput = "",
                    pendingMedia = null,
                    items = current.items + pending,
                    sending = true,
                    transientMediaError = null,
                )
            }
        } else {
            _uiState.update { current ->
                (current as ProviderConversationUiState.Ready).copy(
                    promptInput = "",
                    pendingMedia = null,
                    sending = true,
                    transientMediaError = null,
                )
            }
        }
        viewModelScope.launch {
            val outcome = sendMediaMessage(conversationId, listOf(media))
            resolveSendOutcome(pendingKey, "", media, outcome)
        }
    }

    private fun resolveSendOutcome(
        pendingKey: String,
        prompt: String,
        media: MediaUpload?,
        outcome: SendMessageOutcome,
    ) {
        _uiState.update { current ->
            val ready = current as? ProviderConversationUiState.Ready ?: return@update current
            ready.copy(
                items = ready.items.map { item ->
                    if (item.key == pendingKey) item.toResolved(outcome, prompt, media) else item
                },
                sending = false,
            )
        }
    }

    private fun ChatListItem.toResolved(
        outcome: SendMessageOutcome,
        prompt: String,
        media: MediaUpload?,
    ): ChatListItem = when (outcome) {
        is SendMessageOutcome.Success -> ChatListItem.ServerConfirmed(outcome.message)
        is SendMessageOutcome.Failure -> ChatListItem.LocalFailed(
            key = key,
            sender = sender,
            content = content,
            createdOnEpochMillis = createdOnEpochMillis,
            pendingPrompt = prompt,
            pendingMedia = media,
        )
    }

    private fun newLocalKey(): String = "local-${UUID.randomUUID()}"
}
