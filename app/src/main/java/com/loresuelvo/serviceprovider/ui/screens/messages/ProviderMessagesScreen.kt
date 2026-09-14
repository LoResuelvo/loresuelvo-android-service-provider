package com.loresuelvo.serviceprovider.ui.screens.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.ui.screens.messages.components.ProviderConversationRow

@Composable
fun ProviderMessagesRoute(
    viewModel: MessagesListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProviderMessagesScreen(state = state)
}

@Composable
fun ProviderMessagesScreen(
    state: MessagesListUiState,
    onConversationClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag(PROVIDER_MESSAGES_SCREEN_TAG),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                MessagesListUiState.Loading -> LoadingState()
                is MessagesListUiState.Ready -> {
                    if (state.conversations.isEmpty()) {
                        EmptyState()
                    } else {
                        ConversationsList(
                            conversations = state.conversations,
                            onConversationClick = onConversationClick,
                        )
                    }
                }
                is MessagesListUiState.Error -> ErrorState()
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag(PROVIDER_MESSAGES_LOADING_TAG))
        Text(stringResource(R.string.provider_messages_loading))
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.provider_messages_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.provider_messages_empty),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState() {
    Text(
        text = stringResource(R.string.provider_messages_error),
        modifier = Modifier.padding(horizontal = 32.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ConversationsList(
    conversations: List<Conversation>,
    onConversationClick: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PROVIDER_MESSAGES_LIST_TAG),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(conversations, key = { it.id }) { conversation ->
            ProviderConversationRow(
                conversation = conversation,
                onClick = { onConversationClick(conversation.id) },
            )
        }
    }
}

const val PROVIDER_MESSAGES_SCREEN_TAG = "provider-messages-screen"
const val PROVIDER_MESSAGES_LOADING_TAG = "provider-messages-loading"
const val PROVIDER_MESSAGES_LIST_TAG = "provider-messages-list"
