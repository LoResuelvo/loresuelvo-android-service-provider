package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.ConversationCounterpartDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationMessageDto
import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessageKind
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus

internal fun ConversationDto.toDomain(): Conversation = Conversation(
    id = id.requirePositive("conversation id"),
    status = status.toConversationStatus(),
    counterpart = counterpart.toDomain(),
    lastMessage = lastMessage?.toDomain(),
    updatedOnEpochMillis = updatedOn?.toEpochMillis()
        ?: throw IllegalArgumentException("Missing conversation updated_on"),
)

private fun ConversationCounterpartDto.toDomain(): ConversationCounterpart {
    require(role.isNullOrBlank() || role.equals("consumer", ignoreCase = true)) {
        "Conversation counterpart is not a consumer"
    }
    return ConversationCounterpart(
        id = id.requirePositive("counterpart id"),
        name = name.requireNotBlank("counterpart name"),
        surname = surname.requireNotBlank("counterpart surname"),
        profilePhotoUrl = profilePhotoUrl?.takeIf(String::isNotBlank),
    )
}

private fun ConversationMessageDto.toDomain(): ConversationMessage = ConversationMessage(
    content = content,
    kind = when {
        audio != null -> ConversationMessageKind.Audio
        video != null -> ConversationMessageKind.Video
        else -> ConversationMessageKind.Text
    },
    createdOnEpochMillis = createdOn?.toEpochMillis()
        ?: throw IllegalArgumentException("Missing message created_on"),
).also {
    id.requirePositive("message id")
}

internal fun String.toConversationStatus(): ConversationStatus = when (lowercase()) {
    "pending" -> ConversationStatus.Pending
    "active" -> ConversationStatus.Active
    "rejected" -> ConversationStatus.Rejected
    else -> ConversationStatus.Unsupported(this)
}

private fun Int.requirePositive(label: String): Int =
    also { require(it > 0) { "$label must be positive" } }

private fun String.requireNotBlank(label: String): String =
    also { require(isNotBlank()) { "$label must not be blank" } }
