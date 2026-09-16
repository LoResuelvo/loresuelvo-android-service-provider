package com.loresuelvo.serviceprovider.ui.screens.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R

/**
 * Bottom-of-screen prompt composer for the provider conversation
 * screen (US-A). Stateless — the parent owns the prompt text,
 * the `canSend` gate, and the click handler.
 *
 * US-A scope is text only: no attach button, no microphone
 * affordance — those land on US-B (image attachments) and US-C
 * (audio attachments) on top of this seam.
 *
 * The send button is disabled while [canSend] is false, with a
 * reduced opacity so the layout footprint stays stable across
 * the enabled / disabled transition (matching the consumer's
 * `ChatInputBar`).
 */
@Composable
fun ChatInputBar(
    promptInput: String,
    canSend: Boolean,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 20.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptField(
            value = promptInput,
            onValueChange = onPromptChange,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 56.dp)
                .testTag(PROVIDER_CHAT_INPUT_FIELD_TAG),
        )

        SendButton(
            canSend = canSend,
            onSendClick = onSendClick,
            modifier = Modifier.testTag(PROVIDER_CHAT_SEND_BUTTON_TAG),
        )
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
        shape = androidx.compose.foundation.shape.CircleShape,
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

const val PROVIDER_CHAT_INPUT_FIELD_TAG: String = "provider-chat-input-field"
const val PROVIDER_CHAT_SEND_BUTTON_TAG: String = "provider-chat-send-button"
const val PROVIDER_CHAT_SEND_ICON_TAG: String = "provider-chat-send-icon"
