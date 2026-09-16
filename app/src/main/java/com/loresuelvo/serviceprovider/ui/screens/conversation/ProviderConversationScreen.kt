package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.ChatInputBar
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.MessageBubble

/**
 * Stateless screen for the provider conversation detail
 * (`Route.Conversation`). The route acquires the
 * [ProviderConversationViewModel] and forwards its [uiState] plus
 * the typed event handlers; this composable renders the three
 * branches of the sealed state without owning any of the data
 * flow.
 *
 * Branches:
 *  - [ProviderConversationUiState.Loading] — centered
 *    [CircularProgressIndicator] inside a full-screen Box so the
 *    navigation transition feels stable while the first fetch is
 *    in flight.
 *  - [ProviderConversationUiState.Error] — typed failure copy
 *    (`R.string.provider_conversation_error_*`) plus a retry
 *    button. Composer is intentionally not rendered — sending
 *    without a loaded detail is meaningless.
 *  - [ProviderConversationUiState.Ready] — Scaffold with the
 *    counterpart name in the top bar, a reverse-stacked
 *    [LazyColumn] of bubbles, and the [ChatInputBar] at the
 *    bottom. Newly arrived bubbles auto-scroll into view so the
 *    user doesn't have to chase the conversation on every send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConversationScreen(
    state: ProviderConversationUiState,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onRetrySendFailedBubble: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ProviderConversationUiState.Loading -> LoadingState(modifier)
        is ProviderConversationUiState.Error -> ErrorState(state, onRetryLoad, modifier)
        is ProviderConversationUiState.Ready -> ReadyState(
            state = state,
            onPromptChange = onPromptChange,
            onSendClick = onSendClick,
            onRetrySendFailedBubble = onRetrySendFailedBubble,
            onClose = onClose,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(PROVIDER_CONVERSATION_LOADING_TAG),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    state: ProviderConversationUiState.Error,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (message, canRetry) = errorCopy(state.failure)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(PROVIDER_CONVERSATION_ERROR_TAG),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (canRetry) {
            Button(
                onClick = onRetryLoad,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(PROVIDER_CONVERSATION_RETRY_LOAD_TAG),
            ) {
                Text(text = stringResource(R.string.provider_conversation_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyState(
    state: ProviderConversationUiState.Ready,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onRetrySendFailedBubble: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) {
            listState.animateScrollToItem(state.items.lastIndex)
        }
    }
    Scaffold(
        modifier = modifier.testTag(PROVIDER_CONVERSATION_READY_TAG),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${state.detail.counterpart.name} ${state.detail.counterpart.surname}",
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag(PROVIDER_CONVERSATION_BACK_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.provider_conversation_close,
                            ),
                        )
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                promptInput = state.promptInput,
                canSend = state.promptInput.isNotBlank() && !state.sending,
                onPromptChange = onPromptChange,
                onSendClick = onSendClick,
            )
        },
    ) { contentPadding ->
        MessagesList(
            items = state.items,
            onRetrySendFailedBubble = onRetrySendFailedBubble,
            listState = listState,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun MessagesList(
    items: List<ChatListItem>,
    onRetrySendFailedBubble: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag(PROVIDER_CONVERSATION_MESSAGES_TAG),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = items, key = { it.key }) { item ->
            MessageBubble(
                item = item,
                onRetrySendFailedBubble = onRetrySendFailedBubble,
            )
        }
    }
}

@Composable
private fun errorCopy(failure: ConversationDetailOutcome.Failure): Pair<String, Boolean> = when (failure) {
    is ConversationDetailOutcome.Failure.Network ->
        stringResource(R.string.provider_conversation_error_network) to true
    is ConversationDetailOutcome.Failure.Server ->
        stringResource(R.string.provider_conversation_error_server) to true
    is ConversationDetailOutcome.Failure.Unauthorized ->
        stringResource(R.string.provider_conversation_error_unauthorized) to false
    is ConversationDetailOutcome.Failure.NotFound ->
        stringResource(R.string.provider_conversation_error_not_found) to false
}

const val PROVIDER_CONVERSATION_LOADING_TAG: String = "provider-conversation-loading"
const val PROVIDER_CONVERSATION_ERROR_TAG: String = "provider-conversation-error"
const val PROVIDER_CONVERSATION_RETRY_LOAD_TAG: String = "provider-conversation-retry-load"
const val PROVIDER_CONVERSATION_READY_TAG: String = "provider-conversation-ready"
const val PROVIDER_CONVERSATION_MESSAGES_TAG: String = "provider-conversation-messages"
const val PROVIDER_CONVERSATION_BACK_TAG: String = "provider-conversation-back"
