package com.loresuelvo.serviceprovider.ui.screens.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome

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
 *    user may send messages. The list is a mix of
 *    [ChatListItem.ServerConfirmed] (server-persisted) plus zero
 *    or more [ChatListItem.LocalPending] / [ChatListItem.LocalFailed]
 *    bubbles representing the provider's own in-flight send.
 *
 * The composer is gated on [Ready.sending] (scenario 03-PCC
 * avoids double-submission) and [Ready.promptInput] being
 * non-blank (scenario 06-PCC). A failed bubble carries its own
 * retry CTA inline — the screen never shows a separate
 * "transient error" card.
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
    ) : ProviderConversationUiState
}
