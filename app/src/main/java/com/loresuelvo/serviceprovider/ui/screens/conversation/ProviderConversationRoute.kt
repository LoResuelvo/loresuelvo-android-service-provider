package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.ui.navigation.Route

/**
 * Route composable for `Route.Conversation` on the provider side.
 * Acquires the [ProviderConversationViewModel] through
 * [hiltViewModel] (Hilt scopes it to this back-stack entry so the
 * same instance survives rotation and process death) and wires
 * the typed event callbacks into the stateless
 * [ProviderConversationScreen].
 *
 * Replaces [com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationPlaceholderRoute]
 * — the temporary destination that survived the `acceptJobRequest`
 * handoff until US-A delivered the real chat surface.
 */
@Composable
fun ProviderConversationRoute(
    navController: NavHostController,
    @Suppress("UNUSED_PARAMETER") conversationId: Int,
) {
    val viewModel: ProviderConversationViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProviderConversationScreen(
        state = state,
        onPromptChange = viewModel::onPromptChange,
        onSendClick = viewModel::onSendClick,
        onRetrySendFailedBubble = viewModel::onRetrySendFailedBubble,
        onRetryLoad = viewModel::onRetryLoad,
        onClose = { navController.popBackStack(Route.Home.path, inclusive = false) },
    )
}
