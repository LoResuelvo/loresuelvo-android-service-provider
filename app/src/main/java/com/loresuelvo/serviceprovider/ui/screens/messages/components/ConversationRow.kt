package com.loresuelvo.serviceprovider.ui.screens.messages.components

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessageKind
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar

@Composable
fun ProviderConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = "${conversation.counterpart.name} ${conversation.counterpart.surname}".trim()
    val preview = conversation.lastMessage?.let { message ->
        when (message.kind) {
            ConversationMessageKind.Text -> message.content
            ConversationMessageKind.Audio -> stringResource(R.string.provider_messages_audio_preview)
            ConversationMessageKind.Video -> stringResource(R.string.provider_messages_video_preview)
        }
    }.orEmpty().ifBlank { stringResource(R.string.provider_messages_no_preview) }
    val time = conversation.updatedOnEpochMillis.toRelativeTime()
    val pendingLabel = if (conversation.status == ConversationStatus.Pending) {
        stringResource(R.string.provider_messages_pending_badge)
    } else {
        null
    }
    val rowDescription = stringResource(
        R.string.provider_messages_conversation_description,
        name,
        preview,
        time,
    ).let { description -> pendingLabel?.let { "$description $it" } ?: description }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + conversation.id)
            .semantics { contentDescription = rowDescription }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProviderAvatar(
            name = conversation.counterpart.name,
            surname = conversation.counterpart.surname,
            profilePhotoUrl = conversation.counterpart.profilePhotoUrl,
            contentDescription = stringResource(
                R.string.provider_messages_photo_description,
                name,
            ),
            size = 56.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(preview, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (pendingLabel != null) {
                Surface(modifier = Modifier.testTag(PROVIDER_MESSAGES_PENDING_TAG),
                    color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(pendingLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

const val PROVIDER_MESSAGES_PENDING_TAG = "provider-messages-pending"
const val PROVIDER_MESSAGES_ROW_TAG_PREFIX = "provider-messages-row-"

private fun Long.toRelativeTime(nowMillis: Long = System.currentTimeMillis()): String =
    if (this <= 0L) "" else DateUtils.getRelativeTimeSpanString(
        this,
        nowMillis,
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
