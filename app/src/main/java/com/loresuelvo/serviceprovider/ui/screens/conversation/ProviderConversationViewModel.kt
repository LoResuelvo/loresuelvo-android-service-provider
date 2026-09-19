package com.loresuelvo.serviceprovider.ui.screens.conversation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.data.media.AudioPlayer
import com.loresuelvo.serviceprovider.data.media.AudioRecorder
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UDF ViewModel for the provider conversation detail screen
 * (`Route.Conversation`).
 *
 * Drives the text / image / audio flows end-to-end:
 *
 * Loading: see [load].
 *
 * Text flow: see [onSendClick] (US-A, scenarios 03-PCC..06-PCC).
 *
 * Image flow (US-B, scenarios 01-PCM..07-PCM): see [onMediaPicked]
 * for the gallery / camera URI handler and [onClearStagedMedia]
 * for the preview discard.
 *
 * Audio flow (US-C, scenarios 01-PCA..03-PCA): see
 * [onStartRecording] / [onStopRecording] / [onCancelRecording]
 * for the in-app recorder and [onPlayAudio] / [onPauseAudio] for
 * the bubble-level playback controls.
 */
@HiltViewModel
class ProviderConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getConversationById: GetConversationByIdUseCase,
    private val sendMessage: SendMessageUseCase,
    private val sendMediaMessage: SendMediaMessageUseCase,
    private val mediaReader: MediaReader,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val conversationId: Int = savedStateHandle.get<Int>(Route.Conversation.argument)
        ?: error("conversationId must be present in the nav arguments")

    private val _uiState = MutableStateFlow<ProviderConversationUiState>(
        ProviderConversationUiState.Loading,
    )

    val uiState: StateFlow<ProviderConversationUiState> = _uiState.asStateFlow()

    /** Coroutine that drives [RecordingState.Recording.elapsedMillis]. */
    private var recordingTickerJob: Job? = null

    init {
        load()
        observeAudioPlayer()
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

    /**
     * Starts an in-app audio recording. The route must have
     * already acquired the `RECORD_AUDIO` runtime permission
     * before invoking this — the VM does NOT request it (UI layer
     * concern).
     */
    fun onStartRecording() {
        val started = audioRecorder.start()
        if (started.isFailure) {
            _uiState.update { current ->
                if (current is ProviderConversationUiState.Ready) {
                    current.copy(
                        transientMediaError = SendMessageOutcome.Failure.Server(
                            code = 0,
                            message = started.exceptionOrNull()?.message
                                ?: "Could not start audio recording",
                        ),
                    )
                } else {
                    current
                }
            }
            return
        }
        _uiState.update { current ->
            if (current is ProviderConversationUiState.Ready) {
                current.copy(
                    recordingState = RecordingState.Recording(elapsedMillis = 0L),
                )
            } else {
                current
            }
        }
        recordingTickerJob?.cancel()
        recordingTickerJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (true) {
                delay(250L)
                val elapsed = System.currentTimeMillis() - startedAt
                _uiState.update { current ->
                    if (current is ProviderConversationUiState.Ready &&
                        current.recordingState is RecordingState.Recording
                    ) {
                        current.copy(
                            recordingState = RecordingState.Recording(elapsedMillis = elapsed),
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    /**
     * Stops the in-progress recording and stages the captured
     * clip as [MediaUpload.Audio] in
     * [ProviderConversationUiState.Ready.pendingMedia].
     */
    fun onStopRecording() {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        val stopped = audioRecorder.stop()
        val uri = stopped.getOrNull()
        if (uri == null) {
            _uiState.update { current ->
                if (current is ProviderConversationUiState.Ready) {
                    current.copy(
                        recordingState = RecordingState.Idle,
                        transientMediaError = SendMessageOutcome.Failure.Server(
                            code = 0,
                            message = stopped.exceptionOrNull()?.message
                                ?: "Could not stop audio recording",
                        ),
                    )
                } else {
                    current
                }
            }
            return
        }
        viewModelScope.launch {
            val media = try {
                mediaReader.read(uri)
            } catch (e: IOException) {
                _uiState.update { current ->
                    if (current is ProviderConversationUiState.Ready) {
                        current.copy(
                            recordingState = RecordingState.Idle,
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
                if (current is ProviderConversationUiState.Ready) {
                    current.copy(
                        pendingMedia = media,
                        recordingState = RecordingState.Idle,
                        promptInput = "",
                    )
                } else {
                    current
                }
            }
        }
    }

    fun onCancelRecording() {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        audioRecorder.cancel()
        _uiState.update { current ->
            if (current is ProviderConversationUiState.Ready) {
                current.copy(recordingState = RecordingState.Idle)
            } else {
                current
            }
        }
    }

    fun onPlayAudio(bubbleKey: String, url: String) {
        audioPlayer.play(url)
        _uiState.update { current ->
            if (current is ProviderConversationUiState.Ready) {
                current.copy(playingMediaKey = bubbleKey)
            } else {
                current
            }
        }
    }

    fun onPauseAudio() {
        audioPlayer.pause()
        _uiState.update { current ->
            if (current is ProviderConversationUiState.Ready) {
                current.copy(playingMediaKey = null)
            } else {
                current
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

    override fun onCleared() {
        super.onCleared()
        recordingTickerJob?.cancel()
        audioRecorder.cancel()
        audioPlayer.stop()
    }

    private fun load() {
        _uiState.value = ProviderConversationUiState.Loading
        // Drop any playback the user might have started on a
        // previous screen so the bubble doesn't show a stale
        // `isPlaying = true` after navigation.
        audioPlayer.stop()
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
                        recordingState = RecordingState.Idle,
                        playingMediaKey = null,
                        playingPositionMillis = 0L,
                        isPlaying = false,
                    )
                }
                is ConversationDetailOutcome.Failure ->
                    ProviderConversationUiState.Error(outcome)
            }
        }
    }

    /**
     * Mirrors [AudioPlayer.isPlaying] and
     * [AudioPlayer.currentPositionMillis] into the UI state so
     * the bubble can swap its play / pause icon and advance its
     * progress bar without leaking the player itself into the UI
     * layer. Runs for the whole ViewModel lifetime; the bubble
     * decides whether to render the values based on
     * [ProviderConversationUiState.Ready.playingMediaKey].
     */
    private fun observeAudioPlayer() {
        viewModelScope.launch {
            combine(
                audioPlayer.isPlaying,
                audioPlayer.currentPositionMillis,
            ) { playing, position -> playing to position }
                .collect { (playing, position) ->
                    _uiState.update { current ->
                        if (current is ProviderConversationUiState.Ready) {
                            current.copy(
                                isPlaying = playing,
                                playingPositionMillis = position,
                            )
                        } else {
                            current
                        }
                    }
                }
        }
    }

    private fun fireSendText(prompt: String, retryKey: String? = null) {
        val pendingKey = retryKey ?: newLocalKey()
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
