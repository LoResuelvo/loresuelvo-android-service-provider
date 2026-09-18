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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.MediaAttachSheet
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.MessageBubble
import kotlinx.coroutines.launch

/**
 * Stateless screen for the provider conversation detail
 * (`Route.Conversation`). The route acquires the
 * [ProviderConversationViewModel] and forwards its [uiState] plus
 * the typed event callbacks; this composable renders the three
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
 *    [LazyColumn] of bubbles, the [ChatInputBar] at the bottom,
 *    and a [MediaAttachSheet] modal triggered by the attach
 *    button. Newly arrived bubbles auto-scroll into view so the
 *    user doesn't have to chase the conversation on every send.
 *
 * US-B additions:
 *  - The input bar swaps the text field for a [MediaPreviewCard]
 *    when [Ready.pendingMedia] is non-null.
 *  - A media attach sheet is rendered via
 *    [MediaAttachSheet] when the user taps the attach button.
 *  - A [SnackbarHost] surfaces transient media errors (failed
 *    URI reads, missing bytes) without blocking the composer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConversationScreen(
    state: ProviderConversationUiState,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onRetrySendFailedBubble: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onMediaPicked: (android.net.Uri) -> Unit,
    onClearStagedMedia: () -> Unit,
    onClose: () -> Unit,
    onAttachClick: () -> Unit = {},
    onPickFromGallery: () -> Unit = {},
    onCaptureFromCamera: () -> Unit = {},
    onMicClick: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onPlayAudio: (String, String) -> Unit = { _, _ -> },
    onPauseAudio: () -> Unit = {},
    onDismissMediaError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var attachSheetVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) {
        val ready = state as? ProviderConversationUiState.Ready ?: return@LaunchedEffect
        val error = ready.transientMediaError ?: return@LaunchedEffect
        val message = when (error) {
            is com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.Network ->
                "No pudimos leer la imagen seleccionada."
            is com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.Server ->
                error.message
            is com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.Unauthorized ->
                "Tu sesión expiró."
            is com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.ConversationNotFound ->
                "La conversación ya no está disponible."
            is com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.PayloadTooLarge ->
                "El archivo es demasiado grande."
        }
        scope.launch {
            snackbarHostState.showSnackbar(message)
            onDismissMediaError()
        }
    }

    Scaffold(
        modifier = modifier.testTag(PROVIDER_CONVERSATION_READY_TAG),
        topBar = {
            TopAppBar(
                title = {
                    if (state is ProviderConversationUiState.Ready) {
                        Text(
                            text = "${state.detail.counterpart.name} ${state.detail.counterpart.surname}",
                            modifier = Modifier.semantics { heading() },
                        )
                    }
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
            if (state is ProviderConversationUiState.Ready) {
                val recording = state.recordingState
                val elapsed = (recording as? RecordingState.Recording)?.elapsedMillis ?: 0L
                ChatInputBar(
                    promptInput = state.promptInput,
                    pendingMedia = state.pendingMedia,
                    canSend = (state.promptInput.isNotBlank() || state.pendingMedia != null) &&
                        !state.sending &&
                        state.recordingState == RecordingState.Idle,
                    isRecording = recording is RecordingState.Recording,
                    recordingElapsedMillis = elapsed,
                    onPromptChange = onPromptChange,
                    onSendClick = onSendClick,
                    onAttachClick = { attachSheetVisible = true },
                    onClearStagedMedia = onClearStagedMedia,
                    onMicClick = onMicClick,
                    onStopRecordingClick = onStopRecording,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        when (state) {
            ProviderConversationUiState.Loading -> LoadingState(
                modifier = Modifier.padding(contentPadding),
            )
            is ProviderConversationUiState.Error -> ErrorState(
                state = state,
                onRetryLoad = onRetryLoad,
                modifier = Modifier.padding(contentPadding),
            )
            is ProviderConversationUiState.Ready -> ReadyState(
                state = state,
                onRetrySendFailedBubble = onRetrySendFailedBubble,
                listState = rememberLazyListState(),
                contentPadding = contentPadding,
            )
        }
    }

    if (attachSheetVisible) {
        MediaAttachSheet(
            onPickFromGallery = onPickFromGallery,
            onCaptureFromCamera = onCaptureFromCamera,
            onDismiss = { attachSheetVisible = false },
        )
    }

    // Avoid unused-parameter lint warnings for callbacks that
    // the route wires up but the screen doesn't surface (US-A
    // tests exercise the screen with the defaults).
    @Suppress("UnusedParameter") val keepOnAttach = onAttachClick
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
    onRetrySendFailedBubble: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
) {
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) {
            listState.animateScrollToItem(state.items.lastIndex)
        }
    }
    MessagesList(
        items = state.items,
        onRetrySendFailedBubble = onRetrySendFailedBubble,
        listState = listState,
        contentPadding = contentPadding,
    )
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
