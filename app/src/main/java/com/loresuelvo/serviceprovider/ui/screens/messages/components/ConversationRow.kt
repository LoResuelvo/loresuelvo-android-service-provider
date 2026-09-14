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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessageKind
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
    val rowDescription = stringResource(
        R.string.provider_messages_conversation_description,
        name,
        preview,
        time,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Long.toRelativeTime(nowMillis: Long = System.currentTimeMillis()): String =
    if (this <= 0L) "" else DateUtils.getRelativeTimeSpanString(
        this,
        nowMillis,
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
