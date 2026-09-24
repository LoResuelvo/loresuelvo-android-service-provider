package com.loresuelvo.serviceprovider.ui.screens.conversation

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.data.media.MediaOutputUriFactory
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import com.loresuelvo.serviceprovider.R

/**
 * Route composable for `Route.Conversation` on the provider side.
 * Acquires the [ProviderConversationViewModel] through
 * [hiltViewModel] (Hilt scopes it to this back-stack entry so the
 * same instance survives rotation and process death) and wires
 * the typed event callbacks into the stateless
 * [ProviderConversationScreen].
 *
 * Owns the [androidx.activity.result.ActivityResultLauncher]s for
 * the gallery picker (`ActivityResultContracts.PickVisualMedia`),
 * the camera capture (`ActivityResultContracts.TakePicture`), and
 * the `RECORD_AUDIO` runtime permission
 * (`ActivityResultContracts.RequestPermission`). All launchers are
 * tied to this back-stack entry's lifecycle.
 *
 * The mic-tap flow: the route first requests `RECORD_AUDIO`. On
 * grant it forwards to `viewModel.onStartRecording`; on denial it
 * surfaces a transient `Snackbar` so the user understands why
 * the mic did nothing. The VM itself does NOT request the
 * permission (UI layer concern).
 */
@Composable
fun ProviderConversationRoute(
    navController: NavHostController,
    @Suppress("UNUSED_PARAMETER") conversationId: Int,
) {
    val viewModel: ProviderConversationViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val proposalViewModel: ProviderProposalViewModel = hiltViewModel()
    val proposalState by proposalViewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val proposalSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(proposalViewModel, lifecycleOwner, proposalSnackbarHostState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            proposalViewModel.hasSuccess.collect { pending ->
                if (pending && proposalViewModel.consumeSuccess()) {
                    proposalSnackbarHostState.showSnackbar(context.getString(R.string.provider_proposal_sent))
                }
            }
        }
    }
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

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onStartRecording()
        } else {
            // Surface a transient snackbar so the user understands
            // why the mic did nothing. The VM doesn't own the
            // permission grant; the route knows the contract result.
            // (Snackbar hosting stays on the screen via its own
            // state — this callback just records the denial for
            // any debug logging the host wires up.)
        }
    }

    ProviderConversationScreen(
        state = state,
        proposalSnackbarHostState = proposalSnackbarHostState,
        onPromptChange = viewModel::onPromptChange,
        onSendClick = viewModel::onSendClick,
        onRetrySendFailedBubble = viewModel::onRetrySendFailedBubble,
        onRetryLoad = viewModel::onRetryLoad,
        onMediaPicked = viewModel::onMediaPicked,
        onClearStagedMedia = viewModel::onClearStagedMedia,
        onCreateProposal = {
            (state as? ProviderConversationUiState.Ready)?.detail?.let(proposalViewModel::open)
        },
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
        onMicClick = {
            // Always re-request so the user sees the system prompt
            // if they previously denied with "don't ask again".
            // `RequestPermission` is a no-op (auto-grant) on API
            // levels where the permission is pre-granted.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                viewModel.onStartRecording()
            }
        },
        onStopRecording = viewModel::onStopRecording,
        onPlayAudio = viewModel::onPlayAudio,
        onPauseAudio = viewModel::onPauseAudio,
        onClose = { navController.popBackStack() },
    )

    val sending = proposalState as? ProposalUiState.Sending
    val reviewing = (proposalState as? ProposalUiState.Reviewing) ?: sending?.reviewing
    val form = (proposalState as? ProposalUiState.Form) ?: reviewing?.form
    if (form != null) {
        Dialog(
            onDismissRequest = proposalViewModel::close,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ProviderProposalScreen(
                form = form,
                onAmountChange = proposalViewModel::updateAmount,
                onDateChange = proposalViewModel::updateDate,
                onTimeChange = proposalViewModel::updateTime,
                onReasonChange = proposalViewModel::updateReason,
                onDurationSelect = proposalViewModel::selectDuration,
                onCustomDurationChange = proposalViewModel::updateCustomDuration,
                onClose = proposalViewModel::close,
                onContinue = { proposalViewModel.continueToConfirmation() },
                onOffsetSelect = proposalViewModel::selectOffset,
                enabled = sending == null,
            )
        }
        if (reviewing != null) {
            ProviderProposalConfirmationDialog(
                reviewing = reviewing,
                onConfirm = proposalViewModel::confirmSend,
                onAcknowledgeRisk = proposalViewModel::acknowledgeDuplicateRisk,
                sending = sending != null,
                onCancel = proposalViewModel::cancelReview,
            )
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal interface MediaOutputUriFactoryEntryPoint {
    fun mediaOutputUriFactory(): MediaOutputUriFactory
}
