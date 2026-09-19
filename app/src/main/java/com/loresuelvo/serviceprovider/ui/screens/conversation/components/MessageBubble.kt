package com.loresuelvo.serviceprovider.ui.screens.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.style.TextOverflow
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
 * US-C (audio): the bubble renders a compact audio control with
 * a play / pause affordance, an elapsed / total `mm:ss` counter,
 * and a progress bar driven by [playingMediaKey] /
 * [playingPositionMillis] from the host state. Server-confirmed
 * bubbles stream from [MediaReference.Audio.url] via
 * [AudioPlayer]; pending / failed bubbles use the local
 * [MediaUpload.Audio.durationMillis] for the total label and
 * skip playback (no URL until the server echoes one back).
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
    onPlayAudio: (String, String) -> Unit,
    onPauseAudio: () -> Unit,
    playingMediaKey: String?,
    playingPositionMillis: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is ChatListItem.ServerConfirmed -> ConfirmedBubble(
            item = item,
            onPlayAudio = onPlayAudio,
            onPauseAudio = onPauseAudio,
            playingMediaKey = playingMediaKey,
            playingPositionMillis = playingPositionMillis,
            isPlaying = isPlaying,
            modifier = modifier,
        )
        is ChatListItem.LocalPending -> PendingBubble(
            item = item,
            modifier = modifier,
        )
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
    onPlayAudio: (String, String) -> Unit,
    onPauseAudio: () -> Unit,
    playingMediaKey: String?,
    playingPositionMillis: Long,
    isPlaying: Boolean,
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
                    MediaRenderer(
                        media = media,
                        bubbleKey = item.message.id.toString(),
                        onPlayAudio = onPlayAudio,
                        onPauseAudio = onPauseAudio,
                        playingMediaKey = playingMediaKey,
                        playingPositionMillis = playingPositionMillis,
                        isPlaying = isPlaying,
                        testTagSuffix = item.message.id.toString(),
                    )
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
                    MediaRenderer(
                        media = media,
                        bubbleKey = item.key,
                        onPlayAudio = { _, _ -> },
                        onPauseAudio = {},
                        playingMediaKey = null,
                        playingPositionMillis = 0L,
                        isPlaying = false,
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
                    MediaRenderer(
                        media = media,
                        bubbleKey = item.key,
                        onPlayAudio = { _, _ -> },
                        onPauseAudio = {},
                        playingMediaKey = null,
                        playingPositionMillis = 0L,
                        isPlaying = false,
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
 * Renders the bubble's media payload. Dispatches on the
 * polymorphic [MediaReference] (server-confirmed) and
 * [MediaUpload] (local pending / failed) shapes so image and
 * audio each get a dedicated renderer — the previous
 * `ChatImage(...)` `else -> return` swallowed audio and produced
 * an invisible bubble.
 */
@Composable
private fun MediaRenderer(
    media: Any,
    bubbleKey: String,
    onPlayAudio: (String, String) -> Unit,
    onPauseAudio: () -> Unit,
    playingMediaKey: String?,
    playingPositionMillis: Long,
    isPlaying: Boolean,
    testTagSuffix: String,
) {
    when (media) {
        is MediaReference.Image -> ChatImage(
            model = media,
            testTagSuffix = testTagSuffix,
        )
        is MediaUpload.Image -> ChatImage(
            model = media,
            testTagSuffix = testTagSuffix,
        )
        is MediaReference.Audio -> AudioBubble(
            bubbleKey = bubbleKey,
            url = media.url,
            durationMillis = media.durationMillis,
            onPlayAudio = onPlayAudio,
            onPauseAudio = onPauseAudio,
            playingMediaKey = playingMediaKey,
            playingPositionMillis = playingPositionMillis,
            isPlaying = isPlaying,
            testTagSuffix = testTagSuffix,
        )
        is MediaUpload.Audio -> AudioBubble(
            bubbleKey = bubbleKey,
            url = null,
            durationMillis = media.durationMillis,
            onPlayAudio = { _, _ -> },
            onPauseAudio = {},
            playingMediaKey = null,
            playingPositionMillis = 0L,
            isPlaying = false,
            testTagSuffix = testTagSuffix,
        )
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

/**
 * Compact audio bubble body. Renders a play / pause glyph, the
 * elapsed and total `mm:ss` counters, and a progress bar. The
 * server-confirmed branch drives playback through [onPlayAudio]
 * / [onPauseAudio]; the local pending / failed branch shows the
 * duration-only static control (no URL is available until the
 * server echoes one back).
 */
@Composable
private fun AudioBubble(
    bubbleKey: String,
    url: String?,
    durationMillis: Long,
    onPlayAudio: (String, String) -> Unit,
    onPauseAudio: () -> Unit,
    playingMediaKey: String?,
    playingPositionMillis: Long,
    isPlaying: Boolean,
    testTagSuffix: String,
) {
    val isCurrent = playingMediaKey == bubbleKey
    val bubbleIsPlaying = isCurrent && isPlaying
    val displayPosition = if (isCurrent) playingPositionMillis else 0L
    val progress = if (durationMillis > 0L) {
        (displayPosition.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val canStream = !url.isNullOrBlank()
    Column(
        modifier = Modifier
            .widthIn(min = 180.dp, max = 240.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(PROVIDER_MESSAGE_AUDIO_TAG_PREFIX + testTagSuffix),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (bubbleIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (bubbleIsPlaying) {
                        R.string.provider_conversation_pause_audio_content_description
                    } else {
                        R.string.provider_conversation_play_audio_content_description
                    },
                ),
                tint = LocalContentColor.current,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(enabled = canStream) {
                        if (bubbleIsPlaying) {
                            onPauseAudio()
                        } else {
                            onPlayAudio(bubbleKey, url.orEmpty())
                        }
                    }
                    .testTag(PROVIDER_MESSAGE_AUDIO_PLAY_TAG_PREFIX + testTagSuffix),
            )
            Text(
                text = formatAudioDuration(displayPosition),
                color = LocalContentColor.current,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(PROVIDER_MESSAGE_AUDIO_ELAPSED_TAG_PREFIX + testTagSuffix),
            )
            Text(
                text = "/",
                color = LocalContentColor.current,
            )
            Text(
                text = formatAudioDuration(durationMillis),
                color = LocalContentColor.current,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Visible,
                modifier = Modifier.testTag(PROVIDER_MESSAGE_AUDIO_DURATION_TAG_PREFIX + testTagSuffix),
            )
            if (!canStream) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = LocalContentColor.current,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag(PROVIDER_MESSAGE_AUDIO_LOCAL_INDICATOR_TAG_PREFIX + testTagSuffix),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(PROVIDER_MESSAGE_AUDIO_PROGRESS_TAG_PREFIX + testTagSuffix),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag(PROVIDER_MESSAGE_AUDIO_FILL_TAG_PREFIX + testTagSuffix),
            )
        }
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
            LocalContentColor provides contentColor,
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

private fun formatAudioDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

const val PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX: String = "provider-message-bubble-"
const val PROVIDER_MESSAGE_IMAGE_TAG_PREFIX: String = "provider-message-image-"
const val PROVIDER_MESSAGE_RETRY_BUTTON_TAG_PREFIX: String = "provider-message-retry-"
const val PROVIDER_MESSAGE_PENDING_INDICATOR_TAG: String = "provider-message-pending-indicator"
const val PROVIDER_MESSAGE_AUDIO_TAG_PREFIX: String = "provider-message-audio-"
const val PROVIDER_MESSAGE_AUDIO_PLAY_TAG_PREFIX: String = "provider-message-audio-play-"
const val PROVIDER_MESSAGE_AUDIO_ELAPSED_TAG_PREFIX: String = "provider-message-audio-elapsed-"
const val PROVIDER_MESSAGE_AUDIO_DURATION_TAG_PREFIX: String = "provider-message-audio-duration-"
const val PROVIDER_MESSAGE_AUDIO_PROGRESS_TAG_PREFIX: String = "provider-message-audio-progress-"
const val PROVIDER_MESSAGE_AUDIO_FILL_TAG_PREFIX: String = "provider-message-audio-fill-"
const val PROVIDER_MESSAGE_AUDIO_LOCAL_INDICATOR_TAG_PREFIX: String = "provider-message-audio-local-"
