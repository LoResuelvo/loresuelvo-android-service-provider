package com.loresuelvo.serviceprovider.ui.screens.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome

/**
 * UDF state for the provider conversation detail screen
 * (`Route.Conversation`). Modelled as a sealed hierarchy so the
 * screen renders exactly one of the states without boolean flags
 * — mirrors
 * [com.loresuelvo.serviceprovider.ui.screens.messages.MessagesListUiState].
 *
 *  - [Loading] — initial fetch of the conversation detail is in
 *    flight. The screen shows a centered spinner.
 *  - [Error] — the initial detail fetch failed. The screen
 *    renders the typed failure copy and a retry button that
 *    re-fires [ProviderConversationViewModel.onRetryLoad].
 *    Sending is impossible from this state — the composer is not
 *    rendered.
 *  - [Ready] — the detail loaded; the composer is live and the
 *    user may send messages. [pendingMedia] holds the bytes of
 *    an image the user just picked from the gallery / camera OR
 *    the audio clip the user just recorded. Tapping Send calls
 *    [SendMessageUseCase] (text only) or [SendMediaMessageUseCase]
 *    (image or audio) and the optimistic pending bubble carries
 *    the staged media for the local preview until the server
 *    echoes the persisted media URLs.
 *
 * Recording state (US-C):
 *  - [recordingState] is `Idle` when no recording is in flight
 *    and `Recording(elapsedMillis)` while the recorder is
 *    capturing. The input bar swaps its text field for a
 *    "Stop" affordance while `Recording` is non-null.
 *
 * Audio playback state (US-C):
 *  - [playingMediaKey] carries the [ChatListItem.key] of the
 *    bubble currently driving the audio player — `null` when
 *    nothing is playing. The bubble surfaces its own play /
 *    pause affordance and asks the VM to start / stop playback.
 *
 * The composer is gated on [Ready.sending] (scenario 03-PCC
 * avoids double-submission) and on having either [promptInput]
 * non-blank OR [pendingMedia] non-null (scenario 06-PCC). A
 * failed bubble carries its own retry CTA inline — the screen
 * never shows a separate "transient error" card.
 */
sealed interface ProviderConversationUiState {

    data object Loading : ProviderConversationUiState

    data class Error(
        val failure: ConversationDetailOutcome.Failure,
    ) : ProviderConversationUiState

    data class Ready(
        val detail: ConversationDetail,
        val items: List<ChatListItem>,
        val promptInput: String,
        val sending: Boolean,
        val pendingMedia: MediaUpload? = null,
        val transientMediaError: SendMessageOutcome.Failure? = null,
        val recordingState: RecordingState = RecordingState.Idle,
        val playingMediaKey: String? = null,
        val playingPositionMillis: Long = 0L,
    ) : ProviderConversationUiState
}

/**
 * US-C recording lifecycle. The chat surface renders the input
 * bar differently for each variant:
 *  - [Idle] — empty prompt + no staged media → mic button.
 *  - [Recording] — recorder is live; the input bar swaps its
 *    text field for a stop affordance and a live elapsed-millis
 *    counter.
 */
sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val elapsedMillis: Long) : RecordingState
}
