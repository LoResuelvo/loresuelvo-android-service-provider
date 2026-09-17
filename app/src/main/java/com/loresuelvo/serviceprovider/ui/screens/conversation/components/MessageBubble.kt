package com.loresuelvo.serviceprovider.ui.screens.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.MediaReference
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.ui.screens.conversation.ChatListItem

/**
 * Stateless chat bubble for the provider conversation screen.
 * Dispatches on the [ChatListItem] subtype so the surface
 * rendering keeps the optimistic / failed UX inline with the
 * server-confirmed bubbles.
 *
 * US-A (text): the bubble shows the text content. Pending and
 * failed variants add an inline spinner / retry button respectively.
 *
 * US-B (images): when the bubble carries media, the surface
 * renders the image (server-confirmed: from URL; optimistic /
 * failed: from local bytes) under a fixed-height thumbnail.
 * Text and image compose naturally: captions appear under the
 * thumbnail.
 *
 * Visual rules:
 *  - Provider bubbles align to the end of the row, painted with
 *    the primary color (US-A) / surface (US-B failed).
 *  - Consumer bubbles align to the start of the row, painted with
 *    `surface` to match the consumer's counterpart colour.
 *  - Local pending bubbles show a tiny inline `CircularProgressIndicator`.
 *  - Local failed bubbles render an inline refresh icon button
 *    under the content; tapping it re-fires the send through the
 *    parent screen's `onRetrySendFailedBubble` callback.
 *
 * The asymmetric top corner (top-start for consumer, top-end for
 * provider) reads as a "conversational tail" and matches modern
 * chat UIs (WhatsApp, iMessage).
 */
@Composable
fun MessageBubble(
    item: ChatListItem,
    onRetrySendFailedBubble: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is ChatListItem.ServerConfirmed -> ConfirmedBubble(item, modifier)
        is ChatListItem.LocalPending -> PendingBubble(item, modifier)
        is ChatListItem.LocalFailed -> FailedBubble(
            item = item,
            onRetry = { onRetrySendFailedBubble(item.key) },
            modifier = modifier,
        )
    }
}

@Composable
private fun ConfirmedBubble(
    item: ChatListItem.ServerConfirmed,
    modifier: Modifier = Modifier,
) {
    val alignment = when (item.sender) {
        ConversationSender.Provider -> Alignment.End
        ConversationSender.Consumer -> Alignment.Start
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (item.sender == ConversationSender.Provider) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        BubbleSurface(
            containerColor = bubbleColorFor(item.sender),
            contentColor = bubbleContentColorFor(item.sender),
            shape = bubbleShapeFor(item.sender),
            testTag = bubbleTestTag(item),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.message.media?.let { media ->
                    ChatImage(model = media, testTagSuffix = "${item.message.id}")
                }
                if (item.content.isNotEmpty()) {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingBubble(
    item: ChatListItem.LocalPending,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        BubbleSurface(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = bubbleShapeFor(ConversationSender.Provider),
            testTag = bubbleTestTag(item),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.pendingMedia?.let { media ->
                    ChatImage(
                        model = media,
                        testTagSuffix = "${item.key}-pending",
                    )
                }
                if (item.content.isNotEmpty()) {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.provider_conversation_sending),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(14.dp)
                            .testTag(PROVIDER_MESSAGE_PENDING_INDICATOR_TAG),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedBubble(
    item: ChatListItem.LocalFailed,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        BubbleSurface(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = bubbleShapeFor(ConversationSender.Provider),
            testTag = bubbleTestTag(item),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.pendingMedia?.let { media ->
                    ChatImage(
                        model = media,
                        testTagSuffix = "${item.key}-failed",
                    )
                }
                if (item.content.isNotEmpty()) {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.provider_conversation_send_failed),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(
                            R.string.provider_conversation_retry_content_description,
                        ),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onRetry)
                            .testTag(PROVIDER_MESSAGE_RETRY_BUTTON_TAG_PREFIX + item.key),
                    )
                }
            }
        }
    }
}

/**
 * Image renderer shared by all three bubble variants. Loads from
 * the wire URL (server-confirmed) or the local in-memory bytes
 * (optimistic / failed) via Coil. The fixed height keeps the
 * bubble compact while still being tappable for a future
 * fullscreen viewer (US-B keeps it read-only).
 */
@Composable
private fun ChatImage(model: Any, testTagSuffix: String) {
    val context = LocalContext.current
    val request = when (model) {
        is MediaReference.Image -> ImageRequest.Builder(context)
            .data(model.url)
            .crossfade(true)
            .build()
        is MediaUpload.Image -> ImageRequest.Builder(context)
            .data(model.bytes)
            .crossfade(true)
            .build()
        else -> return
    }
    Box(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .size(width = 180.dp, height = 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(PROVIDER_MESSAGE_IMAGE_TAG_PREFIX + testTagSuffix),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp,
                )
            },
            error = {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            },
        )
    }
}

@Composable
private fun BubbleSurface(
    containerColor: Color,
    contentColor: Color,
    shape: RoundedCornerShape,
    testTag: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .background(color = containerColor, shape = shape)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
            content = { content() },
        )
    }
}

@Composable
private fun bubbleColorFor(sender: ConversationSender): Color = when (sender) {
    ConversationSender.Provider -> MaterialTheme.colorScheme.primary
    // Matches the consumer's `ConversationMessageBubble` counterpart
    // (consumer side) so the same chat looks symmetric from both
    // sides of the conversation.
    ConversationSender.Consumer -> MaterialTheme.colorScheme.surface
}

@Composable
private fun bubbleContentColorFor(sender: ConversationSender): Color = when (sender) {
    ConversationSender.Provider -> MaterialTheme.colorScheme.onPrimary
    ConversationSender.Consumer -> MaterialTheme.colorScheme.onSurface
}

private fun bubbleShapeFor(sender: ConversationSender): RoundedCornerShape = when (sender) {
    ConversationSender.Provider -> RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 4.dp,
        bottomEnd = 20.dp,
        bottomStart = 20.dp,
    )
    ConversationSender.Consumer -> RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 20.dp,
        bottomEnd = 20.dp,
        bottomStart = 20.dp,
    )
}

private fun bubbleTestTag(item: ChatListItem): String = when (item) {
    is ChatListItem.ServerConfirmed ->
        PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + item.message.id
    is ChatListItem.LocalPending ->
        PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + item.key
    is ChatListItem.LocalFailed ->
        PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + item.key
}

const val PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX: String = "provider-message-bubble-"
const val PROVIDER_MESSAGE_IMAGE_TAG_PREFIX: String = "provider-message-image-"
const val PROVIDER_MESSAGE_RETRY_BUTTON_TAG_PREFIX: String = "provider-message-retry-"
const val PROVIDER_MESSAGE_PENDING_INDICATOR_TAG: String = "provider-message-pending-indicator"
