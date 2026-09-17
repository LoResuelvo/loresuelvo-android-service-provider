package com.loresuelvo.serviceprovider.ui.screens.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import androidx.compose.ui.platform.LocalContext

/**
 * Stateless preview card for an image the provider just picked
 * from the gallery or captured with the camera, staged in the
 * [com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationViewModel]
 * and waiting to be sent. Replaces the text input field while
 * `pendingMedia != null` — tapping the trailing X clears the
 * staged media and the input bar falls back to the text field.
 *
 * The image is rendered from the in-memory bytes (no need to
 * write the staged bytes to disk just for preview) via Coil's
 * data-uri model so the preview is byte-stable across
 * configuration changes.
 */
@Composable
fun MediaPreviewCard(
    media: MediaUpload.Image,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .testTag(PROVIDER_CHAT_MEDIA_PREVIEW_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(media.bytes)
                    .build(),
                contentDescription = media.originalName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = media.originalName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onClear,
            modifier = Modifier.testTag(PROVIDER_CHAT_MEDIA_PREVIEW_CLEAR_TAG),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = androidx.compose.ui.res.stringResource(
                    R.string.provider_conversation_clear_media_content_description,
                ),
            )
        }
    }
}

const val PROVIDER_CHAT_MEDIA_PREVIEW_TAG: String = "provider-chat-media-preview"
const val PROVIDER_CHAT_MEDIA_PREVIEW_CLEAR_TAG: String = "provider-chat-media-preview-clear"
