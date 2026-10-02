package com.loresuelvo.serviceprovider.ui.screens.conversation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.conversation.AudioPlayer
import com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder
import com.loresuelvo.serviceprovider.domain.conversation.MediaReader
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.MediaReadException
import com.loresuelvo.serviceprovider.domain.conversation.validateMediaUploads
import com.loresuelvo.serviceprovider.domain.conversation.MAX_MESSAGE_IMAGES
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
// Cohesion review: retain the existing shared send/status state machine in this batch.
// Recording/player ports own platform resources; the next extraction seam is capture orchestration.
// PCA01–11, virtual-time ceiling/read cancellation and shared text/image retry tests prove this cohesive lifecycle.
@HiltViewModel
class ProviderConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getConversationById: GetConversationByIdUseCase,
    private val sendMessage: SendMessageUseCase,
    private val sendMediaMessage: SendMediaMessageUseCase,
    private val mediaReader: MediaReader,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val recordingTimeSource: RecordingTimeSource = RecordingTimeSource(),
) : ViewModel() {

    private val conversationId: Int = savedStateHandle.get<Int>(Route.Conversation.argument)
        ?: error("conversationId must be present in the nav arguments")

    private val _uiState = MutableStateFlow<ProviderConversationUiState>(
        ProviderConversationUiState.Loading,
    )

    val uiState: StateFlow<ProviderConversationUiState> = _uiState.asStateFlow()

    /** Coroutine that drives [RecordingState.Recording.elapsedMillis]. */
    private var recordingTickerJob: Job? = null
    private var audioReadJob: Job? = null
    private var previewUri: String? = null
    private var recordingStartedAt = 0L

    init {
        load()
        observeAudioPlayer()
    }

    fun onRetryLoad() {
        if (_uiState.value is ProviderConversationUiState.Error) load()
    }

    fun onPromptChange(value: String) {
        _uiState.update { current ->
            when (current) {
                is ProviderConversationUiState.Ready ->
                    if (current.composerAllowed && current.pendingMedia !is MediaUpload.Audio) current.copy(promptInput = value) else current
                else -> current
            }
        }
    }

    fun onMediaPicked(uri: Uri) = onImagesPicked(listOf(uri.toString()))

    fun onImagesPicked(uris: List<String>, replaceIndex: Int? = null) {
        val ready = _uiState.value as? ProviderConversationUiState.Ready ?: return
        if (!ready.canStartComposerOperation || ready.pendingMedia is MediaUpload.Audio || uris.isEmpty()) return
        val existing = ready.pendingImages
        val nextCount = if (replaceIndex == null) existing.size + uris.size else existing.size
        if (nextCount > MAX_MESSAGE_IMAGES || (replaceIndex != null && replaceIndex !in existing.indices)) {
            _uiState.value = ready.copy(transientMediaError = SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.TooManyImages))
            return
        }
        if (replaceIndex != null && uris.size != 1) {
            _uiState.value = ready.copy(transientMediaError = SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.InvalidReplacement))
            return
        }
        _uiState.value = ready.copy(readingMedia = true, transientMediaError = null)
        viewModelScope.launch {
            val images = try {
                uris.map { uri ->
                    val image = mediaReader.read(uri) as? MediaUpload.Image
                        ?: throw MediaReadException(SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.UnsupportedFormat))
                    validateMediaUploads(listOf(image))?.let { throw MediaReadException(it) }
                    image
                }
            } catch (e: IOException) {
                _uiState.update { current ->
                    if (current is ProviderConversationUiState.Ready) current.copy(
                        readingMedia = false,
                        transientMediaError = (e as? MediaReadException)?.failure
                            ?: SendMessageOutcome.Failure.Network(e),
                    ) else current
                }
                return@launch
            }
            _uiState.update { current ->
                if (current is ProviderConversationUiState.Ready && current.composerAllowed) {
                    val selected = if (replaceIndex == null) existing + images else
                        existing.mapIndexed { index, image -> if (index == replaceIndex) images.single() else image }
                    current.copy(pendingMedia = selected.firstOrNull(), pendingImages = selected,
                        readingMedia = false, transientMediaError = null)
                } else current
            }
        }
    }

    fun onDiscardImage(index: Int) {
        _uiState.update { current ->
            if (current is ProviderConversationUiState.Ready && current.canStartComposerOperation && index in current.pendingImages.indices) {
                val images = current.pendingImages.filterIndexed { position, _ -> position != index }
                current.copy(pendingMedia = images.firstOrNull(), pendingImages = images, transientMediaError = null)
            } else current
        }
    }

    fun onClearStagedMedia() {
        if (!canStartComposerOperation()) return
        releasePreview()
        _uiState.update { current ->
            when (current) {
                is ProviderConversationUiState.Ready ->
                    if (current.canStartComposerOperation) current.copy(pendingMedia = null, pendingImages = emptyList(), transientMediaError = null) else current
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
        val ready = _uiState.value as? ProviderConversationUiState.Ready ?: return
        if (!ready.canStartComposerOperation || ready.pendingMedia != null || ready.promptInput.isNotBlank()) return
        audioPlayer.stop()
        val started = audioRecorder.start()
        if (started.isFailure) {
            audioRecorder.cancel()
            onAudioFailure(if (started.exceptionOrNull() is SecurityException)
                SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.MicrophonePermission)
                else SendMessageOutcome.Failure.Server(0, "Audio unavailable"))
            return
        }
        recordingStartedAt = recordingTimeSource.nowMillis()
        _uiState.value = ready.copy(recordingState = RecordingState.Recording(0L), transientMediaError = null)
        recordingTickerJob = viewModelScope.launch {
            while (true) {
                delay(250L)
                val elapsed = (recordingTimeSource.nowMillis() - recordingStartedAt).coerceAtLeast(0L)
                _uiState.update { current ->
                    if (current is ProviderConversationUiState.Ready && current.recordingState is RecordingState.Recording)
                        current.copy(recordingState = RecordingState.Recording(elapsed.coerceAtMost(com.loresuelvo.serviceprovider.domain.conversation.MAX_AUDIO_DURATION_MILLIS))) else current
                }
                if (elapsed >= com.loresuelvo.serviceprovider.domain.conversation.MAX_AUDIO_DURATION_MILLIS) {
                    onStopRecording()
                    break
                }
            }
        }
    }

    fun onMicrophonePermissionDenied() {
        onAudioFailure(SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.MicrophonePermission))
    }

    fun onStopRecording() {
        val ready = _uiState.value as? ProviderConversationUiState.Ready ?: return
        if (!ready.composerAllowed || ready.recordingState !is RecordingState.Recording) return
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        val uri = audioRecorder.stop().getOrNull()
        if (uri == null) {
            audioRecorder.cancel()
            onAudioFailure()
            return
        }
        previewUri = uri
        _uiState.value = ready.copy(recordingState = RecordingState.Idle, readingMedia = true)
        audioReadJob = viewModelScope.launch {
            try {
                val media = mediaReader.read(uri) as? MediaUpload.Audio
                    ?: throw MediaReadException(SendMessageOutcome.Failure.InvalidMedia(SendMessageOutcome.Failure.MediaReason.UnsupportedFormat))
                validateMediaUploads(listOf(media))?.let { throw MediaReadException(it) }
                _uiState.update { current ->
                    if (current is ProviderConversationUiState.Ready && current.composerAllowed)
                        current.copy(pendingMedia = media, pendingImages = emptyList(), readingMedia = false, transientMediaError = null) else current
                }
            } catch (e: IOException) {
                releasePreview()
                onAudioFailure((e as? MediaReadException)?.failure ?: SendMessageOutcome.Failure.Network(e))
            }
        }
    }

    private fun onAudioFailure(failure: SendMessageOutcome.Failure = SendMessageOutcome.Failure.Server(0, "Audio unavailable")) {
        _uiState.update { current ->
            if (current is ProviderConversationUiState.Ready) current.copy(recordingState = RecordingState.Idle,
                readingMedia = false, transientMediaError = failure) else current
        }
    }

    fun onCancelRecording() {
        val ready = _uiState.value as? ProviderConversationUiState.Ready
        val capturing = ready?.recordingState is RecordingState.Recording || audioReadJob?.isActive == true
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        audioReadJob?.cancel()
        audioReadJob = null
        audioRecorder.cancel()
        _uiState.update { current ->
            if (current is ProviderConversationUiState.Ready) current.copy(recordingState = RecordingState.Idle, readingMedia = if (capturing) false else current.readingMedia) else current
        }
        if ((_uiState.value as? ProviderConversationUiState.Ready)?.pendingMedia !is MediaUpload.Audio) releasePreview()
    }

    /** Background cancels capture; a cached preview survives without replaying or sending. */
    fun onConversationBackgrounded() {
        onCancelRecording()
        audioPlayer.stop()
    }

    private fun releasePreview() {
        audioPlayer.stop()
        previewUri?.let(audioRecorder::discard)
        previewUri = null
    }

    fun onPlayPreview() {
        val ready = _uiState.value as? ProviderConversationUiState.Ready ?: return
        if (ready.pendingMedia !is MediaUpload.Audio) return
        previewUri?.let { onPlayAudio("audio-preview", it) }
    }

    fun onPlayAudio(bubbleKey: String, url: String) {
        val ready = _uiState.value as? ProviderConversationUiState.Ready ?: return
        val position = if (ready.playingMediaKey == bubbleKey) ready.playingPositionMillis else 0L
        _uiState.value = ready.copy(playingMediaKey = bubbleKey)
        audioPlayer.play(url, position)
    }

    fun onSeekAudio(bubbleKey: String, url: String, positionMillis: Long) {
        val ready = _uiState.value as? ProviderConversationUiState.Ready ?: return
        if (ready.playingMediaKey != bubbleKey) return
        audioPlayer.seekTo(positionMillis.coerceAtLeast(0L))
    }

    fun onPauseAudio() = audioPlayer.pause()

    fun onSendClick() {
        val state = _uiState.value as? ProviderConversationUiState.Ready ?: return
        val prompt = state.promptInput.trim()
        val media = state.pendingMedia
        if ((prompt.isEmpty() && media == null) || !state.canStartComposerOperation) return

        when {
            media != null -> fireSendMedia(if (state.pendingImages.isNotEmpty()) state.pendingImages else listOf(media))
            prompt.isNotEmpty() -> fireSendText(prompt)
        }
    }

    fun onRetrySendFailedBubble(localKey: String) {
        val state = _uiState.value as? ProviderConversationUiState.Ready ?: return
        val failed = state.items
            .firstOrNull { it.key == localKey }
            as? ChatListItem.LocalFailed
            ?: return
        if (!state.canStartComposerOperation) return

        val pending = ChatListItem.LocalPending(
            key = failed.key,
            sender = failed.sender,
            content = failed.content,
            createdOnEpochMillis = failed.createdOnEpochMillis,
            pendingMedia = failed.pendingMedia,
            pendingImages = failed.pendingImages,
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
            fireSendMedia(if (failed.pendingImages.isNotEmpty()) failed.pendingImages else listOf(failed.pendingMedia), retryKey = pending.key)
        } else {
            fireSendText(failed.pendingPrompt, retryKey = pending.key)
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingTickerJob?.cancel()
        audioReadJob?.cancel()
        audioRecorder.cancel()
        releasePreview()
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
                    if (outcome.detail.id != conversationId) {
                        ProviderConversationUiState.Error(
                            ConversationDetailOutcome.Failure.NotFound("Conversation unavailable"),
                        )
                    } else ProviderConversationUiState.Ready(
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
                    pendingImages = emptyList(),
                    items = current.items + pending,
                    sending = true,
                    transientMediaError = null,
                )
            }
        } else {
            _uiState.update { current ->
                (current as ProviderConversationUiState.Ready).copy(
                    sending = true,
                    transientMediaError = null,
                )
            }
        }
        viewModelScope.launch {
            val outcome = sendMessage(conversationId, prompt)
            resolveSendOutcome(pendingKey, prompt, emptyList(), outcome)
        }
    }

    private fun fireSendMedia(media: List<MediaUpload>, retryKey: String? = null) {
        if (retryKey == null) releasePreview()
        val pendingKey = retryKey ?: newLocalKey()
        if (retryKey == null) {
            val pending = ChatListItem.LocalPending(
                key = pendingKey,
                sender = ConversationSender.Provider,
                content = "",
                createdOnEpochMillis = System.currentTimeMillis(),
                pendingMedia = media.firstOrNull(),
                pendingImages = media.filterIsInstance<MediaUpload.Image>(),
            )
            _uiState.update { current ->
                (current as ProviderConversationUiState.Ready).copy(
                    promptInput = "",
                    pendingMedia = null,
                    pendingImages = emptyList(),
                    items = current.items + pending,
                    sending = true,
                    transientMediaError = null,
                )
            }
        } else {
            _uiState.update { current ->
                (current as ProviderConversationUiState.Ready).copy(
                    sending = true,
                    transientMediaError = null,
                )
            }
        }
        viewModelScope.launch {
            val outcome = sendMediaMessage(conversationId, media)
            resolveSendOutcome(pendingKey, "", media, outcome)
        }
    }

    private fun resolveSendOutcome(
        pendingKey: String,
        prompt: String,
        media: List<MediaUpload>,
        outcome: SendMessageOutcome,
    ) {
        _uiState.update { current ->
            val ready = current as? ProviderConversationUiState.Ready ?: return@update current
            val accessFailure = when (outcome) {
                SendMessageOutcome.Failure.Unauthorized -> ConversationDetailOutcome.Failure.Unauthorized
                is SendMessageOutcome.Failure.ConversationNotFound ->
                    ConversationDetailOutcome.Failure.NotFound("Conversation unavailable")
                is SendMessageOutcome.Failure.Server -> if (outcome.code == 403) {
                    ConversationDetailOutcome.Failure.NotFound("Conversation unavailable")
                } else null
                else -> null
            }
            if (accessFailure != null) return@update ProviderConversationUiState.Error(accessFailure)
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
        media: List<MediaUpload>,
    ): ChatListItem = when (outcome) {
        is SendMessageOutcome.Success -> ChatListItem.ServerConfirmed(outcome.message)
        is SendMessageOutcome.Failure -> ChatListItem.LocalFailed(
            key = key,
            sender = sender,
            content = content,
            createdOnEpochMillis = createdOnEpochMillis,
            pendingPrompt = prompt,
            pendingMedia = media.firstOrNull(),
            pendingImages = media.filterIsInstance<MediaUpload.Image>(),
        )
    }

    fun canStartComposerOperation(): Boolean =
        (_uiState.value as? ProviderConversationUiState.Ready)?.canStartComposerOperation == true

    private fun newLocalKey(): String = "local-${UUID.randomUUID()}"
}
