package com.loresuelvo.serviceprovider.ui.screens.conversation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.data.media.MediaOutputUriFactory
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext

/**
 * Route composable for `Route.Conversation` on the provider side.
 * Acquires the [ProviderConversationViewModel] through
 * [hiltViewModel] (Hilt scopes it to this back-stack entry so the
 * same instance survives rotation and process death) and wires
 * the typed event callbacks into the stateless
 * [ProviderConversationScreen].
 *
 * Owns the [androidx.activity.result.ActivityResultLauncher]s for
 * the gallery picker (`ActivityResultContracts.PickVisualMedia`)
 * and the camera capture (`ActivityResultContracts.TakePicture`).
 * The launchers are tied to this back-stack entry's lifecycle, so
 * any URI handed to the camera survives configuration changes and
 * is reclaimed when the user leaves the conversation.
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

    val context = LocalContext.current
    val outputUriFactory = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MediaOutputUriFactoryEntryPoint::class.java,
        ).mediaOutputUriFactory()
    }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.onMediaPicked(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) viewModel.onMediaPicked(uri)
        pendingCameraUri = null
    }

    ProviderConversationScreen(
        state = state,
        onPromptChange = viewModel::onPromptChange,
        onSendClick = viewModel::onSendClick,
        onRetrySendFailedBubble = viewModel::onRetrySendFailedBubble,
        onRetryLoad = viewModel::onRetryLoad,
        onMediaPicked = viewModel::onMediaPicked,
        onClearStagedMedia = viewModel::onClearStagedMedia,
        onPickFromGallery = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onCaptureFromCamera = {
            val uri = outputUriFactory.createCameraOutputUri()
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        },
        onClose = { navController.popBackStack() },
    )
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal interface MediaOutputUriFactoryEntryPoint {
    fun mediaOutputUriFactory(): MediaOutputUriFactory
}
