package com.loresuelvo.serviceprovider.ui.screens.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload

/**
 * Bottom-of-screen prompt composer for the provider conversation
 * screen. Stateless — the parent owns the prompt text, the
 * staged media, the `canSend` gate, and the click handlers.
 *
 * US-A scope was text-only. US-B adds an attach button that opens
 * a [MediaAttachSheet] (gallery + camera) and a [MediaPreviewCard]
 * mode that replaces the text field while a media is staged.
 *
 *  - `pendingMedia == null` → renders the text input + attach
 *    button + send button.
 *  - `pendingMedia != null` → renders the [MediaPreviewCard] in
 *    place of the text input. The send button stays enabled while
 *    the staged media is non-empty.
 *
 * The send button is disabled when there's nothing to send (both
 * prompt blank and no media) and while a previous send is in
 * flight. `canSend` from the parent drives the disabled state so
 * the rule lives in the VM.
 */
@Composable
fun ChatInputBar(
    promptInput: String,
    pendingMedia: MediaUpload?,
    canSend: Boolean,
    isRecording: Boolean,
    recordingElapsedMillis: Long = 0L,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
    onClearStagedMedia: () -> Unit,
    onMicClick: () -> Unit,
    onStopRecordingClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 20.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                pendingMedia != null -> {
                    AttachButton(onClick = onAttachClick)
                    MediaPreviewCard(
                        media = pendingMedia,
                        onClear = onClearStagedMedia,
                        modifier = Modifier.weight(1f),
                    )
                }
                isRecording -> {
                    RecordingIndicator(
                        elapsedMillis = recordingElapsedMillis,
                        onStop = onStopRecordingClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> {
                    AttachButton(onClick = onAttachClick)
                    PromptField(
                        value = promptInput,
                        onValueChange = onPromptChange,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 56.dp)
                            .testTag(PROVIDER_CHAT_INPUT_FIELD_TAG),
                    )
                }
            }
            // Trailing slot — single button that swaps affordance
            // (Send / Mic / Stop) so the chat composer keeps a
            // single tap target. Mirrors the consumer's pattern.
            when {
                isRecording -> StopButton(onClick = onStopRecordingClick)
                promptInput.isBlank() && pendingMedia == null && !canSend ->
                    MicButton(onClick = onMicClick)
                else -> SendButton(
                    canSend = canSend,
                    onSendClick = onSendClick,
                    modifier = Modifier.testTag(PROVIDER_CHAT_SEND_BUTTON_TAG),
                )
            }
        }
    }
}

@Composable
private fun MicButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(PROVIDER_CHAT_MIC_BUTTON_TAG),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = stringResource(
                    R.string.provider_conversation_record_audio_content_description,
                ),
            )
        }
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(PROVIDER_CHAT_STOP_BUTTON_TAG),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = stringResource(
                    R.string.provider_conversation_stop_recording_content_description,
                ),
            )
        }
    }
}

@Composable
private fun RecordingIndicator(
    elapsedMillis: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(PROVIDER_CHAT_RECORDING_INDICATOR_TAG),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = formatElapsed(elapsedMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .testTag(PROVIDER_CHAT_RECORDING_TIMER_TAG)
                    .padding(horizontal = 4.dp),
            )
            IconButton(
                onClick = onStop,
                modifier = Modifier.testTag(PROVIDER_CHAT_RECORDING_STOP_BUTTON_TAG),
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = stringResource(
                        R.string.provider_conversation_stop_recording_content_description,
                    ),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun AttachButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(
                    R.string.provider_conversation_attach_content_description,
                ),
            )
        }
    }
}

@Composable
private fun PromptField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = false,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.provider_conversation_input_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
            inner()
        },
    )
}

@Composable
private fun SendButton(
    canSend: Boolean,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSendClick,
        enabled = canSend,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = if (canSend) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        },
        contentColor = if (canSend) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(
                    R.string.provider_conversation_send_content_description,
                ),
                modifier = Modifier.testTag(PROVIDER_CHAT_SEND_ICON_TAG),
            )
        }
    }
}

/**
 * Bottom sheet that lets the provider choose between picking an
 * image from the gallery or capturing a fresh photo with the
 * camera. The actual launcher lives in the route (so the URIs
 * stay inside the navigation entry's lifecycle); this composable
 * just renders the two affordances and forwards the choice.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MediaAttachSheet(
    onPickFromGallery: () -> Unit,
    onCaptureFromCamera: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            MediaAttachRow(
                labelRes = R.string.provider_conversation_attach_gallery,
                onClick = {
                    onPickFromGallery()
                    onDismiss()
                },
                testTag = PROVIDER_MEDIA_ATTACH_GALLERY_ROW_TAG,
            )
            MediaAttachRow(
                labelRes = R.string.provider_conversation_attach_camera,
                onClick = {
                    onCaptureFromCamera()
                    onDismiss()
                },
                testTag = PROVIDER_MEDIA_ATTACH_CAMERA_ROW_TAG,
            )
        }
    }
}

@Composable
private fun MediaAttachRow(
    labelRes: Int,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag(testTag),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

const val PROVIDER_CHAT_INPUT_FIELD_TAG: String = "provider-chat-input-field"
const val PROVIDER_CHAT_SEND_BUTTON_TAG: String = "provider-chat-send-button"
const val PROVIDER_CHAT_SEND_ICON_TAG: String = "provider-chat-send-icon"
const val PROVIDER_CHAT_ATTACH_BUTTON_TAG: String = "provider-chat-attach-button"
const val PROVIDER_CHAT_MIC_BUTTON_TAG: String = "provider-chat-mic-button"
const val PROVIDER_CHAT_STOP_BUTTON_TAG: String = "provider-chat-stop-button"
const val PROVIDER_CHAT_RECORDING_INDICATOR_TAG: String = "provider-chat-recording-indicator"
const val PROVIDER_CHAT_RECORDING_TIMER_TAG: String = "provider-chat-recording-timer"
const val PROVIDER_CHAT_RECORDING_STOP_BUTTON_TAG: String = "provider-chat-recording-stop-button"
const val PROVIDER_MEDIA_ATTACH_GALLERY_ROW_TAG: String = "provider-media-attach-gallery-row"
const val PROVIDER_MEDIA_ATTACH_CAMERA_ROW_TAG: String = "provider-media-attach-camera-row"
